package com.panl.installer.core

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.ZipFile

/**
 * 拆分包（.apks / .xapk / .apkm）解包。
 *
 * 这三种本质都是 zip：
 *  - `.apks`  = bundletool 产物，`base.apk` + `split_config.*.apk` + `toc.pb`；
 *  - `.xapk`  = APKPure，`manifest.json` + 一堆 apk（可能还有 obb）；
 *  - `.apkm`  = APKMirror，`info.json` + apk（部分是加密的，加密的解不了）。
 *
 * 我们只做一件事：把里面的 apk 全解出来，认出哪个是 base，交给系统按多包会话装。
 * 思路参考 AntiSplit-M（它更进一步把分片合并成单 apk，这里不需要合并）。
 */
object BundleReader {

    private const val TAG = "BundleReader"

    private val EXTS = listOf(".apks", ".xapk", ".apkm")

    fun isBundle(name: String?): Boolean =
        name != null && EXTS.any { name.endsWith(it, ignoreCase = true) }

    data class Extracted(
        /** 主包（一定是解出来的 base.apk）。 */
        val base: File,
        /** 其余分片（split_config.xxx.apk 之类），不含 base。 */
        val splits: List<File>,
        /** 解包目录，装完要删。 */
        val dir: File
    )

    /**
     * @return 解包结果；不是合法拆分包（比如加密的 apkm）返回 null。
     */
    fun extract(context: Context, archive: File): Extracted? = runCatching {
        val dir = File(context.cacheDir, "bundle/${archive.nameWithoutExtension}").apply {
            deleteRecursively()
            mkdirs()
        }
        ZipFile(archive).use { zip ->
            val apks = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                // 别把 META-INF 里的签名文件当分片
                .filterNot { it.name.startsWith("META-INF/", ignoreCase = true) }
                .toList()

            if (apks.isEmpty()) {
                Log.w(TAG, "no apk inside ${archive.name}（可能是加密的 apkm）")
                return null
            }

            val files = apks.map { entry ->
                val name = entry.name.substringAfterLast('/')
                val out = File(dir, name)
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output, 512 * 1024) }
                }
                name to out
            }

            // base 优先按文件名认，认不出来就取最大的那个
            val base = files.firstOrNull { it.first.equals("base.apk", ignoreCase = true) }?.second
                ?: files.maxByOrNull { it.second.length() }?.second
                ?: return null
            val splits = files.map { it.second }.filter { it != base }
            Log.i(TAG, "extracted ${archive.name}: base=${base.name} splits=${splits.size}")
            Extracted(base = base, splits = splits, dir = dir)
        }
    }.getOrElse {
        Log.w(TAG, "extract failed: ${archive.name}", it)
        null
    }

    /** 解包目录占的字节数，用于进度加权。 */
    fun totalBytes(base: File, splits: List<File>): Long =
        (base.length() + splits.sumOf { it.length() }).coerceAtLeast(1L)
}
