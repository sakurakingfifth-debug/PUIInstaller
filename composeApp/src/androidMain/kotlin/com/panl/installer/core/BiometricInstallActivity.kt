package com.panl.installer.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.panl.installer.R
import com.panl.installer.i18n.tr
import kotlinx.coroutines.launch
import java.io.File

/**
 * 实时通知模式的「认证中转站」。
 *
 * 实时通知这条线全程没有界面：服务直接把包装了，所以浮窗那条路上的生物识别
 * 根本管不到它。这个 Activity 就是一个**不可见**的中转：
 *
 *  1. 弹系统认证（[BiometricGate]）；
 *  2. 通过 → 把原来的参数原样交给 [LiveInstallService]（ACTION_START）；
 *  3. 取消/失败 → 发一条失败通知，什么都不装。
 *
 * 两个入口都会走到这里：
 *  - 通知里点「安装」（手动确认）；
 *  - 开了「自动安装」时，安装页直接把包丢给服务之前。
 */
class BiometricInstallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PuiInstaller_Auth)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val path = intent?.getStringExtra(LiveInstallService.EXTRA_FILE)
        val label = intent?.getStringExtra(LiveInstallService.EXTRA_LABEL).orEmpty()
        val packageName = intent?.getStringExtra(LiveInstallService.EXTRA_PACKAGE).orEmpty()
        val sourceUri = intent?.getStringExtra(LiveInstallService.EXTRA_URI)?.let(Uri::parse)
        val file = path?.let(::File)

        if (file == null || !file.exists()) {
            Log.w(TAG, "no staged file, abort")
            finish()
            return
        }

        Log.i(TAG, "onCreate label=$label pkg=$packageName file=${file.absolutePath}")
        lifecycleScope.launch {
            val passed = BiometricGate.authenticate(this@BiometricInstallActivity)
            Log.i(TAG, "auth passed=$passed")
            if (passed) {
                LiveInstallService.startRaw(
                    this@BiometricInstallActivity,
                    file,
                    label,
                    packageName,
                    sourceUri,
                    autoStart = true
                )
            } else {
                InstallerLog.record(this@BiometricInstallActivity, "INSTALL", "生物认证未通过，安装取消 $packageName")
                Notifications.showFailed(
                    this@BiometricInstallActivity,
                    label,
                    tr("认证未通过，安装已取消", "Authentication failed — install cancelled")
                )
            }
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val TAG = "BiometricInstall"

        /** 从通知按钮或安装页拉起认证中转。 */
        fun start(context: Context, file: File, label: String, packageName: String, sourceUri: Uri?) {
            val intent = Intent(context, BiometricInstallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(LiveInstallService.EXTRA_FILE, file.absolutePath)
                .putExtra(LiveInstallService.EXTRA_LABEL, label)
                .putExtra(LiveInstallService.EXTRA_PACKAGE, packageName)
                .putExtra(LiveInstallService.EXTRA_URI, sourceUri?.toString())
            runCatching { context.startActivity(intent) }
                .onFailure { Log.e(TAG, "start failed", it) }
        }
    }
}
