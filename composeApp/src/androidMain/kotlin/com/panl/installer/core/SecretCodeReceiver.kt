package com.panl.installer.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.panl.installer.i18n.tr
import com.panl.installer.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 拨号盘的「暗号」：`*#*#078#*#*`。
 *
 * Android 只允许通过 `android.provider.Telephony.SECRET_CODE` 接管 `*#*#...#*#*` 这种形式，
 * 裸的 `*#078#` 是 MMI 码，只有默认拨号器能看到，第三方应用拿不到 —— 所以实际触发码是
 * `*#*#078#*#*`（拨号盘会直接本地拦截，不会发到网络）。
 *
 * 作用：**打开安装器本体**。桌面图标藏起来之后，这就是唯一的入口。
 *
 * 另外还处理安全网通知里「恢复」按钮的显式广播 [ACTION_TOGGLE_ICON]（切图标显隐）。
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i("SecretCode", "trigger: $action ${intent?.data}")

        when (action) {
            "android.provider.Telephony.SECRET_CODE" -> openInstaller(context)
            ACTION_TOGGLE_ICON -> toggleIcon(context)
        }
    }

    /**
     * 暗号的作用：把功能面板拉起来（图标藏着也能进）。
     *
     * 接收器运行在后台，直接 `startActivity` 会被系统的 BAL 拦掉
     * （日志 `Background activity launch blocked`）。所以：
     *  1. 有 root 就走 `su -c am start`，root/shell 有豁免；
     *  2. 没 root 就退一条可点开的通知，至少不会「点了没反应」。
     */
    private fun openInstaller(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                val target = "${context.packageName}/com.panl.installer.ui.MainActivity"
                val (ok, out) = RootShell.run("am start -n $target")
                if (ok && !out.contains("Error", ignoreCase = true)) return@launch
                Log.i("SecretCode", "root am start failed, fallback to notification: ${out.take(80)}")
                runCatching {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    )
                }
                Notifications.showOpenInstaller(context)
            } finally {
                pending.finish()
            }
        }
    }

    /** 通知里「恢复」按钮：切桌面图标显隐。 */
    private fun toggleIcon(context: Context) {
        val nowHidden = !LauncherIcon.isHidden(context)
        val ok = LauncherIcon.setHidden(context, nowHidden)
        val text = when {
            !ok -> tr("切换桌面图标失败", "Couldn't toggle the launcher icon")
            nowHidden -> tr("已隐藏桌面图标，再次输入可恢复", "Launcher icon hidden — enter the code again to restore")
            else -> tr("已恢复桌面图标", "Launcher icon restored")
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        /** 安全网通知里「恢复」按钮用的显式广播。 */
        const val ACTION_TOGGLE_ICON = "com.panl.installer.action.TOGGLE_LAUNCHER_ICON"
    }
}
