package com.panl.installer.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 跳到 LSPosed 管理器里「这个模块」的作用域页。
 *
 * 管理器有两种存活形态：
 *
 *  1. **独立安装**：包名 `org.lsposed.manager`，直接起 MainActivity。
 *  2. **寄生在 shell 里**：没装管理器 APK，LSPosed 本体把管理器塞进 `com.android.shell`
 *     进程里跑（设置页「管理器包名」显示 com.android.shell）。此时 `org.lsposed.manager`
 *     根本不存在，要靠 shell 里的一个「跳板 Activity」+ 专用 category 唤醒：
 *
 *         action   = android.intent.action.MAIN
 *         category = org.lsposed.manager.LAUNCH_MANAGER
 *         component= com.android.shell/<主进程里的任意 Activity>
 *
 *     LSPosed 的 shell hook 看到这个 category 就会重定向到管理器。
 *     跳板具体是哪个 Activity **不固定**（AOSP 上是 BugreportWarningActivity，各家 ROM 未必一样），
 *     所以照 LSPosed 自己的做法（ShortcutUtil#getLaunchIntent）来：遍历 shell 的 Activity，
 *     挑 `processName == packageName`（主进程，hook 就注入在这个进程里）的逐个试。
 *
 * 另外：跳板 Activity 在清单里是 `exported=false`，普通 App 起不来，
 * 寄生这条路最终只能借 root 的 `am start`。
 *
 * 两种形态都带上模块深链 `module://<包名>:<userId>`（LSPosed `MainActivity#handleIntent` 认这个），
 * 能直达该模块的作用域页；被重定向吃掉也没关系，至少管理器能打开。
 */
object Lsposed {

    private const val MANAGER = "org.lsposed.manager"
    private const val MAIN_CLASS = "org.lsposed.manager.ui.activity.MainActivity"

    private const val SHELL = "com.android.shell"
    private const val CATEGORY_LAUNCH_MANAGER = "org.lsposed.manager.LAUNCH_MANAGER"
    /** AOSP 上的跳板，优先试它；别的机型没有就靠遍历。 */
    private const val SHELL_TRAMPOLINE = "com.android.shell.BugreportWarningActivity"

    /** 管理器可用（独立安装，或有 shell 跳板）就算 true。 */
    fun isInstalled(context: Context): Boolean =
        managerComponents(context).isNotEmpty() || shellLaunchTargets(context).isNotEmpty()

    /**
     * 打开 [packageName] 这个模块的作用域页；全都失败返回 false（调用方给提示）。
     *
     * 是 suspend 的：寄生形态最后要靠 su 跑 `am start`。
     */
    suspend fun openModuleScope(context: Context, packageName: String): Boolean {
        val userId = runCatching { Process.myUid() / 100000 }.getOrDefault(0)
        val link = Uri.parse("module://$packageName:$userId")

        // ① 独立安装的管理器
        for (component in managerComponents(context)) {
            if (start(context, baseIntent(component).setData(link))) return true
        }

        // ② 直接问系统：谁声明了 LAUNCH_MANAGER 这个 category（寄生时 LSPosed 会把它挂出来）
        for (component in resolvedManagerActivities(context)) {
            if (start(context, baseIntent(component).setData(link))) return true
            if (start(context, trampolineIntent(component).setData(link))) return true
        }

        // ③ 寄生：LSPosed 有时会把管理器组件注入 shell 的包信息里，按名字先找一遍
        for (component in shellInjectedComponents(context)) {
            if (start(context, baseIntent(component).setData(link))) return true
        }

        val targets = shellLaunchTargets(context)
        if (targets.isEmpty()) return false

        // ③ 先按普通身份试（跳板是 exported=false，多半被拦，但失败了不亏）
        for (component in targets) {
            if (start(context, trampolineIntent(component).setData(link))) return true
        }

        // ④ 借 root 的身份起：`am start` 以 root 跑，绕开 exported 限制
        for (component in targets) {
            if (amStart(context, component, link)) return true
        }
        // ⑤ 深链可能被重定向吃掉，不带 data 再来一轮，至少把管理器打开
        for (component in targets) {
            if (amStart(context, component, null)) return true
        }
        InstallerLog.record(
            context, "LSP",
            "打开管理器失败，试过 ${targets.joinToString { it.className }}（多半是没给 ROOT）"
        )
        return false
    }

