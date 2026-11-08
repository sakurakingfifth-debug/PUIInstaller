package com.panl.installer.core

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import com.panl.installer.corepatch.CorePatchPrefs
import com.panl.installer.i18n.tr

/**
 * 「实时通知」安装方式的后台流程：全程不显示界面，只在通知/胶囊里播报。
 *
 * 三个坑（都踩过了）：
 *  1. 必须是**前台服务**，不能拿 Activity 当宿主 —— ColorOS 只在应用不在前台时显示自己的实时活动；
 *  2. `content://` 的读权限绑在接收 intent 的 Activity 上，所以复制到缓存这一步在 Activity 里做完，
 *     服务只接收**文件路径**；
 *  3. `stopForeground(DETACH)` 会把 `startForeground` 那条重新发布，覆盖掉期间的 notify，
 *     所以要先 detach 再发最终通知。
 */
class LiveInstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var detached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "onStartCommand action=$action")

        if (action == ACTION_CANCEL) {
            Notifications.cancelProgress(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val path = intent?.getStringExtra(EXTRA_FILE)
        val label = intent?.getStringExtra(EXTRA_LABEL).orEmpty()
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val sourceUri = intent?.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        val file = path?.let(::File)

        if (file == null || !file.exists()) {
            Notifications.post(
                this,
                tr("读取安装包失败", "Failed to read the APK"),
                tr("暂存文件已丢失，请重新选择安装包", "The staged file is gone — pick the APK again")
            )
            stopSelf()
            return START_NOT_STICKY
        }

        // 前台服务：系统要求 5 秒内 startForeground
        runCatching {
            startForeground(
                Notifications.LIVE_ID,
                Notifications.progressNotification(
                    this, label, tr("正在读取安装包…", "Reading the APK…"), 0, false
                )
            )
        }

        job?.cancel()
        job = scope.launch {
            var keep = false
            try {
                keep = if (action == ACTION_START) {
                    install(file, label, packageName, sourceUri)
                } else if (Settings.get(this@LiveInstallService).autoInstallLive) {
                    // 开了「自动安装」：不问了，直接装
                    install(file, label, packageName, sourceUri)
                } else {
                    // 先解绑前台身份，否则这条提示会被「正在读取安装包」盖回去
                    detach()
                    // 等宿主 Activity 真正退到后台再发提示：ColorOS 不会给还在前台的
                    // 应用提升胶囊，抢在这之前发就会「点了没反应」（再点一次才好）。
                    delay(400)
                    Notifications.showInstallPrompt(this@LiveInstallService, label, packageName, file, sourceUri)
                    true
                }
            } catch (t: Throwable) {
                Log.e(TAG, "flow failed", t)
                InstallerLog.record(this@LiveInstallService, "LIVE", "实时通知安装流程异常", t)
                Notifications.cancelProgress(this@LiveInstallService)
            } finally {
                if (!detached) {
                    runCatching {
                        stopForeground(if (keep) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
                    }
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /** @return 是否把通知留在通知栏 / 胶囊里。 */
    private suspend fun install(
        file: File,
        label: String,
        packageName: String,
        sourceUri: Uri?
    ): Boolean {
        val promoted = Notifications.canPromote(this)
        val settings = Settings.get(this)

        InstallEngine.Bus.outcome = null
        Notifications.showInstall(
            this, label, Notifications.Stage.COPYING, 0f,
            tr("正在写入安装会话", "Writing the install session"), promoted
        )

        val sessionId = InstallEngine.install(
            context = this,
            apk = file,
            packageName = packageName,
            allowDowngrade = true,
            safeMode = settings.safeMode
        ) { p ->
            Notifications.showInstall(
                this, label, Notifications.Stage.COPYING, p,
                tr("正在复制安装包", "Copying the APK"), promoted
            )
        }

        if (sessionId == -1) {
            fail(label, InstallEngine.Bus.outcome?.message ?: tr("创建安装会话失败", "Failed to create the install session"))
            return false
        }

        Notifications.showInstall(
            this, label, Notifications.Stage.INSTALLING, null,
            tr("正在安装，请稍候", "Installing, please wait"), promoted
        )

        // 服务里没有组合树，直接轮询结果总线
        val outcome = withTimeoutOrNull(180_000) {
            while (InstallEngine.Bus.outcome == null) delay(200)
            InstallEngine.Bus.outcome
        }

        if (outcome == null) {
            fail(label, tr("安装超时，系统没有返回结果", "Install timed out — the system returned no result"))
            return false
        }
        var result = outcome
        // 降级被系统拒：核心破解 hook 可能被别的模块顶掉，用 root 兜底
        if (!result.success && result.downgradeBlocked && CorePatchPrefs.activeInSystem) {
            val rootOutcome = withContext(Dispatchers.IO) { InstallEngine.installAsRoot(this@LiveInstallService, file) }
            if (rootOutcome.success) result = result.copy(success = true, message = "Success")
        }
        if (!result.success) {
            fail(label, result.message)
            return false
        }

        // 装成功就留一条历史（文件马上要删，先解析）
        withContext(Dispatchers.IO) {
            ApkInspector.inspect(this@LiveInstallService, file)?.let { InstallHistory.record(this@LiveInstallService, it) }
        }
        detach()
        Notifications.showInstalled(this, label, packageName, popup = settings.popupOnDone)

        if (settings.deleteApkAfterInstall && sourceUri != null) {
            withContext(Dispatchers.IO) { SourceCleaner.delete(this@LiveInstallService, sourceUri, null) }
        }
        // 只删自己的缓存副本，file:// 传进来的是用户原文件
        withContext(Dispatchers.IO) {
            if (file.absolutePath.startsWith(cacheDir.absolutePath)) runCatching { file.delete() }
        }
        return true
    }

    private fun fail(label: String, message: String) {
        detach()
        Notifications.showFailed(this, label, message)
    }

    /**
     * 撤掉前台身份**连同那条「正在读取安装包」通知**。
     *
     * 不能用 DETACH：它会把 startForeground 那条重新发布，异步落下来正好盖掉
     * 紧接着发的提示/完成通知（表现就是「一直卡在正在读取」）。
     */
    private fun detach() {
        if (detached) return
        detached = true
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LiveInstall"
        const val ACTION_PREPARE = "com.panl.installer.action.LIVE_PREPARE"
        const val ACTION_START = "com.panl.installer.action.LIVE_START"
        const val ACTION_CANCEL = "com.panl.installer.action.LIVE_CANCEL"
        const val EXTRA_FILE = "com.panl.installer.extra.LIVE_FILE"
        const val EXTRA_LABEL = "com.panl.installer.extra.LIVE_LABEL"
        const val EXTRA_PACKAGE = "com.panl.installer.extra.LIVE_PACKAGE"
        const val EXTRA_URI = "com.panl.installer.extra.LIVE_URI"

        /** 入口：Activity 已经把 APK 落到缓存，这里只传路径。 */
        fun start(context: Context, info: ApkInfo, sourceUri: Uri?, autoStart: Boolean) {
            startRaw(context, info.file, info.label, info.packageName, sourceUri, autoStart)
        }

        /**
         * 直接按「文件 + 文案」启动。
         *
         * 给 [BiometricInstallActivity] 用：认证通过后手上只有路径和包名，没有 ApkInfo。
         */
        fun startRaw(
            context: Context,
            file: File,
            label: String,
            packageName: String,
            sourceUri: Uri?,
            autoStart: Boolean
        ) {
            val intent = Intent(context, LiveInstallService::class.java)
                .setAction(if (autoStart) ACTION_START else ACTION_PREPARE)
                .putExtra(EXTRA_FILE, file.absolutePath)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_URI, sourceUri?.toString())
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.e(TAG, "startForegroundService failed: ${it.javaClass.simpleName} ${it.message}") }
        }
    }
}
