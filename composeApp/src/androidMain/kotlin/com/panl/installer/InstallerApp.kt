package com.panl.installer

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.panl.installer.core.FairMemoryReceiver
import com.panl.installer.core.InstallerLog
import com.panl.installer.core.MemoryPressure
import com.panl.installer.core.Notifications
import com.panl.installer.core.Settings
import com.panl.installer.i18n.AppLanguage
import com.panl.installer.corepatch.CorePatchPrefs
import com.panl.installer.i18n.I18n
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class InstallerApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 早点连 LSPosed：连上才拿得到远程配置（核心破解的开关都存在那边）
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) = CorePatchPrefs.bind(service, this@InstallerApp)
                override fun onServiceDied(service: XposedService) = CorePatchPrefs.unbind()
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 提前把设置读进内存，避免第一帧里做磁盘 IO。
        // 系统应用可能在 direct boot 阶段被拉起，此时 CE 存储不可用，包一层避免启动即崩。
        runCatching { Settings.get(this) }
        // 语言要在建通知渠道之前定下来，否则渠道名会停在中文
        runCatching { I18n.apply(AppLanguage.fromId(Settings.get(this).language)) }
        runCatching { Notifications.createChannel(this) }
        // 任何没被 catch 的异常都先落到「日志」页里，方便用户直接分享出来
        runCatching { InstallerLog.installCrashHandler(this) }
        // 公平运行内存（ITGSA）：接收 TRIM/KILL 广播并回执
        runCatching { FairMemoryReceiver.register(this) }
        // Android 17 内存限制器：把上次是不是被它带走的记下来，方便自查
        runCatching { logLastExit() }
        // 组件自愈：被「工具箱」禁用过也能自己爬回来
        runCatching { healEntryComponents() }
    }

    /**
     * 自愈：把一个被外部禁用的入口组件恢复成默认（跟随清单）。
     *
     * 现场（用户 bugreport）：选择器能列出「安装器」，点下去却是
     * `Unable to find explicit activity class {…ui.InstallActivity}` —— 组件被禁用了。
     * 爱玩机工具箱这类「接管安装 / 静默安装」的工具、以及某些 LSPosed 模块都会这么干；
     * 组件一旦被禁用，点 apk 只会看到选择器一闪而过，什么都弹不出来，
     * 而且卸载重装安装器、重刷模块都不会把它恢复（禁用状态是按组件名记在 user state 里的）。
     *
     * 用字符串而不是 KClass：不想在启动时把 Compose 那套类都加载进来。
     */
    private fun healEntryComponents() {
        listOf(
            "com.panl.installer.ui.InstallActivity",
            "com.panl.installer.ui.UninstallActivity"
        ).forEach { name ->
            runCatching {
                val component = ComponentName(this, name)
                val state = packageManager.getComponentEnabledSetting(component)
                val disabled = state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
                if (disabled) {
                    packageManager.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                        PackageManager.DONT_KILL_APP
                    )
                    InstallerLog.record(
                        this, "SELF-HEAL",
                        "入口组件被禁用，已恢复：$name (state=$state)"
                    )
                }
            }
        }
    }

    /**
     * 标准内存回调（所有 Android 版本都有）：只在系统真的缺内存时归还，
     * `TRIM_MEMORY_UI_HIDDEN` 这类"界面不可见"的信号不触发，避免无谓的 GC。
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val shouldFree = level == TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= TRIM_MEMORY_BACKGROUND
        if (shouldFree) {
            runCatching { MemoryPressure.release(this, "onTrimMemory($level)") }
        }
    }

    /**
     * 记下上次进程是怎么退出的。
     *
     * 内存限制器（Android 17+）在把应用打到 zRAM 上限后终止进程时，
     * [ApplicationExitInfo.getDescription] 会是 `MemoryLimiter:AnonSwap`，
     * 而 reason 只是 REASON_OTHER —— 不看 description 根本分辨不出来。
     * 这里把新出现的这类退出写进「日志」页，用户反馈"老是被杀"时可以直接给出证据。
     */
    private fun logLastExit() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = getSystemService(ActivityManager::class.java) ?: return
        val prefs = getSharedPreferences("mem_diag", MODE_PRIVATE)
        val logged = prefs.getLong(KEY_LAST_EXIT_LOGGED, 0L)
        val exits = runCatching {
            am.getHistoricalProcessExitReasons(packageName, 0, 5)
        }.getOrNull().orEmpty()
        var newest = logged
        exits.asReversed().forEach { info ->
            if (info.timestamp <= logged) return@forEach
            newest = maxOf(newest, info.timestamp)
            val desc = runCatching { info.description }.getOrNull().orEmpty()
            val byLimiter = desc.contains("MemoryLimiter", ignoreCase = true)
            val lowMemory = info.reason == ApplicationExitInfo.REASON_LOW_MEMORY
            if (byLimiter || lowMemory) {
                InstallerLog.record(
                    this, "MEM",
                    "上次进程被系统回收：reason=${info.reason}" +
                        (if (desc.isNotBlank()) " desc=$desc" else "") +
                        " importance=${info.importance}"
                )
            }
        }
        if (newest > logged) {
            prefs.edit().putLong(KEY_LAST_EXIT_LOGGED, newest).apply()
        }
    }

    private companion object {
        const val KEY_LAST_EXIT_LOGGED = "last_exit_logged"
    }
}
