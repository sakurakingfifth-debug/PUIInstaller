package com.panl.installer.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * 把外部 Uri 落到应用缓存里。
 *
 * 两个原因必须落地：
 *  1. `PackageManager.getPackageArchiveInfo` 只吃文件路径；
 *  2. 写安装会话需要把整包读一遍，content:// 不保证能重复打开。
 */
object ApkStaging {

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /**
     * QQ / 微信 的「.apk.1」。
     *
     * 关掉开关时直接当非法文件；开着就按普通 APK 走，落到缓存时顺手把名字改回 `.apk`
     * （下游有按 `.apk` 结尾判断的地方，比如删源文件的路径检查）。
     */
    private fun isApk1(name: String?): Boolean =
        name != null && name.endsWith(".apk.1", ignoreCase = true)

    private fun copyInto(src: File, dir: File, name: String): File? = runCatching {
        val target = File(dir, name)
        src.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, 512 * 1024) }
        }
        target
    }.getOrNull()

    /** @return 可直接读取的 APK 文件；失败返回 null。 */
    fun stage(context: Context, uri: Uri, dirName: String = "staging"): File? {
        val dir = File(context.cacheDir, dirName).apply { mkdirs() }
        // 清掉超过 6 小时的残留，避免缓存目录越堆越大
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > 6 * 3600_000) it.delete() }

        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path).takeIf { it.exists() && it.canRead() } ?: return null
            if (isApk1(file.name)) {
                if (!Settings.get(context).apk1Support) return null
                // 复制一份改成 .apk 的名字，别动用户原文件
                return copyInto(file, dir, file.name.dropLast(2))
            }
            return file
        }

        var name = displayName(context, uri)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "package.apk"
        if (isApk1(name)) {
            if (!Settings.get(context).apk1Support) return null
            name = name.dropLast(2)
        }
        val target = File(dir, name)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 512 * 1024) }
            } ?: return null
            target
        }.getOrNull()
    }
}
