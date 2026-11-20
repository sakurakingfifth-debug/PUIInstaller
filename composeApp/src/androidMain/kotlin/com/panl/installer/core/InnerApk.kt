package com.panl.installer.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 从普通压缩包里挖出 APK。
 *
 * 场景：有人把 apk 塞进 zip 里发过来（网盘分享、群文件），或者把「apk + 说明」打成一个包。
 * 这类文件以前会被判成「不是有效的安装包」，现在直接从 zip 里取出 APK 装。
 *
 * 多个 apk 时的取舍：
 *  1. 优先 `base.apk`（拆分包打成 zip 时主包通常叫这个）；
 *  2. 否则取**体积最大**的那个 —— 分片/语言包都很小，主包一定最大。
 *
 * 用 java.util.zip 而不是自写的 ZipLite：这里要的是**解压二进制**，
 * 而 ZipLite 只读中央目录和文本条目；deflate 是 java.util.zip 的原生能力，
 * Android 12+ 的实现（libziparchive）顺带支持 zstd，够用。
 */
object InnerApk {

    private const val TAG = "InnerApk"

    /** 取出来的 APK 放在 cache/inner_apk 下，由调用方负责清理。 */
    fun extract(context: Context, zip: File): File? {
        // 每次用独立子目录：批量里两个 zip 里都叫 base.apk 时不会互相覆盖，
        // 也避免「清空目录」把另一次正在用的文件删掉
        val root = File(context.cacheDir, "inner_apk").apply { mkdirs() }
        // 超过一天的直接清掉，别越堆越多
        val now = System.currentTimeMillis()
        root.listFiles()?.forEach { f ->
            if (now - f.lastModified() > 24 * 3600_000L) runCatching { f.deleteRecursively() }
        }
        val dir = File(root, System.currentTimeMillis().toString() + "-" + (zip.name.hashCode() and 0xffff))
            .apply { mkdirs() }

        return runCatching {
            java.util.zip.ZipFile(zip).use { zf ->
                val apks = zf.entries().toList()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", true) && it.size > 1024 }
                if (apks.isEmpty()) return@use null

                val picked = apks.firstOrNull { it.name.substringAfterLast('/').equals("base.apk", true) }
                    ?: apks.maxByOrNull { it.size }
                    ?: return@use null

                val out = File(dir, picked.name.substringAfterLast('/').ifBlank { "inner.apk" })
                zf.getInputStream(picked).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                Log.i(TAG, "extracted ${picked.name} (${picked.size} B) from ${zip.name}")
                out.takeIf { it.length() > 1024 }
            }
        }.getOrElse {
            Log.w(TAG, "extract failed for ${zip.name}: ${it.javaClass.simpleName} ${it.message}")
            null
        }
    }
}
