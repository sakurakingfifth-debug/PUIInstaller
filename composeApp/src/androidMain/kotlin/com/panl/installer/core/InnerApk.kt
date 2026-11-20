package com.panl.installer.core

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 从普通压缩包里挖出可以安装的文件。
 *
 * 场景：有人把 apk 塞进 zip 里发过来（网盘分享、群文件），或者把「apk + 说明」打成包，
 * 也可能是 apk 与 apks/xapk/apkm **混着放**。这类文件以前会被判成「不是有效的安装包」。
 *
 * 与 InstallController 的约定：
 *  - 只有一个候选：当普通安装包继续走确认页；
 *  - 多个候选：交给批量流程，逐个识别（apk / 拆分包 / 模块都认）。
 *
 * 用 java.util.zip 而不是自写的 ZipLite：这里要的是**解压二进制**，
 * ZipLite 只读中央目录和文本条目；deflate 是 java.util.zip 的原生能力，
 * Android 12+ 的实现（libziparchive）顺带支持 zstd。
 */
object InnerApk {

    private const val TAG = "InnerApk"

    /** 这些后缀都当成「可以装的东西」，可能是 apk，也可能是拆分包。 */
    private val CANDIDATE = listOf(".apk", ".apks", ".xapk", ".apkm")

    /**
     * 取出压缩包里**所有**可装的文件，顺序按它们在包里的出现顺序。
     *
     * 同名条目（不同目录下的 base.apk）加序号前缀区分，避免互相覆盖。
     */
    fun list(context: Context, zip: File): List<File> {
        val dir = freshDir(context, zip)
        return runCatching {
            java.util.zip.ZipFile(zip).use { zf ->
                val used = mutableSetOf<String>()
                zf.entries().toList()
                    .filter { e ->
                        !e.isDirectory && e.size > 1024 &&
                            CANDIDATE.any { e.name.endsWith(it, ignoreCase = true) }
                    }
                    .mapIndexedNotNull { i, e ->
                        var name = e.name.substringAfterLast('/').ifBlank { "inner-$i.apk" }
                        if (!used.add(name)) {
                            name = "$i-$name"
                            used.add(name)
                        }
                        val out = File(dir, name)
                        runCatching {
                            zf.getInputStream(e).use { input ->
                                out.outputStream().use { output -> input.copyTo(output) }
                            }
                            out.takeIf { it.length() > 1024 }
                        }.getOrNull()
                    }
            }
        }.getOrElse {
            Log.w(TAG, "list failed for ${zip.name}: ${it.javaClass.simpleName} ${it.message}")
            emptyList()
        }
    }

    /**
     * 只取一个（单个安装路径用）。
     *
     * 多个候选时的取舍：优先 `base.apk`，否则取体积最大的 —— 分片/语言包都很小，主包一定最大。
     */
    fun extract(context: Context, zip: File): File? {
        val all = list(context, zip)
        if (all.isEmpty()) return null
        return all.firstOrNull { it.name.equals("base.apk", ignoreCase = true) }
            ?: all.maxByOrNull { it.length() }
    }

    /** 每次解到独立子目录（批量里多个 zip 互不干扰），顺手清掉一天以上的旧目录。 */
    private fun freshDir(context: Context, zip: File): File {
        val root = File(context.cacheDir, "inner_apk").apply { mkdirs() }
        val now = System.currentTimeMillis()
        root.listFiles()?.forEach { f ->
            if (now - f.lastModified() > 24 * 3600_000L) runCatching { f.deleteRecursively() }
        }
        return File(root, now.toString() + "-" + (zip.name.hashCode() and 0xffff)).apply { mkdirs() }
    }
}
