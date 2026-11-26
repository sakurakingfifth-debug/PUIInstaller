package com.panl.installer.core

import android.util.Log
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * 极简 tar.gz 打包（USTAR + gzip）。
 *
 * 为什么不引依赖：只需要「把一两个文本文件打进 tar.gz」这一件事，
 * commons-compress 有几百 KB；Android 自带的 toybox 也没有稳定的 tar 调用入口
 * （不同 ROM 的 toybox 编译开关不一样），所以自己写头。
 *
 * 只支持普通文件（typeflag '0'），不支持目录/软链/长文件名分段 —— 够用。
 */
object TarGz {

    private const val TAG = "TarGz"
    private const val BLOCK = 512

    /** 把 [entries] 依次写进 [out]；返回是否成功。 */
    fun write(out: File, entries: List<Pair<String, ByteArray>>): Boolean = runCatching {
        GZIPOutputStream(out.outputStream().buffered(), BLOCK * 8).use { gz ->
            entries.forEach { (name, data) ->
                gz.write(header(name, data.size.toLong()))
                gz.write(data)
                val pad = (BLOCK - data.size % BLOCK) % BLOCK
                if (pad > 0) gz.write(ByteArray(pad))
            }
            // 结束：两个 512 字节的全零块
            gz.write(ByteArray(BLOCK * 2))
        }
        true
    }.onFailure { Log.w(TAG, "tar.gz failed: ${out.name}", it) }.getOrDefault(false)

    private fun header(rawName: String, size: Long): ByteArray {
        val h = ByteArray(BLOCK)
        fun put(offset: Int, length: Int, s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            System.arraycopy(b, 0, h, offset, minOf(length, b.size))
        }

        // 名字超过 100 字节时截取尾部，保证一眼还能认出来
        val name = if (rawName.toByteArray(Charsets.UTF_8).size > 100) {
            rawName.takeLast(90)
        } else rawName

        put(0, 100, name)
        put(100, 8, "0000644\u0000")                                  // mode
        put(108, 8, "0000000\u0000")                                  // uid
        put(116, 8, "0000000\u0000")                                  // gid
        put(124, 12, String.format("%011o", size.coerceAtLeast(0L)) + "\u0000")
        put(136, 12, String.format("%011o", System.currentTimeMillis() / 1000) + "\u0000")
        put(148, 8, "        ")                                       // 校验和占位：8 个空格
        h[156] = '0'.code.toByte()                                    // 普通文件
        put(257, 6, "ustar\u0000")
        put(263, 2, "00")
        put(265, 32, "root")
        put(297, 32, "root")

        var sum = 0
        for (b in h) sum += b.toInt() and 0xff
        // 校验和：6 位八进制 + NUL + 空格
        put(148, 8, String.format("%06o", sum) + "\u0000 ")
        return h
    }
}
