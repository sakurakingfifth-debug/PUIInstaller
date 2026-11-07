package com.panl.installer.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 安装 / 卸载的执行层。
 *
 * 系统安装器的身份（INSTALL_PACKAGES）让我们可以走 [PackageInstaller] 的静默路径：
 * 会话 commit 之后系统直接安装，不再弹「是否安装」二次确认。
 * 没有该权限时系统会把确认界面通过 [PackageInstaller.STATUS_PENDING_USER_ACTION] 抛回来，
 * 这里原样转交给系统处理，避免死循环。
 */
object InstallEngine {

    const val EXTRA_SESSION_ID = "com.panl.installer.extra.SESSION_ID"
    const val EXTRA_PACKAGE_NAME = "com.panl.installer.extra.PACKAGE_NAME"
    const val EXTRA_STATUS = "com.panl.installer.extra.STATUS"
    const val EXTRA_MESSAGE = "com.panl.installer.extra.MESSAGE"

    private const val TAG = "InstallEngine"

    /** 一次安装的结果，接收器写入、界面读取。 */
    data class Outcome(
        val success: Boolean,
        val packageName: String?,
        val message: String,
        val statusCode: Int,
        /** 系统是因为「版本低于已安装版本」拒的，可以走 root 兜底。 */
        val downgradeBlocked: Boolean = false,
        val at: Long = System.currentTimeMillis()
    )

    /** 跨「Activity ↔ BroadcastReceiver」的结果总线：同一个进程，直接状态共享即可。 */
    object Bus {
        var outcome by mutableStateOf<Outcome?>(null)
        var sessionId by mutableStateOf(-1)
    }

