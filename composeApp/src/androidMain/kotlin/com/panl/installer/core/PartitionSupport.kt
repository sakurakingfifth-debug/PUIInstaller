package com.panl.installer.core

import java.io.File

/**
 * 系统分区能不能直接改。
 *
 * Android 10 之后 system / system_ext / product / vendor 这些分区基本都是
 * **EROFS + dm-verity**：只读挂载，`mount -o rw,remount` 不生效，
 * 里面的 APK 文件在文件系统层面就删不掉。
 *
 * 这里读 `/proc/mounts` 判断（不需要 root，app 进程就能读）。
 */
object PartitionSupport {

    /** 算作「系统分区」的挂载点。 */
    private val SYSTEM_MOUNTS = setOf(
        "/system", "/system_ext", "/product", "/vendor", "/odm",
        "/my_product", "/my_heytap", "/my_stock", "/my_bigball"
    )

    /**
     * 系统分区是不是 EROFS（只读，删不掉文件）。
     *
     * 只要有一个系统分区是 erofs 就算 —— 混着 ext4 的机器极少，而且这种机器
     * 通常也开着 dm-verity，一样改不了。
     */
    fun isErofs(): Boolean = runCatching {
        File("/proc/mounts").useLines { lines ->
            lines.any { line ->
                val f = line.split(' ')
                f.size >= 3 && f[1] in SYSTEM_MOUNTS && f[2].equals("erofs", ignoreCase = true)
            }
        }
    }.getOrDefault(false)
}
