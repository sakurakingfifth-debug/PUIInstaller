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
 * 管理器有两种存活形态，必须都覆盖：
 *
 *  1. **独立安装**：包名 `org.lsposed.manager`，直接起 `MainActivity` 就行。
 *  2. **寄生在 shell 里**（用户没装管理器 APK，LSPosed 本体模块把管理器塞进
 *     `com.android.shell` 进程跑，设置页「管理器包名」显示 com.android.shell）。
 *     这时 `org.lsposed.manager` 根本不存在，只能走它给 shell 留的跳板：
 *       targetActivity = com.android.shell/.BugreportWarningActivity
 *       action         = android.intent.action.MAIN
 *       category       = org.lsposed.manager.LAUNCH_MANAGER
 *     LSPosed 的 shell hook 看到这个 category 会把启动重定向到管理器。
 *
 * 两种形态都用同一个深链定位到具体模块（见 LSPosed `MainActivity#handleIntent`：
 * `data.scheme == "module"` → host 是模块包名、port 是 userId）。
 */
object Lsposed {

    private const val MANAGER = "org.lsposed.manager"
    private const val MAIN_CLASS = "org.lsposed.manager.ui.activity.MainActivity"

    private const val SHELL = "com.android.shell"
    private const val SHELL_TRAMPOLINE = "com.android.shell.BugreportWarningActivity"
    private const val CATEGORY_LAUNCH_MANAGER = "org.lsposed.manager.LAUNCH_MANAGER"

    /** 管理器可用（独立安装，或有 shell 跳板）就算 true。 */
    fun isInstalled(context: Context): Boolean =
        managerComponents(context).isNotEmpty() || hasShellTrampoline(context)

    /**
     * 打开 [packageName] 这个模块的作用域页。
     *
     * 依次尝试：独立安装的管理器 → 寄生（shell 里的注入组件 / 跳板）→ root 的 `am start`。
     * 寄生模式下跳板 Activity 是 `exported=false`，普通 App 起不来，只能借 root 冒充 shell，
     * 所以这个函数是 suspend 的（最后一步要走 su）。
     */
    suspend fun openModuleScope(context: Context, packageName: String): Boolean {
        val userId = runCatching { Process.myUid() / 100000 }.getOrDefault(0)
        val link = Uri.parse("module://$packageName:$userId")

        // ① 独立安装的管理器
        managerComponents(context).forEach { component ->
            if (start(context, baseIntent(component).setData(link))) return true
        }

        // ② 寄生：LSPosed 会把管理器的 Activity 注入 shell 的包信息里，按名字先找一遍
        for (component in shellManagerComponents(context)) {
            if (start(context, baseIntent(component).setData(link))) return true
        }
        // 注入没暴露出来时，按它惯用的类名盲试一次（不存在会抛，被 catch 吃掉）
        val blind = ComponentName(SHELL, MAIN_CLASS)
        if (start(context, baseIntent(blind).setData(link))) return true

        // ③ 寄生跳板：AOSP shell 的 BugreportWarningActivity + LAUNCH_MANAGER 分类
        if (hasShellTrampoline(context)) {
            val trampoline = baseIntent(ComponentName(SHELL, SHELL_TRAMPOLINE))
                .addCategory(CATEGORY_LAUNCH_MANAGER)
            if (start(context, Intent(trampoline).setData(link))) return true
            // 深链可能被重定向吃掉，至少把管理器打开
            if (start(context, trampoline)) return true
        }

        // ④ 寄生兜底：跳板 exported=false，普通 App 会被 SecurityException 拦掉，
        //    用 root 的 am start 以 shell 身份启动（设备本来就 root，安装器也一直在用 su）
        val rooted = withContext(Dispatchers.IO) {
            RootShell.run(
                "am start -p $SHELL -a android.intent.action.MAIN " +
                    "-c $CATEGORY_LAUNCH_MANAGER -f 0x10000000 " +
                    "-d '$link' -n $SHELL/$SHELL_TRAMPOLINE"
            )
        }
        if (rooted.first && !rooted.second.contains("Error")) return true

        // ⑤ 最后再试一次管理器首页（个别分支改了组件名，只能退而求其次）
        val launch = runCatching {
            context.packageManager.getLaunchIntentForPackage(MANAGER)
        }.getOrNull()
        if (launch != null && start(context, Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return true
        }
        return false
    }

    private fun baseIntent(component: ComponentName) = Intent(Intent.ACTION_MAIN)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent); true }.getOrDefault(false)

    /** 独立安装的管理器里能用的入口组件；没装就是空列表。 */
    private fun managerComponents(context: Context): List<ComponentName> {
        val info = runCatching {
            context.packageManager.getPackageInfo(MANAGER, PackageManager.GET_ACTIVITIES)
        }.getOrNull() ?: return emptyList()
        val names = info.activities.orEmpty()
            .map { it.name }
            .filter { it.contains("MainActivity") || it.contains("Splash") }
        if (names.isEmpty()) return listOf(ComponentName(MANAGER, MAIN_CLASS))
        // MainActivity 优先：Splash 只是启动图，深链由 MainActivity 处理
        return names.sortedBy { if (it.contains("MainActivity")) 0 else 1 }
            .map { ComponentName(MANAGER, it) }
    }

    /** 寄生时被注入到 shell 里的管理器组件（类名里带 lsposed 的都算）。 */
    private fun shellManagerComponents(context: Context): List<ComponentName> {
        val info = runCatching {
            context.packageManager.getPackageInfo(SHELL, PackageManager.GET_ACTIVITIES)
        }.getOrNull() ?: return emptyList()
        return info.activities.orEmpty()
            .map { it.name }
            .filter { it.contains("lsposed", ignoreCase = true) }
            .sortedBy { if (it.contains("MainActivity")) 0 else 1 }
            .map { ComponentName(SHELL, it) }
    }

    /** shell 里有没有那个跳板 Activity（寄生模式才用得上）。 */
    private fun hasShellTrampoline(context: Context): Boolean = runCatching {
        context.packageManager.getActivityInfo(ComponentName(SHELL, SHELL_TRAMPOLINE), 0)
        true
    }.getOrDefault(false)
}
