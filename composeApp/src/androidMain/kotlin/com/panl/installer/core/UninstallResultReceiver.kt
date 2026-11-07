package com.panl.installer.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.panl.installer.i18n.tr

/** 卸载结果回调。 */
class UninstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val raw = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val packageName = intent.getStringExtra(InstallEngine.EXTRA_PACKAGE_NAME)
            ?: intent.data?.schemeSpecificPart

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) {
                runCatching {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }.onFailure { Log.w("UninstallReceiver", "start confirm failed", it) }
            }
            return
        }

        val success = status == PackageInstaller.STATUS_SUCCESS
        val message = if (success) tr("卸载成功", "Uninstalled") else friendly(raw, status)

        InstallEngine.UninstallBus.outcome = InstallEngine.Outcome(
            success = success,
            packageName = packageName,
            message = message,
            statusCode = status
        )

        if (Settings.get(context).showNotification) {
            Notifications.post(
                context,
                if (success) tr("卸载完成", "Uninstalled") else tr("卸载失败", "Uninstall failed"),
                "${packageName ?: "应用"}：$message"
            )
        }
    }

    private fun friendly(raw: String, status: Int): String {
        val code = raw.substringBefore(":").substringBefore(" ").trim()
        return when {
            code.contains("DELETE_FAILED_DEVICE_POLICY_MANAGER") -> tr("设备策略管理器不允许卸载", "Blocked by device policy")
            code.contains("DELETE_FAILED_USER_RESTRICTED") -> tr("当前用户被限制卸载", "The current user can't uninstall")
            code.contains("DELETE_FAILED_OWNER_BLOCKED") -> tr("设备管理员阻止了卸载", "A device admin blocked the uninstall")
            code.contains("DELETE_FAILED_INTERNAL_ERROR") -> tr("系统内部错误", "Internal system error")
            code.contains("DELETE_FAILED_ABORTED") -> tr("卸载已取消", "Uninstall cancelled")
            status == PackageInstaller.STATUS_FAILURE_ABORTED -> tr("卸载已取消", "Uninstall cancelled")
            status == PackageInstaller.STATUS_FAILURE_BLOCKED -> tr("卸载被系统拦截", "Uninstall blocked by the system")
            status == PackageInstaller.STATUS_FAILURE_INVALID -> tr("包名无效", "Invalid package name")
            else -> raw.ifBlank { tr("未知错误（status=$status）", "Unknown error (status=$status)") }
        }
    }
}