    /** 把 [apk] 写进一个安装会话并 commit。
     *
     * @param onProgress 0..1 的写入进度；commit 之后由接收器回调最终结果。
     * @return 会话 id；失败返回 -1，并把错误写进 [Bus.outcome]。
     */
    suspend fun install(
        context: Context,
        apk: File,
        packageName: String,
        allowDowngrade: Boolean,
        safeMode: Boolean = false,
        onProgress: (Float) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(packageName)
                setSize(apk.length())

                // 注册应用商店（RAS）的「一键安装」：告诉系统这次会话不需要用户操作。
                // 普通应用调用会被系统忽略 / 拒绝，包一层保护。
                runCatching {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }

                // 降级安装走 @SystemApi 的 setRequestDowngrade，公开 SDK 里没有，只能反射。
                // 被隐藏 API 策略拦下时静默跳过，最终由系统返回 INSTALL_FAILED_VERSION_DOWNGRADE。
                if (allowDowngrade) {
                    runCatching {
                        val m = javaClass.getMethod(
                            "setRequestDowngrade",
                            Boolean::class.javaPrimitiveType
                        )
                        m.invoke(this, true)
                    }
                }

                // ---- 安全模式 ----
                // 关：伪造安装者 + 测试包 + 低 targetSdk 绕过，尽量让系统把这次安装当成商店来源；
                // 开：老老实实用自己当安装者，只保留安装能力。
                if (safeMode) {
                    runCatching { setInstallerPackageName(context.packageName) }
                    if (Build.VERSION.SDK_INT >= 33) {
                        runCatching { setPackageSource(PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE) }
                    }
                } else {
                    runCatching { setInstallerPackageName(VerificationBypass.forgedInstaller(context)) }
                    if (Build.VERSION.SDK_INT >= 33) {
                        runCatching { setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE) }
                    }
                    // INSTALL_ALLOW_TEST
                    VerificationBypass.addInstallFlag(this, 0x00000004)
                    if (Build.VERSION.SDK_INT >= 34) {
                        // INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK
                        VerificationBypass.addInstallFlag(this, 0x00080000)
                    }
                }
            }

            // 关掉本 UID 的安装校验（Play 保护 / 校验代理）；安全模式下不做这件事
            if (!safeMode) VerificationBypass.disableForUid(context)

            sessionId = installer.createSession(params)
            Bus.sessionId = sessionId

            installer.openSession(sessionId).use { session ->
                val total = apk.length().coerceAtLeast(1L)
                var copied = 0L
                var lastTick = 0f

                session.openWrite("base.apk", 0, total).use { out ->
                    apk.inputStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            copied += read
                            val p = (copied.toFloat() / total).coerceIn(0f, 1f)
                            // 每 1% 才回调一次，避免高频重组
                            if (p - lastTick >= 0.01f || p >= 1f) {
                                lastTick = p
                                onProgress(p)
                            }
                        }
                    }
                    session.fsync(out)
                }

                val resultIntent = Intent(context, InstallResultReceiver::class.java).apply {
                    putExtra(EXTRA_SESSION_ID, sessionId)
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                }
                val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_MUTABLE
                val pending = android.app.PendingIntent.getBroadcast(
                    context, sessionId, resultIntent, flags
                )
                // 跳过「开发者验证」（Android 16+）
                if (!safeMode) VerificationBypass.addDeveloperExperiment(context, packageName)

                session.commit(pending.intentSender)
            }
            sessionId
        } catch (t: Throwable) {
            Log.w(TAG, "install failed", t)
            Bus.outcome = Outcome(
                success = false,
                packageName = packageName,
                message = t.message ?: t.javaClass.simpleName,
                statusCode = -1
            )
            if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
            -1
        }
    }


    /** 静默卸载。需要 DELETE_PACKAGES（系统安装器身份）。 */    fun uninstall(context: Context, packageName: String) {
        try {
            val intent = Intent(context, UninstallResultReceiver::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_MUTABLE
            val pending = android.app.PendingIntent.getBroadcast(
                context, packageName.hashCode(), intent, flags
            )
            context.packageManager.packageInstaller.uninstall(packageName, pending.intentSender)
        } catch (t: Throwable) {
            Log.w(TAG, "uninstall failed", t)
            UninstallBus.outcome = Outcome(
                success = false,
                packageName = packageName,
                message = t.message ?: t.javaClass.simpleName,
                statusCode = -1
            )
        }
    }

    /**
     * 系统拒绝降级时的兜底：用 root 直接 `pm install -r -d`。
     *
     * 核心破解的 checkDowngrade hook 在某些 ROM 上会被别的模块（例如 LuckyTool）
     * 的替换式 hook 顶掉，这时 `INSTALL_ALLOW_DOWNGRADE` 反而最可靠。
     */
    suspend fun installAsRoot(context: Context, apk: File): Outcome {
        // system_server 读不了 /data/user/0/<app>/cache 和 /sdcard，先落到 /data/local/tmp
        val tmp = "/data/local/tmp/pui_root_install.apk"
        val (copied, copyOut) = RootShell.run("cp '${apk.absolutePath}' $tmp && chmod 644 $tmp")
        if (!copied) {
            Log.w(TAG, "installAsRoot: copy failed ${copyOut.take(120)}")
            return Outcome(false, null, copyOut.ifBlank { "copy failed" }, -1)
        }
        val (ok, out) = RootShell.run("pm install -r -d $tmp")
        RootShell.run("rm -f $tmp")
        val success = ok && out.contains("Success")
        Log.i(TAG, "installAsRoot: ok=$ok success=$success out=${out.take(120)}")
        return Outcome(
            success = success,
            packageName = null,
            message = if (success) "Success" else out.ifBlank { "root install failed" },
            statusCode = if (success) 0 else -1
        )
    }

    object UninstallBus {
        var outcome by mutableStateOf<Outcome?>(null)
    }
}

/**
 * 安装包清理：安装成功后按设置删除源文件。
 *
 * 源的来路决定了难度，逐条试：
 *  1. `file://`：有全盘权限才删得掉；
 *  2. 文件管理器 / 下载器给的 `content://`：有的 provider 实现 delete，有的只认 SAF 的 deleteDocument；
 *  3. 聊天软件（Telegram 等）给的 `content://`：走 FileProvider，**根本不实现 delete**，
 *     文件真实位置在 `Android/data/<包名>/files/...`，连 MANAGE_EXTERNAL_STORAGE 都进不去；
 *  4. 云盘：没有本地文件，删不掉也正常。
 *
 * 第 3 种是用户报的「Telegram 装完没删安装包」。办法是把 fd 反查成真实路径
 * （FileProvider 的 fd 一定指向原始文件），再用 **已授权的 ROOT** 删 —— 这是唯一可行的手段。
 * 删不掉只影响体验，不抛异常、不影响安装结果。
 */
object SourceCleaner {

    private const val TAG = "SourceCleaner"

    suspend fun delete(context: Context, uri: Uri?, staged: File?): Boolean {
        var ok = false
        staged?.let { ok = it.delete() || ok }
        if (uri == null) return ok

        when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path
                ok = deletePath(path) || ok
                if (!ok) ok = deleteViaRoot(path) || ok
            }

