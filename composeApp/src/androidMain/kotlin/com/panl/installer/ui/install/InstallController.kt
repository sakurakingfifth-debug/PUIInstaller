package com.panl.installer.ui.install

import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.panl.installer.core.ApkInfo
import com.panl.installer.core.ApkInspector
import com.panl.installer.core.ApkStaging
import com.panl.installer.core.BiometricGate
import com.panl.installer.core.BundleReader
import com.panl.installer.core.InstallEngine
import com.panl.installer.core.InstallHistory
import com.panl.installer.core.LiveInstallService
import com.panl.installer.core.InstallerLog
import com.panl.installer.core.Notifications
import com.panl.installer.core.Settings
import com.panl.installer.corepatch.CorePatchPrefs
import com.panl.installer.core.SessionInstaller
import com.panl.installer.core.SourceCleaner
import com.panl.installer.core.VersionRelation
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import com.panl.installer.i18n.tr

/** 安装页的所有状态。 */
sealed interface InstallUiState {
    data object Loading : InstallUiState
    data class Preparing(val progress: Float?) : InstallUiState
    data class Ready(val info: ApkInfo) : InstallUiState
    data class Error(val title: String, val message: String) : InstallUiState
    data class Installing(val stage: String, val progress: Float?) : InstallUiState
    data class Success(
        val info: ApkInfo,
        val deleted: Boolean,
        val deleteFailed: Boolean
    ) : InstallUiState
    data class Failed(val info: ApkInfo?, val message: String) : InstallUiState

    /** 系统把别的应用创建的安装会话丢过来确认。 */
    data class SessionConfirm(
        val sessionId: Int,
        val packageName: String,
        val label: String,
        /** 从会话暂存目录里读到的安装包信息；读不到就是 null（会话不是我们建的时常见）。 */
        val apk: ApkInfo? = null
    ) : InstallUiState
}

/**
 * 安装页控制器：负责「解析 intent → 准备 APK → 安装 → 清理」这条链路。
 *
 * 之所以不塞进 Activity：这套流程要跨多个挂起点（复制、写会话、等广播），
 * 放在一起才看得清状态是怎么迁移的。
 */
