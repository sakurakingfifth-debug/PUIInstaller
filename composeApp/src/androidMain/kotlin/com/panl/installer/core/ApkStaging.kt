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

    /** 暂存结果。带上失败原因，界面才能说清楚「为什么打不开」。 */
    sealed interface StageResult {
        /** @param copied 是否复制到了缓存（复制过的退出时要删，原文件不能删）。 */
        data class Ok(val file: File, val copied: Boolean) : StageResult
        data class Failed(val reason: Reason) : StageResult
    }

    enum class Reason {
        /** 路径上根本没有这个文件（被移动 / 重命名 / 删除）。 */
        NOT_FOUND,

        /** 文件在，但读不了（权限不足）。 */
        NO_PERMISSION,

        /** 读的过程中出错（空间不足、provider 异常之类）。 */
        READ_ERROR,

        /** 后缀是 .apk.1，但设置里关掉了。 */
        APK1_DISABLED
    }

    /** @return 可直接读取的 APK 文件；失败返回 null。 */
    fun stage(context: Context, uri: Uri, dirName: String = "staging"): File? =
        (stageEx(context, uri, dirName) as? StageResult.Ok)?.file

    fun stageEx(context: Context, uri: Uri, dirName: String = "staging"): StageResult {
        val dir = File(context.cacheDir, dirName).apply { mkdirs() }
        // 清掉超过 6 小时的残留，避免缓存目录越堆越大
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > 6 * 3600_000) it.delete() }

        if (uri.scheme == "file") {
            val path = uri.path ?: return StageResult.Failed(Reason.NOT_FOUND)
            val file = File(path)
            if (!file.exists()) return StageResult.Failed(Reason.NOT_FOUND)
            if (isApk1(file.name)) {
                if (!Settings.get(context).apk1Support) return StageResult.Failed(Reason.APK1_DISABLED)
                // 复制一份改成 .apk 的名字，别动用户原文件
                val copy = copyInto(file, dir, file.name.dropLast(2))
                    ?: return StageResult.Failed(Reason.READ_ERROR)
                return StageResult.Ok(copy, copied = true)
            }
            if (file.canRead()) return StageResult.Ok(file, copied = false)
            // canRead() 在个别 FUSE 场景会误判：直接读一次，能读就复制一份来用
            val copy = copyInto(file, dir, file.name)
                ?: return StageResult.Failed(Reason.NO_PERMISSION)
            return StageResult.Ok(copy, copied = true)
        }

        var name = displayName(context, uri)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "package.apk"
        if (isApk1(name)) {
            if (!Settings.get(context).apk1Support) return StageResult.Failed(Reason.APK1_DISABLED)
            name = name.dropLast(2)
        }
        val target = File(dir, name)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 512 * 1024) }
            } ?: return StageResult.Failed(Reason.NO_PERMISSION)
            StageResult.Ok(target, copied = true)
        }.getOrElse { StageResult.Failed(Reason.READ_ERROR) }
    }
}
