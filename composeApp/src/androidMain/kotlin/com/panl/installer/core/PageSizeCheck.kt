package com.panl.installer.core

import java.io.File
import java.util.zip.ZipFile

/**
 * 16 KB 页大小兼容性检测。
 *
 * Android 15 起部分设备把内核页大小改成 16 KB，native 库的 ELF 段必须按 16 KB 对齐
 * （`p_align >= 0x4000`），否则装得上但一加载就崩。这里直接读 APK 里每个 `.so` 的
 * program header 判断，不依赖设备。
 *
 * 返回：
 *  - true：有 native 库，且全部按 16 KB 对齐
 *  - false：有 native 库，存在 4 KB 对齐的
 *  - null：没有 native 库（纯 Java/Kotlin），不做判断
 */
object PageSizeCheck {

    private const val PT_LOAD = 1
    private const val ALIGN_16K = 0x4000L

    fun detect(apk: File): Boolean? {
        return runCatching {
            ZipFile(apk).use { zip ->
                val libs = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".so") && it.name.startsWith("lib/") }
                    .toList()
                if (libs.isEmpty()) return@use null

                var allAligned = true
                for (entry in libs) {
                    val aligned = zip.getInputStream(entry).use { input ->
                        isAligned16K(readUpTo(input, 64 * 1024))
                    }
                    if (aligned != true) {
                        allAligned = false
                        break
                    }
                }
                allAligned
            }
        }.getOrNull()
    }

    /**
     * 只看 ELF 头 + program header。为了能顺序读，最多取 64 KB；
     * 真正的 program header 通常在前几百字节内。
     */
    private fun isAligned16K(head: ByteArray): Boolean? {
        if (head.size < 52) return null
        if (head[0] != 0x7F.toByte() || head[1] != 'E'.code.toByte() ||
            head[2] != 'L'.code.toByte() || head[3] != 'F'.code.toByte()
        ) {
            return null
        }

        val is64 = head[4].toInt() == 2
        val littleEndian = head[5].toInt() == 1
        if (!littleEndian) return null

        val phoff: Long
        val phentsize: Int
        val phnum: Int
        if (is64) {
            phoff = readLong(head, 0x20)
            phentsize = readShort(head, 0x36)
            phnum = readShort(head, 0x38)
        } else {
            phoff = readInt(head, 0x1C).toLong() and 0xFFFFFFFFL
            phentsize = readShort(head, 0x2A)
            phnum = readShort(head, 0x2C)
        }
        if (phoff <= 0 || phentsize <= 0 || phnum <= 0) return null

        var anyLoad = false
        for (i in 0 until phnum) {
            val base = phoff + i.toLong() * phentsize
            if (base + phentsize > head.size) break
            val offset = base.toInt()
            val type = readInt(head, offset)
            if (type != PT_LOAD) continue
            anyLoad = true
            val align = if (is64) readLong(head, offset + 0x30) else readInt(head, offset + 0x1C).toLong() and 0xFFFFFFFFL
            if (align < ALIGN_16K) return false
        }
        return if (anyLoad) true else null
    }

    private fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val n = input.read(buffer, read, limit - read)
            if (n <= 0) break
            read += n
        }
        return if (read == limit) buffer else buffer.copyOf(read)
    }

    private fun readShort(b: ByteArray, at: Int): Int {
        if (at + 2 > b.size) return -1
        return (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    }

    private fun readInt(b: ByteArray, at: Int): Int {
        if (at + 4 > b.size) return -1
        return (b[at].toInt() and 0xFF) or
            ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or
            ((b[at + 3].toInt() and 0xFF) shl 24)
    }

    private fun readLong(b: ByteArray, at: Int): Long {
        if (at + 8 > b.size) return -1
        var value = 0L
        for (i in 7 downTo 0) {
            value = (value shl 8) or (b[at + i].toLong() and 0xFF)
        }
        return value
    }
}
