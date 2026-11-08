package com.panl.installer.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.panl.installer.R
import com.panl.installer.core.ApkInspector
import com.panl.installer.core.ApkStaging
import com.panl.installer.core.LiveInstallService
import com.panl.installer.core.Notifications
import com.panl.installer.core.Settings
import com.panl.installer.i18n.tr
import com.panl.installer.ui.install.InstallController
import com.panl.installer.ui.install.InstallScreen
import com.panl.installer.ui.install.InstallSheet
import com.panl.installer.ui.theme.InstallerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 安装页宿主。所有「装东西」的入口最终都落到这里。
 *
 * 三种形态（设置里可切）：
 *  - 浮窗：窗口半透明 + `FLAG_BLUR_BEHIND`，系统把身后的界面糊掉，卡片贴底或居中；
 *  - 全屏：传统整页；
 *  - 浮窗 + 通知：浮窗只做确认，点「安装」后关掉浮窗，安装交给 [LiveInstallService]。
 *
 * 形态必须在 `setTheme` 阶段定下来（translucent 是窗口属性，中途改不了），
 * 所以这里在 `super.onCreate` 之前先读一次设置。
 */
/**
 * 应用级作用域：不跟着 Activity 的销毁取消。
 *
 * 实时通知模式下要把 content:// 复制到缓存再交给服务，这一步不能因为 Activity
 * 被系统回收（或者用户又点了一次）而中断。
 */
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

class InstallActivity : ComponentActivity() {

    private lateinit var controller: InstallController

    // 「浮窗+通知」也是浮窗，只是点安装之后交给服务
    private val sheetStyle: Boolean
        get() = Settings.get(this).installStyle.let { it == STYLE_SHEET || it == STYLE_SHEET_LIVE }

    private val liveStyle: Boolean
        get() = Settings.get(this).installStyle == STYLE_LIVE

    /** 点「安装」后转实时通知（浮窗只做确认）。 */
    private val handOffStyle: Boolean
        get() = Settings.get(this).installStyle == STYLE_SHEET_LIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        val sheet = sheetStyle
        val live = liveStyle
        // 全屏态要显式换回不透明主题，否则窗口还是半透明的；实时通知模式用不可见窗口
        setTheme(
            when {
                sheet -> R.style.Theme_PuiInstaller_Sheet
                live -> R.style.Theme_PuiInstaller_Invisible
                else -> R.style.Theme_PuiInstaller
            }
        )
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (sheet) applyBlurBehind()

        // 实时通知：界面完全不出现，把 uri 丢给服务就撤
        if (live) {
            val uri = intent?.data
            val auto = intent?.getBooleanExtra(Notifications.EXTRA_LIVE_AUTO_START, false) == true
            if (uri == null) {
                finish()
                return
            }
            // 先把「正在读取安装包」挂出来：大包复制要一两秒，界面上什么都不显示，
            // 用户会以为没点到，于是反复点——第二次点击又会顶掉这次的任务。
            Notifications.showReading(this)
            // content:// 的读权限绑在这个 Activity 上，finish 之后就没；先落地成缓存文件。
            // 用应用级作用域而不是 lifecycleScope：Activity 被回收/被下一次点击顶掉时，
            // 复制和启动服务不能跟着取消，否则就是「点了没反应」。
            appScope.launch {
                val file = withContext(Dispatchers.IO) { ApkStaging.stage(this@InstallActivity, uri) }
                val info = file?.let { withContext(Dispatchers.IO) { ApkInspector.inspect(this@InstallActivity, it) } }
                if (info == null) {
                    Notifications.cancelProgress(this@InstallActivity)
                    Notifications.post(
                        this@InstallActivity,
                        tr("读取安装包失败", "Failed to read the APK"),
                        tr("无法打开 $uri", "Can't open $uri")
                    )
                } else {
                    LiveInstallService.start(this@InstallActivity, info, uri, auto)
                }
                runCatching { finish() }
            }
            return
        }


        controller = InstallController(this, handOffToLive = handOffStyle)
        setContent {
            InstallerTheme {
                if (sheetStyle) {
                    InstallSheet(controller = controller, onClose = { finish() })
                } else {
                    InstallScreen(controller = controller, onClose = { finish() })
                }
            }
        }
        controller.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller.handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 流体云模式没有初始化 controller（页面直接 finish 了）
        if (::controller.isInitialized && isFinishing) controller.cleanupOnExit()
    }

    /** 系统级背景模糊（Android 12+，需要设备支持 cross-window blur）。 */
    private fun applyBlurBehind() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val lp = window.attributes
            lp.setBlurBehindRadius(Settings.get(this).sheetBlurPx)
            window.attributes = lp
        }
    }

    companion object {
        const val STYLE_SHEET = "Sheet"
        const val STYLE_LIVE = "Live"

        /** 浮窗确认 + 实时通知安装。 */
        const val STYLE_SHEET_LIVE = "SheetLive"
        const val STYLE_FULL = "Full"
    }
}
