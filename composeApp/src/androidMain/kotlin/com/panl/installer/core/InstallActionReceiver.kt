package com.panl.installer.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.panl.installer.i18n.tr

/** 成功通知上的「打开」按钮。 */
class InstallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifications.ACTION_OPEN) return
        val pkg = intent.getStringExtra(Notifications.EXTRA_OPEN_PACKAGE)
        if (pkg.isNullOrBlank()) return
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(context, tr("无法打开这个应用", "Can't open this app"), Toast.LENGTH_SHORT).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
        // 点完就把胶囊收掉
        Notifications.cancelProgress(context)
    }
}