            "content" -> {
                // 真实路径先算出来：删成功之后 provider 里的条目就没了，再查只会拿到 null。
                // 两条都算只为日志，出问题能一眼看出卡在哪一步。
                val dataPath = resolvePath(context, uri)
                val fdPath = pathFromFd(context, uri)
                val real = dataPath ?: fdPath

                // 1. 有写权限时直接删
                ok = runCatching { context.contentResolver.delete(uri, null, null) > 0 }
                    .getOrDefault(false) || ok
                // 2. SAF 文档
                if (!ok) {
                    ok = runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                        .getOrDefault(false) || ok
                }
                // 3. 真实路径直接删
                if (!ok) ok = deletePath(real) || ok
                // 4. ROOT 兜底：聊天软件的缓存在别的应用私有目录里，普通权限永远删不掉
                if (!ok) ok = deleteViaRoot(real) || ok
                Log.i(TAG, "content uri=$uri data=$dataPath fd=$fdPath deleted=$ok")
                if (!ok) {
                    InstallerLog.record(
                        context, "CLEAN",
                        "删除安装包失败 uri=$uri data=$dataPath fd=$fdPath"
                    )
                }
            }
        }
        return ok
    }

    private fun deletePath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        // exists() 在「别的应用的 Android/data」里恒为 false，所以这里只能当快速通道
        if (!file.exists()) return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    /**
     * 从 fd 反查真实路径 —— 专治 FileProvider。
     *
     * FileProvider 的做法是「在自己进程里打开真实文件，把 fd 递过来」，
     * 所以 `/proc/self/fd/<n>` 这个符号链接就指向原始文件。
     * 必须用 readlink 而不是 File.canonicalPath：后者要逐级 stat，
     * 遇到别的应用的 `Android/data` 会 EACCES，退化成 `/proc/self/fd/12` 这种废值。
     */
    private fun pathFromFd(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val link = "/proc/self/fd/${pfd.fd}"
            // readlink 只需要 fd 本身，不需要对目标文件有任何权限
            val real = android.system.Os.readlink(link)
            // 管道 / socket 会返回 "pipe:[123]"，不是路径
            real?.takeIf { it.startsWith("/") && it.endsWith(".apk", ignoreCase = true) }
                ?.let(::normalizeMount)
        }
    }.getOrElse {
        Log.i(TAG, "fd lookup failed for $uri: ${it.javaClass.simpleName} ${it.message}")
        null
    }

    /**
     * fd 反查出来的是挂载点视角的路径（`/mnt/user/0/emulated/0/...`、`/mnt/runtime/default/...`），
     * 换成用户视角的 `/storage/emulated/0/...` —— 同一个挂载点，只是 su 和日志里看着顺眼。
     */
    private fun normalizeMount(path: String): String {
        val prefixes = listOf(
            "/mnt/user/0/emulated/0/",
            "/mnt/runtime/default/emulated/0/",
            "/mnt/runtime/read/emulated/0/",
            "/mnt/runtime/write/emulated/0/"
        )
        prefixes.firstOrNull { path.startsWith(it) }?.let {
            return "/storage/emulated/0/" + path.removePrefix(it)
        }
        return path
    }

    /**
     * 用 ROOT 删一个真实路径。
     *
     * 只对 `.apk` 生效 —— 这条路径是从 provider 反查出来的，多加一道保险，
     * 免得哪天 provider 给个奇怪的 fd 就把别的东西删了。
     * 删除结果用 su 自己确认：应用侧 stat 不到别人的目录，`File.exists()` 会骗人。
     */
    private suspend fun deleteViaRoot(path: String?): Boolean {
        if (path.isNullOrBlank() || !path.endsWith(".apk", ignoreCase = true)) return false
        val quoted = "'" + path.replace("'", "'\\''") + "'"
        val command = "rm -f $quoted; if [ -e $quoted ]; then echo STILL; else echo GONE; fi"
        val (code, out) = kotlinx.coroutines.withTimeoutOrNull(20_000) {
            RootShell.run(command)
        } ?: (false to "timeout")
        val gone = out.contains("GONE")
        Log.i(TAG, "root delete $path -> code=$code gone=$gone out=${out.take(120)}")
        return gone
    }

    /** content:// → 真实路径。只有拿到 MANAGE_EXTERNAL_STORAGE 才尝试，否则一定失败。 */
    private fun resolvePath(context: Context, uri: Uri): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (!android.os.Environment.isExternalStorageManager()) return null
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.MediaColumns.DATA),
                null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }
}
