package com.panl.installer.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.panl.installer.i18n.tr

/**
 * 拨号盘的「暗号」：`*#*#078#*#*`。
 *
 * Android 只允许通过 `android.provider.Telephony.SECRET_CODE` 接管 `*#*#...#*#*` 这种形式，
 * 裸的 `*#078#` 是 MMI 码，只有默认拨号器能看到，第三方应用拿不到 —— 所以实际触发码是
 * `*#*#078#*#*`（拨号盘会直接本地拦截，不会发到网络）。
 *
 * 作用：切换桌面图标的显示/隐藏。图标藏起来之后，这就是唯一的入口。
 */
class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != "android.provider.Telephony.SECRET_CODE" && action != ACTION_TOGGLE_ICON) return
        Log.i("SecretCode", "trigger: $action ${intent.data}")

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
