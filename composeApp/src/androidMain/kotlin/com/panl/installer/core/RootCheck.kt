package com.panl.installer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** ROOT 检测结果。 */
enum class RootState {
    /** 还没检测完。 */
    Checking,

    /** 能拿到 uid=0。 */
    Granted,

    /** 有 su，但这次没拿到授权（拒绝 / 超时）。 */
    Denied,

    /** 系统里根本找不到 su。 */
    NoSu
}

/**
 * ROOT 检测。
 *
 * 只做两件事：找 su 二进制、试着执行一次 `su -c id`。
 * 后者会触发 Magisk / KernelSU 的授权弹窗，所以放在 IO 线程并带超时。
 */
object RootCheck {

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/magisk/su",
        "/debug_ramdisk/su",
        "/data/local/bin/su",
        "/data/local/xbin/su"
    )

    fun hasSuBinary(): Boolean {
        if (SU_PATHS.any { File(it).exists() }) return true
        // PATH 里也可能有（部分 ROM 装在别处）
        return runCatching {
            val p = ProcessBuilder("which", "su").redirectErrorStream(true).start()
            p.waitFor() == 0 && p.inputStream.bufferedReader().readText().isNotBlank()
        }.getOrDefault(false)
    }

    suspend fun detect(): RootState = withContext(Dispatchers.IO) {
        // 先直接试 su：KernelSU / Magisk 的隐藏模式下，su 二进制对未授权应用不可见，
        // 但授权之后又能用，所以「找二进制」只能作为失败后的归因，不能当前置条件。
        val granted = withTimeoutOrNull(8_000) {
            runCatching {
                val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                out.contains("uid=0")
            }.getOrDefault(false)
        } ?: false
        when {
            granted -> RootState.Granted
            hasSuBinary() -> RootState.Denied
            else -> RootState.NoSu
        }
    }
}
