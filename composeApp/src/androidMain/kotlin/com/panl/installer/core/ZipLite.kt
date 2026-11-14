package com.panl.installer.core

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.Inflater

/**
 * 极简 zip 目录读取器。
 *
 * 为什么不用 `java.util.zip.ZipFile`：新版 LSPosed / Vector 的模块包把大部分条目压成
 * **zstd（compression method 95）**，Java 的 ZipFile 在**打开档案**那一步就直接抛
 * `ZipException: invalid CEN header (bad compression method: 95)`，连目录都列不出来。
 * 而 KernelSU 的 ksud（Rust zip 库）解得开，包本身完全可用。
 *
 * 所以这里只做两件事：
 *  1. 自己扫 EOCD + 中央目录，拿到条目名（判断「是不是模块包」只要名字）；
 *  2. 对 store / deflate 的条目按本地头读出内容；zstd 的读不了，返回 null。
 *
 * 不支持 ZIP64（模块包不会那么大），遇到就走 null。
 */
internal object ZipLite {

    data class Entry(
        val name: String,
        val method: Int,
        val compressedSize: Long,
        val size: Long,
        val localHeaderOffset: Long
    ) {
        /** 只有这两种我们能自己解。 */
        val readable: Boolean get() = method == 0 || method == 8
    }

    private const val EOCD_SIG = 0x06054b50L
    private const val CEN_SIG = 0x02014b50L
    private const val LOC_SIG = 0x04034b50L
    private const val ZIP64_MARK = 0xFFFFFFFFL

    fun entries(file: File): List<Entry>? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (length < 22) return null
            // EOCD 在文件末尾，最多 64KB 注释
            val tailLen = minOf(length, 66_000L)
            val tail = ByteArray(tailLen.toInt())
            raf.seek(length - tailLen)
            raf.readFully(tail)

            var eocd = -1
            for (i in tail.size - 22 downTo 0) {
                if (u32(tail, i) == EOCD_SIG) {
                    eocd = i
                    break
                }
            }
            if (eocd < 0) return null

            val count = u16(tail, eocd + 10)
            val cdSize = u32(tail, eocd + 12)
            val cdOffset = u32(tail, eocd + 16)
            if (cdSize == ZIP64_MARK || cdOffset == ZIP64_MARK) return null
            if (cdOffset + cdSize > length) return null

            val cd = ByteArray(cdSize.toInt())
            raf.seek(cdOffset)
            raf.readFully(cd)

            val result = ArrayList<Entry>(count)
            var p = 0
            while (p + 46 <= cd.size) {
                if (u32(cd, p) != CEN_SIG) break
                val method = u16(cd, p + 10)
                val compSize = u32(cd, p + 20)
                val size = u32(cd, p + 24)
                val nameLen = u16(cd, p + 28)
                val extraLen = u16(cd, p + 30)
                val commentLen = u16(cd, p + 32)
                val localOffset = u32(cd, p + 42)
                if (p + 46 + nameLen > cd.size) break
                val name = String(cd, p + 46, nameLen, Charsets.UTF_8)
                if (compSize != ZIP64_MARK && localOffset != ZIP64_MARK) {
                    result += Entry(name, method, compSize, size, localOffset)
                }
                p += 46 + nameLen + extraLen + commentLen
            }
            result
        }
    }.getOrNull()

    /** 读条目内容（仅 store / deflate）。zstd 条目返回 null。 */
    fun read(file: File, entry: Entry): String? = runCatching {
        if (!entry.readable) return null
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(entry.localHeaderOffset)
            val head = ByteArray(30)
            raf.readFully(head)
            if (u32(head, 0) != LOC_SIG) return null
            val nameLen = u16(head, 26)
            val extraLen = u16(head, 28)
            raf.seek(entry.localHeaderOffset + 30 + nameLen + extraLen)

            val compressed = ByteArray(entry.compressedSize.toInt())
            raf.readFully(compressed)
            val raw = if (entry.method == 0) {
                compressed
            } else {
                val inflater = Inflater(true)
                inflater.setInput(compressed)
                val out = ByteArray(entry.size.toInt().coerceAtLeast(64))
                val n = inflater.inflate(out)
                inflater.end()
                out.copyOf(n)
            }
            String(raw, Charsets.UTF_8)
        }
    }.getOrNull()

    private fun u16(b: ByteArray, i: Int): Int =
        (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, i: Int): Long =
        (b[i].toLong() and 0xFF) or
            ((b[i + 1].toLong() and 0xFF) shl 8) or
            ((b[i + 2].toLong() and 0xFF) shl 16) or
            ((b[i + 3].toLong() and 0xFF) shl 24)
}
