package com.panl.installer.core

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 两个「系统级」开关（思路来自 InstallerX-Revived）：
 *
 *  1. **停用系统安装验证** —— 把 `verifier_verify_adb_installs` / `package_verifier_enable`
 *     两个全局项置 0，部分 ROM（尤其带 Play 保护机制的）就不会在安装时再扫一遍。
 *     写全局设置要 WRITE_SECURE_SETTINGS，普通安装的包拿不到，所以走 root。
 *  2. **忽略电池优化** —— 后台管理严格的 ROM 会掐掉前台服务，实时通知的安装就会卡住；
 *     把本应用加进电池优化白名单即可。
 */
object SystemTweaks {

    private const val TAG = "SystemTweaks"

    private const val KEY_VERIFIER_ADB = "verifier_verify_adb_installs"
    private const val KEY_VERIFIER_ENABLE = "package_verifier_enable"

    /** 当前是否已经关掉了安装校验（任一项为 0 就算关）。 */
    fun isVerificationDisabled(context: Context): Boolean = runCatching {
        val cr = context.contentResolver
        Settings.Global.getInt(cr, KEY_VERIFIER_ADB, 1) == 0 ||
            Settings.Global.getInt(cr, KEY_VERIFIER_ENABLE, 1) == 0
    }.getOrDefault(false)

    /**
     * 开关安装校验。返回是否成功。
     *
     * 先直接写（系统应用 / 有 WRITE_SECURE_SETTINGS 时能成），失败再走 root。
     */
    suspend fun setVerificationDisabled(context: Context, disabled: Boolean): Boolean {
        val value = if (disabled) 0 else 1
        val direct = runCatching {
            Settings.Global.putInt(context.contentResolver, KEY_VERIFIER_ADB, value)
            Settings.Global.putInt(context.contentResolver, KEY_VERIFIER_ENABLE, value)
            true
        }.getOrDefault(false)
        if (direct) {
            Log.i(TAG, "verification disabled=$disabled (direct)")
            return true
        }
        val (ok1, out1) = RootShell.run("settings put global $KEY_VERIFIER_ADB $value")
        val (ok2, out2) = RootShell.run("settings put global $KEY_VERIFIER_ENABLE $value")
        val ok = ok1 && ok2
        Log.i(TAG, "verification disabled=$disabled via root: $ok ${out1.take(60)} ${out2.take(60)}")
        // 顺手把本进程的安装校验也关掉（Android 14+ 的隐藏接口）
        if (ok && disabled) runCatching { VerificationBypass.disableForUid(context) }
        return ok
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }.getOrDefault(false)

    /** 弹系统的「忽略电池优化」确认框（需要 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS）。 */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Log.w(TAG, "requestIgnoreBatteryOptimizations failed", it)
            // 没权限弹窗就退回到列表页，让用户自己点
            openBatteryOptimizationSettings(context)
        }
    }

    /** 打开系统的电池优化列表（用来取消忽略）。 */
    fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            context.startActivity(
                android.content.Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "openBatteryOptimizationSettings failed", it) }
    }
}