class InstallController(
    private val activity: ComponentActivity,
    /**
     * 「浮窗 + 通知」样式：浮窗只负责确认，点「安装」后把活交给 [LiveInstallService]，
     * 界面立刻关掉，进度和结果都在实时通知里。
     */
    private val handOffToLive: Boolean = false
) {

    private val TAG = "InstallController"

    var state by mutableStateOf<InstallUiState>(InstallUiState.Loading)
        private set

    /** 安装成功后等待用户决定「是否删除安装包」。 */
    var pendingDelete by mutableStateOf(false)
        private set

    var deleteDialogVisible by mutableStateOf(false)
        private set

    private var sourceUri: Uri? = null
    private var stagedFile: File? = null
    /** 已经交给前台服务了：退出时别删缓存副本，服务还要读它。 */
    private var handedOff = false
    /** 拆分包解出来的临时目录，退出时一起删。 */
    private var bundleDir: File? = null
    private var installedInfo: ApkInfo? = null

    private val settings get() = Settings.get(activity)

    fun handleIntent(intent: Intent?) {
        if (intent == null) {
            state = InstallUiState.Error(tr("没有拿到安装包", "No APK received"), tr("请从文件管理器重新选择 APK", "Pick the APK again from your file manager"))
            return
        }

        if (intent.action == ACTION_CONFIRM_INSTALL) {
            handleConfirmSession(intent)
            return
        }

        // 受管安装器：列表非空时只允许这些应用发起安装（拿不到来源就放行）
        val allowed = settings.managedInstallerPackages
        val caller = activity.launchedFromPackage
        // 自己拉起的不算「外部来源」，否则应用内的选择安装包会被自己拦掉
        val selfCaller = caller != null && caller.equals(activity.packageName, ignoreCase = true)
        if (settings.sourcePresetEnabled && allowed.isNotEmpty() && caller != null && !selfCaller &&
            !allowed.contains(caller.lowercase())
        ) {
            InstallerLog.record(activity, "INSTALL", "受管安装器拦截 $caller")
            state = InstallUiState.Failed(
                null,
                tr(
                    "该安装请求来自未受管的应用（$caller），已拒绝",
                    "This install request comes from an unmanaged app ($caller) — blocked"
                )
            )
            return
        }

        val data: Uri? = intent.data
        if (data == null) {
            state = InstallUiState.Error(tr("没有拿到安装包", "No APK received"), tr("Intent 里没有文件地址", "The intent has no file URI"))
            return
        }
        sourceUri = data
        InstallEngine.Bus.outcome = null
        load(data)
    }

    private fun handleConfirmSession(intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (sessionId == -1) {
            state = InstallUiState.Error(tr("会话无效", "Invalid session"), tr("系统没有传入安装会话 ID", "The system didn't pass an install session ID"))
            return
        }
        val pm = activity.packageManager
        val info = runCatching { pm.packageInstaller.getSessionInfo(sessionId) }.getOrNull()
        val pkg = info?.appPackageName
            ?: intent.getStringExtra("android.content.pm.extra.PACKAGE_NAME")
        val label = runCatching {
            info?.appLabel?.toString()
        }.getOrNull() ?: pkg ?: tr("未知应用", "Unknown app")

        state = InstallUiState.SessionConfirm(
            sessionId = sessionId,
            packageName = pkg ?: tr("未知包名", "Unknown package"),
            label = label
        )
        // 会话不是本应用创建的：getSessionInfo 要么返回 null，要么字段全是空的
        // （包名只在 commit 之后才填），标题只能显示「未知应用」。
        // 暂存目录里的 APK 是能直接读的，解析一下把应用名补上。
        if (info?.appPackageName == null) {
            activity.lifecycleScope.launch {
                val apk = withContext(Dispatchers.IO) { SessionInstaller.inspect(activity, sessionId) }
                val current = state
                if (apk != null && current is InstallUiState.SessionConfirm &&
                    current.sessionId == sessionId
                ) {
                    state = current.copy(
                        packageName = apk.packageName,
                        label = apk.label,
                        apk = apk
                    )
                }
            }
        }
    }

    /** 复制到缓存目录再解析：content:// 只能顺序读一次，后面写会话还要再读。 */
    private fun load(uri: Uri) {
        activity.lifecycleScope.launch {
            state = InstallUiState.Preparing(null)
            val file = withContext(Dispatchers.IO) { stage(uri) }
            if (file == null) {
                state = InstallUiState.Error(tr("读取安装包失败", "Failed to read the APK"), tr("无法打开 $uri", "Can't open $uri"))
                return@launch
            }
            safeRetryDone = false
            // 拆分包（.apks/.xapk/.apkm）：先解包，拿 base.apk 去解析，其余分片挂在 info 上
            val bundle = withContext(Dispatchers.IO) {
                if (BundleReader.isBundle(file.name)) BundleReader.extract(activity, file) else null
            }
            bundleDir = bundle?.dir
            val target = bundle?.base ?: file
            val info = withContext(Dispatchers.IO) { ApkInspector.inspect(activity, target) }
                ?.copy(
                    sourceLabel = resolveSourceLabel(activity),
                    splits = bundle?.splits.orEmpty()
                )
            if (info == null) {
                state = InstallUiState.Error(
                    tr("无法解析安装包", "Can't parse the APK"),
                    tr("文件可能不是有效的 APK，或已损坏。", "The file may not be a valid APK, or it's corrupted.")
                )
                return@launch
            }
            installedInfo = info
            state = InstallUiState.Ready(info)
        }
    }

    /** 只有 content:// 才需要复制；file:// 直接读原文件，**不能**当成自己的暂存副本删掉。 */
    private fun stage(uri: Uri): File? {
        val file = ApkStaging.stage(activity, uri)
        if (file != null && uri.scheme != "file") stagedFile = file
        return file
    }

    fun startInstall() {
        val info = (state as? InstallUiState.Ready)?.info ?: return

        // 包名黑名单 / SharedUID 黑名单：命中直接拒
        val uid = info.sharedUserId
        // SharedUID 是「android.uid.phone」这种字符串，整串比对（忽略大小写）
        val uidBlocked = uid != null &&
            settings.blacklistSharedUids.any { it.equals(uid, ignoreCase = true) }
        if (settings.blacklistPackages.contains(info.packageName.lowercase()) || uidBlocked) {
            InstallerLog.record(activity, "INSTALL", "黑名单拦截 ${info.packageName}")
            state = InstallUiState.Failed(
                info,
                tr(
                    "该应用在黑名单中，已拒绝安装",
                    "This app is on the blacklist — install blocked"
                )
            )
            return
        }

        // 这条签名预检是**客户端**做的，和系统里的 hook 无关。
        // 核心破解已经接入系统框架时，校验就交给系统决定：模块里的开关状态
        // 有可能还没同步过来（远程 prefs 是异步的），在这里拦一道就会变成
        // 「开关全开却装不上」——用户报的就是这个。
        if (info.relation == VersionRelation.UPDATE &&
            info.signatureMatchesInstalled == false &&
            !CorePatchPrefs.activeInSystem
        ) {
            state = InstallUiState.Failed(
                info,
                tr(
                    "与已安装版本的签名不一致，无法覆盖安装。请先卸载旧版本。",
                    "The signature doesn't match the installed version, so it can't be installed over it. Uninstall the old version first."
                )
            )
            return
        }

        InstallEngine.Bus.outcome = null
        activity.lifecycleScope.launch {
            // 生物识别确认：开了就在真正动手前过一次系统认证（浮窗 / 浮窗+通知都算）
            if (settings.biometricBeforeInstall) {
                state = InstallUiState.Installing(
                    tr("等待设备认证…", "Waiting for device authentication…"),
                    null
                )
                val passed = BiometricGate.authenticate(activity)
                if (!passed) {
                    InstallerLog.record(activity, "INSTALL", "生物认证未通过，安装取消 ${info.packageName}")
                    state = InstallUiState.Failed(
                        info,
                        tr("认证未通过，安装已取消", "Authentication failed — install cancelled")
                    )
                    return@launch
                }
            }

            // 浮窗 + 通知：签名预检过了就直接交给前台服务，浮窗马上消失。
            // 会话创建、进度、结果通知、历史记录、删源文件都由服务负责。
            if (handOffToLive) {
                handedOff = true
                LiveInstallService.start(activity, info, sourceUri, autoStart = true)
                runCatching { activity.finish() }
                return@launch
            }

            if (settings.verifyBeforeInstall) {
                state = InstallUiState.Installing(tr("正在校验安装包", "Verifying the APK"), null)
                notifyProgress(info.label, Notifications.Stage.PREPARING, tr("正在校验安装包", "Verifying the APK"))
                val ok = withContext(Dispatchers.IO) { verify(info.file) }
                if (!ok) {
                    val message = tr("安装包结构校验失败：不是有效的 zip，或缺少 AndroidManifest.xml", "APK structure check failed: not a valid zip, or AndroidManifest.xml is missing")
                    stopProgress()
                    if (settings.showNotification) Notifications.showFailed(activity, info.label, message)
                    state = InstallUiState.Failed(info, message)
                    return@launch
                }
            }

            state = InstallUiState.Installing(tr("正在写入安装会话", "Writing the install session"), 0f)
            notifyProgress(info.label, Notifications.Stage.COPYING, tr("正在写入安装会话", "Writing the install session"), 0f)
            val sessionId = InstallEngine.install(
                context = activity,
                apk = info.file,
                packageName = info.packageName,
                allowDowngrade = true,
                safeMode = settings.safeMode,
                splits = info.splits
            ) { p ->
                state = InstallUiState.Installing(tr("正在复制安装包", "Copying the APK"), p)
                notifyProgress(info.label, Notifications.Stage.COPYING, tr("正在复制安装包", "Copying the APK"), p)
            }

            if (sessionId == -1) {
                stopProgress()
                val message = InstallEngine.Bus.outcome?.message ?: tr("创建安装会话失败", "Failed to create the install session")
                InstallerLog.record(activity, "INSTALL", "创建会话失败 ${info.packageName}: $message")
                if (settings.showNotification) Notifications.showFailed(activity, info.label, message)
                state = InstallUiState.Failed(info, message)
                return@launch
            }

            // commit 之后系统接管，进度不可观测（这个 ROM 只在开始时回报一次），
            // 统一交给「来回扫」的不确定进度条，避免卡在某个百分比不动
            state = InstallUiState.Installing(tr("正在安装，请稍候", "Installing, please wait"), null)
            notifyProgress(info.label, Notifications.Stage.INSTALLING, tr("正在安装，请稍候", "Installing, please wait"))

            var outcome = withTimeoutOrNull(180_000) {
                snapshotFlow { InstallEngine.Bus.outcome }.filterNotNull().first()
            }
            // 系统以「版本低于已安装版本」拒绝时，核心破解的 hook 可能被别的模块顶掉，
            // 用 root 直接 pm install -r -d 兜底（INSTALL_ALLOW_DOWNGRADE 最可靠）
            if (outcome != null && !outcome.success && outcome.downgradeBlocked &&
                CorePatchPrefs.activeInSystem
            ) {
                val rootOutcome = withContext(Dispatchers.IO) {
                    InstallEngine.installAsRoot(activity, info.file)
                }
                if (rootOutcome.success) outcome = outcome.copy(success = true, message = "Success")
            }
            if (outcome == null) {
                val message = tr("安装超时，系统没有返回结果", "Install timed out — the system returned no result")
                InstallerLog.record(activity, "INSTALL", "安装超时 ${info.packageName}")
                if (settings.showNotification) Notifications.showFailed(activity, info.label, message)
                state = InstallUiState.Failed(info, message)
                return@launch
            }
            if (!outcome.success) {
                InstallerLog.record(
                    activity, "INSTALL",
                    "安装失败 ${info.packageName} status=${outcome.statusCode} msg=${outcome.message}"
                )
                if (settings.showNotification) {
                    Notifications.showFailed(activity, info.label, outcome.message)
                }
                // 系统拦截了这次安装（ROM 的安装校验 / 开发者验证）：
                // 自动切到安全模式，去掉伪造安装者和测试包，再试一次
                if (!settings.safeMode && !safeRetryDone && isBlocked(outcome)) {
                    settings.safeMode = true
                    safeRetryDone = true
                    state = InstallUiState.Installing(
                        tr("系统拦截了安装，正在用安全模式重试", "The system blocked the install — retrying in safe mode"),
                        null
                    )
                    kotlinx.coroutines.delay(700)
                    startInstall()
                    return@launch
                }
                state = InstallUiState.Failed(info, outcome.message)
                return@launch
            }

            // 装成功了就留一条历史（存的是**当时这个 APK** 的信息）
            InstallHistory.record(activity, info)
            // 结果通知：实时活动样式下是胶囊原地翻成「完成 + 打开」，
            // 标准样式下就是一条普通通知，两者都带「打开」
            if (settings.showNotification) {
                Notifications.showInstalled(activity, info.label, info.packageName)
            }
            // 安装成功后：清掉暂存文件，再按设置决定是否删除源安装包
            stagedFile?.delete()
            val shouldDelete = settings.deleteApkAfterInstall
            if (shouldDelete && settings.confirmBeforeDelete) {
                deleteDialogVisible = true
                pendingDelete = true
                state = InstallUiState.Success(info, deleted = false, deleteFailed = false)
            } else if (shouldDelete) {
                val ok = withContext(Dispatchers.IO) {
                    SourceCleaner.delete(activity, sourceUri, null)
                }
                state = InstallUiState.Success(info, deleted = ok, deleteFailed = !ok)
            } else {
                state = InstallUiState.Success(info, deleted = false, deleteFailed = false)
            }
        }
    }

    /** 这一轮是否已经因为被拦截自动切过安全模式，避免来回重试。 */
    private var safeRetryDone = false

    /** 系统的失败结果算不算「拦截安装」。 */
    private fun isBlocked(outcome: InstallEngine.Outcome): Boolean {
        if (outcome.statusCode == android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED) return true
        val m = outcome.message.lowercase()
        return m.contains("blocked") || m.contains("拦截") ||
            m.contains("verification") || m.contains("developer")
    }

    /** 用户在「删除安装包」对话框里的选择。 */
    fun resolveDeleteDialog(delete: Boolean) {
        deleteDialogVisible = false
        val current = state as? InstallUiState.Success ?: return
        if (!delete) return
        deleteSource()
    }

    /** 结果页上手动删除安装包（设置里没开自动删除时用）。 */
    fun deleteSourceManually() {
        if (state !is InstallUiState.Success) return
        deleteSource()
    }

    /** 源文件还在不在 —— 结果页据此决定要不要显示「删除安装包」。 */
    fun hasSourceFile(): Boolean {
        val uri = sourceUri ?: return false
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it).exists() } ?: false
            "content" -> true
            else -> false
        }
    }

    private fun deleteSource() {
        val current = state as? InstallUiState.Success ?: return
        activity.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { SourceCleaner.delete(activity, sourceUri, null) }
            state = current.copy(deleted = ok, deleteFailed = !ok)
        }
    }

    // ---------------- 实时活动（进度通知） ----------------

    private var lastNotifyPercent = -1
    private var lastNotifyAt = 0L

    /**
     * 更新实时活动。百分比没变就跳过；变化频繁时按 250ms 节流，
     * 避免复制阶段每 1% 都走一次 NotificationManager。
     */
    private fun notifyProgress(
        label: String,
        stage: Notifications.Stage,
        text: String,
        fraction: Float? = null
    ) {
        // 「安装结果通知」关掉时，进度通知也一起停
        if (!settings.showNotification) return
        val now = android.os.SystemClock.elapsedRealtime()
        // 复制阶段按 250ms 节流，别每 1% 都推一次
        if (fraction != null && now - lastNotifyAt < 250) return
        lastNotifyAt = now
        Notifications.showInstall(
            context = activity,
            label = label,
            stage = stage,
            fraction = fraction,
            text = text,
            promoted = Notifications.canPromote(activity)
        )
    }

    private fun stopProgress() {
        lastNotifyPercent = -1
        Notifications.cancelProgress(activity)
    }

    /**
     * 这次安装是哪个应用发起的。
     * `launchedFromPackage` 是 API 34 才有的，低版本直接不显示（宁可不显示也不猜）。
     */
    private fun resolveSourceLabel(activity: android.app.Activity): String? {
        if (android.os.Build.VERSION.SDK_INT < 34) return null
        val pkg = runCatching { activity.launchedFromPackage }.getOrNull() ?: return null
        if (pkg.isBlank() || pkg == "android" || pkg == "com.android.shell") return null
        val pm = activity.packageManager
        return runCatching {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** 结构性校验：能不能当 zip 打开、有没有 manifest。 */
    private fun verify(file: File): Boolean = runCatching {
        if (!file.exists() || file.length() < 1024) return@runCatching false
        java.util.zip.ZipFile(file).use { zip ->
            zip.getEntry("AndroidManifest.xml") != null
        }
    }.getOrDefault(false)

    /**
     * 系统把别的应用创建的会话交过来确认时，由这里提交。
     *
     * 两种会话要分开处理：
     *  - 自己建的：`openSession` + `commit`，结果等 [InstallResultReceiver] 的广播；
     *  - 别人建的：`openSession` 必然抛 `Caller has no access to session`（PMS 只认创建者 uid），
     *    改成直接从会话暂存目录 `pm install`（见 [SessionInstaller]）。
     */
    fun confirmSession() {
        val confirm = state as? InstallUiState.SessionConfirm ?: return
        activity.lifecycleScope.launch {
            state = InstallUiState.Installing(tr("正在安装，请稍候", "Installing, please wait"), null)
            notifyProgress(confirm.label, Notifications.Stage.INSTALLING, tr("正在安装，请稍候", "Installing, please wait"))

            // 会话里的 APK 先读出来：既给结果页用，也是「接管不了」时的安装源
            val info = confirm.apk ?: withContext(Dispatchers.IO) {
                SessionInstaller.inspect(activity, confirm.sessionId)
            }
            InstallEngine.Bus.outcome = null

            val committed = withContext(Dispatchers.IO) { commitOwnSession(confirm) }
            val outcome: InstallEngine.Outcome? = if (committed) {
                withTimeoutOrNull(180_000) {
                    snapshotFlow { InstallEngine.Bus.outcome }.filterNotNull().first()
                }
            } else {
                if (info == null) {
                    stopProgress()
                    state = InstallUiState.Failed(
                        null,
                        tr(
                            "无法读取这个安装会话的安装包，请回到原应用重新发起安装",
                            "Can't read the APK of this install session — start the install again from the original app"
                        )
                    )
                    return@launch
                }
                // 接管不了别人的会话，但它暂存的 APK 能直接装（需要 ROOT）
                withContext(Dispatchers.IO) { SessionInstaller.install(confirm.sessionId) }
                    .also { if (!it.success) InstallerLog.record(activity, "SESSION", "外部会话安装失败 ${confirm.sessionId}: ${it.message}") }
            }

            if (outcome == null) {
                stopProgress()
                state = InstallUiState.Failed(
                    null,
                    tr("安装超时，系统没有返回结果", "Install timed out — the system returned no result")
                )
                return@launch
            }
            if (!outcome.success) {
                stopProgress()
                if (settings.showNotification) Notifications.showFailed(activity, confirm.label, outcome.message)
                state = InstallUiState.Failed(null, outcome.message)
                return@launch
            }

            stopProgress()
            if (settings.showNotification) {
                Notifications.showInstalled(activity, confirm.label, confirm.packageName)
            }
            // 结果页要 ApkInfo；暂存文件读不到时退一步用「已安装信息」拼一个
            val installed = info ?: withContext(Dispatchers.IO) { installedInfoOf(confirm.packageName) }
            if (installed != null) InstallHistory.record(activity, installed)
            state = if (installed != null) {
                InstallUiState.Success(installed, deleted = false, deleteFailed = false)
            } else {
                InstallUiState.Failed(
                    null,
                    tr("应用已安装，但读取不到应用信息", "The app is installed, but its info can't be read")
                )
            }
        }
    }

    /**
     * 提交「本应用创建的」安装会话。
     *
     * @return false 表示这个会话不归我们管（别的应用建的），要走暂存目录那条路。
     */
    private fun commitOwnSession(confirm: InstallUiState.SessionConfirm): Boolean = runCatching {
        val installer = activity.packageManager.packageInstaller
        val session = installer.openSession(confirm.sessionId)
        val intent = Intent(activity, com.panl.installer.core.InstallResultReceiver::class.java)
            .putExtra(InstallEngine.EXTRA_SESSION_ID, confirm.sessionId)
            .putExtra(InstallEngine.EXTRA_PACKAGE_NAME, confirm.packageName)
        val pending = android.app.PendingIntent.getBroadcast(
            activity,
            confirm.sessionId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )
        session.commit(pending.intentSender)
        session.close()
        true
    }.getOrElse {
        Log.i(TAG, "commitOwnSession(${confirm.sessionId}) failed: ${it.javaClass.simpleName} ${it.message}")
        InstallerLog.record(
            activity, "SESSION",
            "接管会话 ${confirm.sessionId} 失败（多半是别的应用创建的）: ${it.message}", it
        )
        false
    }

    /** 装完之后拿不到 APK 时，用系统里已安装的信息拼一个结果页要用的 [ApkInfo]。 */
    private fun installedInfoOf(packageName: String): ApkInfo? {
        val app = ApkInspector.readInstalled(activity, packageName) ?: return null
        return ApkInfo(
            file = File(app.apkPath.orEmpty()),
            packageName = app.packageName,
            label = app.label,
            versionName = app.versionName,
            versionCode = app.versionCode,
            minSdk = app.minSdk,
            targetSdk = app.targetSdk,
            sizeBytes = app.apkSize,
            icon = app.icon,
            permissions = emptyList(),
            signers = app.signers,
            installed = app,
            isDebuggable = false,
            isSystemApp = app.isSystem,
            installLocation = 0
        )
    }

    /** 失败后回到就绪态，让用户可以再点一次安装。 */
    fun retry() {
        val info = installedInfo ?: return
        InstallEngine.Bus.outcome = null
        state = InstallUiState.Ready(info)
    }

    fun openApp() {
        val info = installedInfo ?: return
        val launch = activity.packageManager.getLaunchIntentForPackage(info.packageName)
        if (launch != null) {
            runCatching { activity.startActivity(launch) }
        }
    }

    fun cleanupOnExit() {
        // 交给服务之后这份缓存还有用，删了服务就读不到了
        if (handedOff) return
        stagedFile?.delete()
        bundleDir?.deleteRecursively()
    }

    companion object {
        const val ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL"
    }
}
