package com.panl.installer.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.panl.installer.R
import com.panl.installer.core.ApkDownloader
import com.panl.installer.core.ApkInspector
import com.panl.installer.core.ApkStaging
import com.panl.installer.core.BiometricInstallActivity
import com.panl.installer.core.Formatters
import com.panl.installer.core.InstallerLog
import com.panl.installer.core.LiveInstallService
import com.panl.installer.core.Notifications
import com.panl.installer.core.Settings
import com.panl.installer.core.WebGate
import com.panl.installer.i18n.tr
import com.panl.installer.ui.install.InstallController
import com.panl.installer.ui.install.InstallScreen
import com.panl.installer.ui.install.InstallSheet
import com.panl.installer.ui.install.displayNameOf
import com.panl.installer.ui.install.sharedStream
import com.panl.installer.ui.install.sharedStreams
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
        // 模块 zip 在实时通知模式下没有界面，但刷模块必须人工确认，所以 .zip 一律走浮窗。
        // 分享过来的可能是 content://，path 段里没有文件名，得问 provider 要显示名。
        val incomingUri = intent?.data ?: intent?.sharedStream()
        val incomingName = displayNameOf(incomingUri) ?: incomingUri?.lastPathSegment
        val isZip = incomingName?.endsWith(".zip", ignoreCase = true) == true
        // 多选分享要给人看列表、逐个确认结果，实时通知那种「无界面」形态不适合
        val isBatch = (intent?.sharedStreams()?.size ?: 0) > 1
        val forceSheet = intent?.getBooleanExtra(EXTRA_FORCE_SHEET, false) == true
        val sheet = sheetStyle || forceSheet || isBatch || (liveStyle && isZip)
        val live = liveStyle && !sheet
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
            // 分享文件来的是 EXTRA_STREAM，不一定是 intent.data
            val uri = intent?.data ?: intent?.sharedStream()
            val auto = intent?.getBooleanExtra(Notifications.EXTRA_LIVE_AUTO_START, false) == true
            // 「分享链接」：浏览器分享过来的是文本，不是文件，得先自己下载
            val sharedUrl = if (intent?.action == Intent.ACTION_SEND) {
                ApkDownloader.extractUrl(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
            } else {
                null
            }
            if (uri == null && sharedUrl == null) {
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
                val localUri = if (uri != null) {
                    uri
                } else {
                    val downloaded = downloadForLive(sharedUrl!!)
                    if (downloaded == null) {
                        Notifications.cancelProgress(this@InstallActivity)
                        Notifications.post(
                            this@InstallActivity,
                            tr("下载失败", "Download failed"),
                            tr("无法从链接下载安装包", "Couldn't download the APK from that link")
                        )
                        runCatching { finish() }
                        return@launch
                    }
                    downloaded
                }
                val file = withContext(Dispatchers.IO) { ApkStaging.stage(this@InstallActivity, localUri) }
                // 模块 zip（里面是 module.prop 不是 APK）：实时通知模式没有界面，刷模块又必须人工确认，
                // 所以改成用浮窗重新打开自己，直接进刷入页
                if (file != null && Settings.get(this@InstallActivity).moduleFlashEnabled) {
                    val module = withContext(Dispatchers.IO) {
                        com.panl.installer.core.ModuleInspector.inspect(file)
                    }
                    if (module != null) {
                        // 刷模块必须有人点确认，但这个 Activity 正在退场，自己再拉一个浮窗
                        // 会被 ColorOS 丢掉（launch 被判定 delivered-to-top 后整条丢弃），
                        // 所以留一条「点此刷入」的通知，点开就是浮窗刷入页
                        Notifications.cancelProgress(this@InstallActivity)
                        Notifications.showModuleReady(this@InstallActivity, module.name, file)
                        runCatching { finish() }
                        return@launch
                    }
                }
                val info = file?.let { withContext(Dispatchers.IO) { ApkInspector.inspect(this@InstallActivity, it) } }
                if (info == null) {
                    Notifications.cancelProgress(this@InstallActivity)
                    Notifications.post(
                        this@InstallActivity,
                        tr("读取安装包失败", "Failed to read the APK"),
                        tr("无法打开 $localUri", "Can't open $localUri")
                    )
                } else if (
                    // 「自动安装」有两种来源：intent 里的 extra，或者设置里那个开关
                    (auto || Settings.get(this@InstallActivity).autoInstallLive) &&
                    Settings.get(this@InstallActivity).biometricBeforeInstall
                ) {
                    // 自动安装 + 生物识别：先过认证，再交给服务（服务里没有界面，弹不了认证框）
                    BiometricInstallActivity.start(
                        this@InstallActivity, info.file, info.label, info.packageName, localUri
                    )
                } else {
                    LiveInstallService.start(this@InstallActivity, info, localUri, auto)
                }
                runCatching { finish() }
            }
            return
        }


        controller = InstallController(this, handOffToLive = handOffStyle)
        setContent {
            InstallerTheme {
                // 用 sheet（含 forceSheet）而不是 sheetStyle：
                // 实时通知模式下刷模块会强制转成浮窗，只看设置会把内容渲染成全屏
                if (sheet) {
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
        // 实时通知模式下这个 Activity 没有 controller（页面直接 finish 了），
        // 复用实例时再来一个 intent 只会崩，忽略即可
        if (!::controller.isInitialized) {
            runCatching { finish() }
            return
        }
        controller.handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 流体云模式没有初始化 controller（页面直接 finish 了）
        if (::controller.isInitialized && isFinishing) controller.cleanupOnExit()
    }

    /**
     * 实时通知模式下的「分享链接」：下载进度写进那条实时通知，下完交给 [LiveInstallService]，
     * 全程不出现界面 —— 和点开本地 APK 的行为完全一致。
     *
     * @return 落地后的 file:// Uri；失败返回 null。
     */
    private suspend fun downloadForLive(url: String): Uri? {
        val name = ApkDownloader.guessName(url)
        val engine = ApkDownloader(this)
        InstallerLog.record(this, "DOWNLOAD", "开始下载 $url")
        var cookie: String? = null
        var target = url
        var result: ApkDownloader.Result = ApkDownloader.Result.Failure("下载失败")
        var round = 0
        while (round < 2) {
            result = withContext(Dispatchers.IO) {
                var lastAt = 0L
                engine.download(target, cookie) { received, total, speed ->
                    // 通知栏刷新不用太勤，600ms 一次看着已经很连续了
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (lastAt != 0L && now - lastAt < 600) return@download
                    lastAt = now
                    Notifications.showInstall(
                        context = this@InstallActivity,
                        label = name,
                        stage = Notifications.Stage.DOWNLOADING,
                        fraction = if (total > 0) received.toFloat() / total else null,
                        text = listOf(Formatters.size(received), ApkDownloader.speedText(speed))
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        promoted = Notifications.canPromote(this@InstallActivity)
                    )
                }
            }
            if (result !is ApkDownloader.Result.NeedsWeb || round == 1) break
            round++
            // 网盘直链的 JS 验证：借内置浏览器过一遍拿 cookie / 它给的一次性直链
            InstallerLog.record(this, "DOWNLOAD", "链接需要网页验证，改用内置浏览器 $url")
            val gate = WebGate.harvest(this, target)
            cookie = gate?.cookie
            if (cookie.isNullOrBlank()) {
                result = ApkDownloader.Result.Failure("这个链接要做网页验证，内置浏览器没能通过")
                break
            }
            if (!gate?.downloadUrl.isNullOrBlank()) target = gate!!.downloadUrl!!
        }
        return (result as? ApkDownloader.Result.Success)?.file?.let {
            InstallerLog.record(this, "DOWNLOAD", "下载完成 ${it.name} (${Formatters.size(it.length())})")
            Uri.fromFile(it)
        }
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
        /** 模块 zip 从实时通知模式转浮窗时带这个 extra。 */
        const val EXTRA_FORCE_SHEET = "force_sheet"

        const val STYLE_SHEET = "Sheet"
        const val STYLE_LIVE = "Live"

        /** 浮窗确认 + 实时通知安装。 */
        const val STYLE_SHEET_LIVE = "SheetLive"
        const val STYLE_FULL = "Full"
    }
}
