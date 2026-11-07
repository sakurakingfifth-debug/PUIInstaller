package com.panl.installer.ui.uninstall

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import com.panl.installer.core.ApkInspector
import com.panl.installer.core.InstallEngine
import com.panl.installer.core.InstalledApp
import com.panl.installer.core.PackageUtils
import com.panl.installer.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.panl.installer.i18n.tr

sealed interface UninstallUiState {
    data object Loading : UninstallUiState
    data class Ready(val app: InstalledApp) : UninstallUiState
    data class Error(val title: String, val message: String) : UninstallUiState
    data class Working(val app: InstalledApp) : UninstallUiState
    data class Done(val app: InstalledApp) : UninstallUiState

    /** 更新副本已经被 ROOT 删掉，重启后系统才会回退出厂版本。 */
    data class NeedsReboot(val app: InstalledApp) : UninstallUiState
    data class Failed(val app: InstalledApp?, val message: String) : UninstallUiState
}

class UninstallController(private val activity: ComponentActivity) {

    var state by mutableStateOf<UninstallUiState>(UninstallUiState.Loading)
        private set

    var confirmVisible by mutableStateOf(false)
        private set

    /** 系统应用没有更新可卸时，弹一句说明。 */
    var noUpdatesVisible by mutableStateOf(false)
        private set

    /** 目标是安装器自己：系统硬保护，连 ROOT 都拒。 */
    var selfBlocked by mutableStateOf(false)
        private set

    fun handleIntent(intent: Intent?) {
        val pkg = intent?.getStringExtra("com.panl.installer.extra.PACKAGE")
            ?: intent?.data?.schemeSpecificPart
        if (pkg.isNullOrBlank()) {
            state = UninstallUiState.Error(tr("没有拿到包名", "No package name received"), tr("请从应用列表或设置里重新进入", "Open it again from the app list or Settings"))
            return
        }
        load(pkg)
    }

    private fun load(packageName: String) {
        activity.lifecycleScope.launch {
            state = UninstallUiState.Loading
            val app = withContext(Dispatchers.IO) {
                ApkInspector.readInstalled(activity, packageName)
            }
            state = if (app == null) {
                UninstallUiState.Error(tr("应用未安装", "App not installed"), tr("$packageName 不在这台设备上", "$packageName isn't on this device"))
            } else {
                UninstallUiState.Ready(app)
            }
        }
    }

    fun requestUninstall() {
        val app = (state as? UninstallUiState.Ready)?.app ?: return
        // 安装器自己被系统标成「必需组件」，PMS 直接拒（日志：Attempted to delete
        // system required package，callingUid 是 0 也拒），所以别白试一次再报内部错误
        if (app.isSystem && app.packageName == activity.packageName) {
            selfBlocked = true
            return
        }
        // 系统应用的本体删不掉（除非 ROOT 强删），只能把更新摘掉回到出厂版本。
        // 没有更新就没什么可卸的，先说清楚，别让用户白点一次。
        if (app.isSystem && !app.hasUpdates) {
            noUpdatesVisible = true
            return
        }
        confirmVisible = true
    }

    fun dismissConfirm() {
        confirmVisible = false
    }

    fun dismissNoUpdates() {
        noUpdatesVisible = false
    }

    fun dismissSelfBlocked() {
        selfBlocked = false
    }

    /**
     * 系统接口被拒后的兜底：ROOT 直接删 /data/app 里的更新副本。
     *
     * 只对「必需组件」这种硬拒的包用；删完当前进程还能跑（APK 是 mmap 的），
     * 但重启后才会真正回退到出厂版本。
     */
    fun forceRemoveSelfUpdates() {
        selfBlocked = false
        val app = (state as? UninstallUiState.Ready)?.app ?: return
        state = UninstallUiState.Working(app)
        activity.lifecycleScope.launch {
            val ok = RootShell.removeUpdateDir(activity, app.packageName)
            state = if (ok) {
                UninstallUiState.NeedsReboot(app)
            } else {
                UninstallUiState.Failed(
                    app,
                    tr(
                        "ROOT 删除失败：没能删掉更新文件，检查一下 ROOT 授权",
                        "Root delete failed — check that root access is granted"
                    )
                )
            }
        }
    }

    fun rebootNow() {
        activity.lifecycleScope.launch { RootShell.reboot() }
    }

    fun uninstall() {
        confirmVisible = false
        val app = (state as? UninstallUiState.Ready)?.app ?: return
        if (!PackageUtils.hasDeletePermission(activity)) {
            state = UninstallUiState.Failed(app, tr("缺少 DELETE_PACKAGES 权限，无法静默卸载", "Missing DELETE_PACKAGES — can't uninstall silently"))
            return
        }
        InstallEngine.UninstallBus.outcome = null
        activity.lifecycleScope.launch {
            state = UninstallUiState.Working(app)
            // 同一个 uninstall 调用：系统应用会走「移除更新、回退到出厂版本」，
            // 普通应用才是真的删包（数据一并清掉）。
            InstallEngine.uninstall(activity, app.packageName)

            val outcome = withTimeoutOrNull(90_000) {
                snapshotFlow { InstallEngine.UninstallBus.outcome }.filterNotNull().first()
            }
            state = when {
                outcome == null -> UninstallUiState.Failed(app, tr("卸载超时，系统没有返回结果", "Uninstall timed out — the system returned no result"))
                outcome.success -> UninstallUiState.Done(app)
                else -> UninstallUiState.Failed(app, outcome.message)
            }
        }
    }

    /** 打开系统的「应用信息」页（权限、存储、通知都在那儿）。 */
    fun openSystemAppInfo() {
        val pkg = (state as? UninstallUiState.Ready)?.app?.packageName ?: return
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", pkg, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { activity.startActivity(intent) }
    }

    fun openApp() {
        val pkg = (state as? UninstallUiState.Ready)?.app?.packageName ?: return
        activity.packageManager.getLaunchIntentForPackage(pkg)?.let {
            runCatching { activity.startActivity(it) }
        }
    }
}