    private fun baseIntent(component: ComponentName) = Intent(Intent.ACTION_MAIN)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 跳板形态：MAIN + LAUNCH_MANAGER + 固定包名。 */
    private fun trampolineIntent(component: ComponentName) = Intent(Intent.ACTION_MAIN)
        .setComponent(component)
        .setPackage(SHELL)
        .addCategory(CATEGORY_LAUNCH_MANAGER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent); true }.getOrDefault(false)

    /**
     * 用 `am start` 拉起跳板。
     *
     * **必须切到 shell（uid 2000）身份**：LSPosed 的钩子注入在 com.android.shell 进程里，
     * 只有以 shell 身份发起的启动才会被它改写到管理器；用 root 身份发，钩子不生效，
     * 会直接落到 AOSP 那个「错误报告」页面（用户实测）。
     * KernelSU / Magisk 的 su 都支持 `su <uid> -c`，老实现不支持时再退回 root。
     */
    private suspend fun amStart(context: Context, component: ComponentName, link: Uri?): Boolean =
        withContext(Dispatchers.IO) {
            // URI 里没有空格，不加引号，省掉一层转义
            val data = link?.let { " -d $it" }.orEmpty()
            val cmd = "am start -p $SHELL -a android.intent.action.MAIN " +
                "-c $CATEGORY_LAUNCH_MANAGER -f 0x10000000$data " +
                "-n ${component.packageName}/${component.className}"

            val asShell = RootShell.run("su 2000 -c '$cmd'")
            if (asShell.first && asShell.second.contains("Starting")) return@withContext true

            val asRoot = RootShell.run(cmd)
            val started = asRoot.first && asRoot.second.contains("Starting")
            if (!started) {
                InstallerLog.record(
                    context, "LSP",
                    "am start ${component.className} 失败：shell=${asShell.second.take(80)} root=${asRoot.second.take(80)}"
                )
            }
            started
        }

    /** 独立安装的管理器里能用的入口组件；没装就是空列表。 */
    private fun managerComponents(context: Context): List<ComponentName> {
        val info = runCatching {
            context.packageManager.getPackageInfo(MANAGER, PackageManager.GET_ACTIVITIES)
        }.getOrNull() ?: return emptyList()
        val names = info.activities.orEmpty()
            .map { it.name }
            .filter { it.contains("MainActivity") || it.contains("Splash") }
        if (names.isEmpty()) return listOf(ComponentName(MANAGER, MAIN_CLASS))
        return names.sortedBy { if (it.contains("MainActivity")) 0 else 1 }
            .map { ComponentName(MANAGER, it) }
    }

    /** 系统里声明了 LAUNCH_MANAGER 这个 category 的 Activity（寄生模式可能由它暴露）。 */
    private fun resolvedManagerActivities(context: Context): List<ComponentName> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(CATEGORY_LAUNCH_MANAGER)
        return runCatching {
            context.packageManager.queryIntentActivities(intent, 0)
        }.getOrNull().orEmpty().mapNotNull { ri ->
            ri.activityInfo?.let { ComponentName(it.packageName, it.name) }
        }
    }

    /** 寄生时被注入 shell 包信息里的管理器组件（类名带 lsposed 的都算）。 */
    private fun shellInjectedComponents(context: Context): List<ComponentName> {
        val info = runCatching {
            context.packageManager.getPackageInfo(SHELL, PackageManager.GET_ACTIVITIES)
        }.getOrNull() ?: return emptyList()
        return info.activities.orEmpty()
            .map { it.name }
            .filter { it.contains("lsposed", ignoreCase = true) }
            .map { ComponentName(SHELL, it) }
    }

    /**
     * 可当跳板用的 shell Activity：LSPosed 的 hook 注入在 shell 主进程，
     * 所以只挑 `processName == packageName` 的（跑在独立进程里的钩不到）。
     * BugreportWarningActivity 排最前 —— AOSP 上一直是它。
     */
    private fun shellLaunchTargets(context: Context): List<ComponentName> {
        val info = runCatching {
            context.packageManager.getPackageInfo(SHELL, PackageManager.GET_ACTIVITIES)
        }.getOrNull() ?: return emptyList()
        return info.activities.orEmpty()
            .filter { it.processName.isNullOrEmpty() || it.processName == info.packageName }
            .sortedBy { if (it.name == SHELL_TRAMPOLINE) 0 else 1 }
            .map { ComponentName(SHELL, it.name) }
            .take(6)
    }
}
