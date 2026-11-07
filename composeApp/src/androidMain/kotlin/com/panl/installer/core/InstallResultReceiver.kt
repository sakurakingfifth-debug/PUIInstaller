package com.panl.installer.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.panl.installer.i18n.tr

/** 安装会话的最终状态回调。 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val packageName = intent.getStringExtra(InstallEngine.EXTRA_PACKAGE_NAME)

        // 没有系统安装权限时，系统会把「确认安装」的界面抛回来，转交即可
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) {
                runCatching {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }.onFailure { Log.w("InstallResultReceiver", "start confirm failed", it) }
            }
            return
        }

        val success = status == PackageInstaller.STATUS_SUCCESS
        val message = if (success) tr("安装成功", "Installed") else friendlyError(status, rawMessage)

        InstallEngine.Bus.outcome = InstallEngine.Outcome(
            success = success,
            packageName = packageName,
            message = message,
            statusCode = status,
            downgradeBlocked = !success && rawMessage.contains("VERSION_DOWNGRADE")
        )

        // 结果通知统一由 InstallController / LiveInstallService 按「通知样式」发，
        // 这里再发一条会重复（以前靠 liveUpdate 开关互斥，现在两档样式都要发）
    }

    private fun friendlyError(status: Int, raw: String): String {
        val code = raw.substringBefore(":").substringBefore(" ").trim()
        val known = when {
            code.contains("INSTALL_FAILED_ALREADY_EXISTS") -> tr("已存在同包名应用", "An app with the same package name already exists")
            code.contains("INSTALL_FAILED_INVALID_APK") -> tr("安装包无效或已损坏", "The APK is invalid or corrupted")
            code.contains("INSTALL_FAILED_INVALID_URI") -> tr("安装包路径无效", "Invalid APK path")
            code.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> tr("存储空间不足", "Not enough storage")
            code.contains("INSTALL_FAILED_DUPLICATE_PACKAGE") -> tr("已存在同名应用", "An app with the same name already exists")
            code.contains("INSTALL_FAILED_NO_SHARED_USER") -> tr("共享用户不匹配", "Shared user mismatch")
            code.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") -> tr("与已安装版本签名不一致", "Signature doesn't match the installed version")
            code.contains("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE") -> tr("共享用户不兼容", "Incompatible shared user")
            code.contains("INSTALL_FAILED_MISSING_SHARED_LIBRARY") -> tr("缺少共享库", "Missing shared library")
            code.contains("INSTALL_FAILED_REPLACE_COULDNT_DELETE") -> tr("无法覆盖旧版本", "Can't replace the installed version")
            code.contains("INSTALL_FAILED_DEXOPT") -> tr("dex 优化失败", "dex optimization failed")
            code.contains("INSTALL_FAILED_OLDER_SDK") -> tr("应用要求更高的系统版本", "The app requires a newer Android version")
            code.contains("INSTALL_FAILED_NEWER_SDK") -> tr("应用不支持当前系统版本", "The app doesn't support this Android version")
            code.contains("INSTALL_FAILED_TEST_ONLY") -> tr("这是 testOnly 调试包", "This is a testOnly debug build")
            code.contains("INSTALL_FAILED_CPU_ABI_INCOMPATIBLE") -> tr("CPU 架构不兼容", "Incompatible CPU architecture")
            code.contains("INSTALL_FAILED_MISSING_FEATURE") -> tr("设备缺少必需特性", "The device is missing required features")
            code.contains("INSTALL_FAILED_CONTAINER_ERROR") -> tr("安装容器错误", "Install container error")
            code.contains("INSTALL_FAILED_CONFLICTING_PROVIDER") -> tr("ContentProvider 冲突", "ContentProvider conflict")
            code.contains("INSTALL_FAILED_SESSION_INVALID") -> tr("安装会话已失效", "The install session has expired")
            code.contains("INSTALL_FAILED_VERIFICATION_FAILURE") -> tr("安装校验失败", "Install verification failed")
            code.contains("INSTALL_FAILED_PACKAGE_CHANGED") -> tr("安装包在安装过程中被修改", "The APK changed during installation")
            code.contains("INSTALL_FAILED_UID_CHANGED") -> tr("应用 UID 发生变化", "The app's UID changed")
            code.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> tr("版本低于已安装版本（需允许降级）", "Version is lower than the installed one (downgrade must be allowed)")
            code.contains("INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE") -> tr("权限模型不支持降级", "The permission model doesn't support downgrades")
            code.contains("INSTALL_PARSE_FAILED") -> tr("解析安装包失败", "Failed to parse the APK")
            status == PackageInstaller.STATUS_FAILURE_ABORTED -> tr("安装已取消", "Install cancelled")
            status == PackageInstaller.STATUS_FAILURE_BLOCKED -> tr("安装被系统拦截", "Install blocked by the system")
            status == PackageInstaller.STATUS_FAILURE_CONFLICT -> tr("与已安装应用冲突", "Conflicts with an installed app")
            status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> tr("与当前系统不兼容", "Incompatible with this Android version")
            status == PackageInstaller.STATUS_FAILURE_INVALID -> tr("安装包无效", "Invalid APK")
            status == PackageInstaller.STATUS_FAILURE_STORAGE -> tr("存储空间不足", "Not enough storage")
            else -> null
        }
        return known ?: raw.ifBlank { tr("未知错误（status=$status）", "Unknown error (status=$status)") }
    }
}
