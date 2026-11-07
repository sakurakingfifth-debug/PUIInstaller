package com.panl.installer.core

import android.content.Context
import android.util.Log
import java.io.File
import com.panl.installer.i18n.tr

/**
 * 「系统把别的应用创建的安装会话丢过来确认」的兜底安装。
 *
 * `PackageInstaller.openSession()` 只认会话创建者的 uid（PMS 里的 `isCallingUidOwner`），
 * 就算我们是默认安装器也接管不了，报错就是 `Caller has no access to session <id>`。
 *
 * 但会话的暂存目录 `/data/app/vmdl<id>.tmp/` 权限是 0775 / 文件 0644，
 * 父目录 `/data/app` 对其他 uid 可穿行 —— 里面的 `base`（以及 `split_*`）就是完整的 APK，
 * 不接管会话也能直接读。于是有 root 时用 `pm install` 装它，装完再把会话 abandon 掉，
 * 对用户来说就是一次正常的安装。
 */
object SessionInstaller {

    private const val TAG = "SessionInstaller"

    private fun stageDir(sessionId: Int) = File("/data/app/vmdl$sessionId.tmp")

    /** 暂存目录里的 APK 文件，`base` 排在最前面。读不到返回空列表。 */
    fun stagedApks(sessionId: Int): List<File> {
        val dir = stageDir(sessionId)
        val files = runCatching { dir.listFiles() }.getOrNull()
        if (files == null) {
            Log.w(TAG, "stagedApks($sessionId): can't list ${dir.absolutePath}")
            return emptyList()
        }
        val readable = files.filter { it.isFile && it.canRead() }.sortedBy { if (it.name == "base") 0 else 1 }
        Log.i(TAG, "stagedApks($sessionId): all=${files.joinToString { it.name }} readable=${readable.joinToString { it.name }}")
        return readable
    }

    /** 读暂存里的 APK 拿应用信息；读不到（权限不足 / 已被 abandon）返回 null。 */
    fun inspect(context: Context, sessionId: Int): ApkInfo? {
        val base = stagedApks(sessionId).firstOrNull() ?: return null
        return runCatching { ApkInspector.inspect(context, base) }
            .onFailure { Log.w(TAG, "inspect($sessionId) failed", it) }
            .getOrNull()
            ?.also { Log.i(TAG, "inspect($sessionId) -> ${it.packageName} ${it.label}") }
    }

    /**
     * 直接装暂存目录里的 APK（需要 root）。
     *
     * 单包走 `pm install`，有 split 走 `pm install-multiple`；
     * 装完顺手 `pm install-abandon`，把那个我们接管不了的会话清掉。
     */
    suspend fun install(sessionId: Int): InstallEngine.Outcome {
        val apks = stagedApks(sessionId)
        if (apks.isEmpty()) {
            Log.w(TAG, "install: no staged apk for session $sessionId")
            return InstallEngine.Outcome(false, null, "staged apk not readable", -1)
        }
        val paths = apks.joinToString(" ") { "'${it.absolutePath}'" }
        val command = if (apks.size == 1) {
            "pm install -r -d $paths"
        } else {
            "pm install-multiple -r -d $paths"
        }
        val (ok, out) = RootShell.run(command)
        val success = ok && out.contains("Success")
        if (success) RootShell.run("pm install-abandon $sessionId")
        Log.i(TAG, "install session=$sessionId splits=${apks.size} ok=$ok success=$success out=${out.take(160)}")
        return InstallEngine.Outcome(
            success = success,
            packageName = null,
            // 失败时给一句人话，原始输出留在日志里（大多是没有 ROOT 授权）
            message = if (success) "Success" else tr(
                "这个安装会话由其他应用创建，需要 ROOT 才能继续安装",
                "This install session belongs to another app — ROOT is required to continue"
            ),
            statusCode = if (success) 0 else -1
        )
    }
}
