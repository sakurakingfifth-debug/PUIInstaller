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

    /** @return 可直接读取的 APK 文件；失败返回 null。 */
    fun stage(context: Context, uri: Uri, dirName: String = "staging"): File? {
        val dir = File(context.cacheDir, dirName).apply { mkdirs() }
        // 清掉超过 6 小时的残留，避免缓存目录越堆越大
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > 6 * 3600_000) it.delete() }

        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return File(path).takeIf { it.exists() && it.canRead() }
        }

        val name = displayName(context, uri)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "package.apk"
        val target = File(dir, name)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 512 * 1024) }
            } ?: return null
            target
        }.getOrNull()
    }
}
