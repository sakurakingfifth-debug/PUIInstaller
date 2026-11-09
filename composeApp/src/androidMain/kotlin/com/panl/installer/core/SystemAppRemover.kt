package com.panl.installer.core

import android.content.Context
import android.util.Log
import com.panl.installer.i18n.tr
import java.io.File

/**
 * 彻底卸载系统应用：直接删掉系统分区里的 APK，**不写任何 Magisk / KernelSU 模块**。
 *
 * 做法只有两步：
 *  1. `pm uninstall --user all` —— 对所有用户卸载，应用立刻从桌面/列表里消失；
 *  2. remount 分区为可写后 `rm -rf` 应用目录，连 APK 一起删掉。
 *
 * 现实限制：Android 10 之后 system / system_ext / product / my_* 基本都是 **EROFS 只读**
 * （dm-verity），`mount -o rw,remount` 会失败，文件物理上删不掉。这时只能退化成
 * 「仅卸载」，并把 [Result.apkDeleted] 置 false 让界面如实告诉用户。
 */
object SystemAppRemover {

    private const val TAG = "SystemAppRemover"

    data class Result(
        /** 卸载是否成功（应用已经对所有用户不可用）。 */
        val ok: Boolean,
        /** 分区里的 APK 是否真的删掉了。 */
        val apkDeleted: Boolean,
        /** 删掉了文件的话，要重启让 PMS 忘掉旧记录。 */
        val needsReboot: Boolean,
        val message: String?
    )

    /** 系统分区顶层目录名，用来把绝对路径拆成「分区 + 相对路径」。 */
    private val PARTITIONS = listOf(
        "/system_ext", "/system", "/product", "/my_product", "/my_heytap",
        "/my_stock", "/vendor", "/odm", "/oem", "/my_bigball", "/my_carrier", "/my_company"
    )

    suspend fun remove(context: Context, packageName: String, apkPath: String?): Result {
        // 1. 先对所有用户卸载（这一步立刻生效，也是删不掉文件时的兜底）
        var (uninstallOk, uninstallOut) = RootShell.run("pm uninstall --user all $packageName")
        Log.i(TAG, "uninstall --user all $packageName -> $uninstallOk ${uninstallOut.take(80)}")
        if (!uninstallOk || !uninstallOut.contains("Success")) {
            // 系统包对「所有用户」卸载常被 PMS 拒（DELETE_FAILED_INTERNAL_ERROR），
            // 退回只卸当前用户
            val fallback = RootShell.run("pm uninstall --user 0 $packageName")
            Log.i(TAG, "uninstall --user 0 $packageName -> ${fallback.first} ${fallback.second.take(80)}")
            if (fallback.first && fallback.second.contains("Success")) {
                uninstallOk = true
                uninstallOut = fallback.second
            }
        }

        val dir = apkPath?.substringBeforeLast('/')
        if (dir.isNullOrBlank()) {
            return Result(
                ok = uninstallOk,
                apkDeleted = false,
                needsReboot = false,
                message = if (uninstallOk) null else uninstallOut.take(120)
            )
        }

        // 2. 分区可写就直接把目录连 APK 一起删掉
        val partition = PARTITIONS.firstOrNull { dir == it || dir.startsWith("$it/") }
        if (partition != null) {
            RootShell.run("mount -o rw,remount $partition")
            // mount 在 EROFS 上也会返回 0，只是打了句「read-only」，
            // 所以真写一下试试，写不进去就别 rm 了
            val probe = "$partition/.pui_probe"
            val (writable, _) = RootShell.run("touch '$probe' && rm -f '$probe' && echo WRITABLE")
            if (writable) {
                val (removed, rmOut) = RootShell.run("rm -rf '$dir'")
                if (removed && !File(dir).exists()) {
                    Log.i(TAG, "deleted $dir directly")
                    return Result(true, apkDeleted = true, needsReboot = true, message = null)
                }
                Log.w(TAG, "rm failed: ${rmOut.take(80)}")
            }
        }

        // 3. 分区只读：文件删不掉，只能如实告诉用户「只卸载了，APK 还在分区里」
        Log.w(TAG, "partition read-only, apk kept: $dir")
        return Result(
            ok = uninstallOk,
            apkDeleted = false,
            needsReboot = false,
            message = if (uninstallOk) {
                tr(
                    "已对所有用户卸载，但系统分区是只读的，APK 文件删不掉",
                    "Uninstalled for all users, but the system partition is read-only — the APK file can't be deleted"
                )
            } else {
                uninstallOut.take(120)
            }
        )
    }

}
