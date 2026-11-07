package com.panl.installer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.installer.ui.uninstall.UninstallController
import com.panl.installer.ui.uninstall.UninstallScreen

/**
 * 卸载 / 应用信息页。
 *
 * 同时承担三种入口：
 *  - `ACTION_DELETE` / `ACTION_UNINSTALL_PACKAGE`（设置、桌面长按卸载）
 *  - `ACTION_SHOW_APP_INFO`（LibChecker 等工具点应用图标）
 *  - 应用内部的「已安装应用」列表
 */
class UninstallActivity : ComponentActivity() {

    private lateinit var controller: UninstallController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        controller = UninstallController(this)
        setContent {
            InstallerTheme {
                UninstallScreen(controller = controller, onClose = { finish() })
            }
        }
        controller.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller.handleIntent(intent)
    }

    companion object {
        const val EXTRA_PACKAGE = "com.panl.installer.extra.PACKAGE"
    }
}
