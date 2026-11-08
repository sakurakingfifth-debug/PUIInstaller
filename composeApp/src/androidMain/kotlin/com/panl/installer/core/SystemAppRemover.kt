package com.panl.installer.core

import android.content.Context
import android.util.Log
import com.panl.installer.i18n.tr
import java.io.File

/**
 * 彻底卸载系统应用：连系统分区里的 APK 一起干掉。
 *
 * 现状：Android 10 之后 system / system_ext / product / my_product 都是 **EROFS 只读**
 * （dm-verity），`mount -o rw,remount` 基本必失败，硬删文件不现实。所以分两步：
 *
 *  1. `pm uninstall --user all` —— 立刻对所有用户生效，应用从设备上「消失」；
 *  2. **挂载遮罩**：写一个 Magisk / KernelSU 模块，用 `post-fs-data.sh` 在开机早期
 *     把目标目录 mount 成空 tmpfs，APK 就真的从系统分区「看不见」了。
 *
 * 第 2 步比 remount 改分区安全：不动分区内容，删掉模块目录 + 重启就能完全恢复。
 * 万一分区可写（老设备），会优先直接 rm，省掉一次重启。
 */
object SystemAppRemover {

    private const val TAG = "SystemAppRemover"
    private const val MODULE_PREFIX = "pui_removed_"

    data class Result(val ok: Boolean, val needsReboot: Boolean, val message: String?)

    /** 系统分区顶层目录名，用来把绝对路径拆成「分区 + 相对路径」。 */
    private val PARTITIONS = listOf(
        "/system_ext", "/system", "/product", "/my_product", "/my_heytap",
        "/my_stock", "/vendor", "/odm", "/oem", "/my_bigball", "/my_carrier", "/my_company"
    )

    suspend fun remove(context: Context, packageName: String, apkPath: String?): Result {
        // 1. 先对所有用户卸载（这一步立刻生效，也是失败时的兜底）
        var (uninstallOk, uninstallOut) = RootShell.run("pm uninstall --user all $packageName")
        Log.i(TAG, "uninstall --user all $packageName -> $uninstallOk ${uninstallOut.take(80)}")
        if (!uninstallOk || !uninstallOut.contains("Success")) {
            // 系统包对「所有用户」卸载常被 PMS 拒（DELETE_FAILED_INTERNAL_ERROR），
            // 退回只卸当前用户；APK 由下面的遮罩负责
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
                needsReboot = false,
                message = if (uninstallOk) null else uninstallOut.take(120)
            )
        }

        // 2. 能直接删就删（少数设备分区可写），删完仍需重启让 PMS 忘掉
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
                    return Result(true, needsReboot = true, message = null)
                }
                Log.w(TAG, "rm failed: ${rmOut.take(80)}")
            }
        }

        // 3. 写挂载遮罩模块（开机时把目录挂成空 tmpfs）
        val ok = writeMaskModule(packageName, dir, partition)
        return Result(
            ok = uninstallOk || ok,
            needsReboot = ok,
            message = if (ok || uninstallOk) {
                null
            } else {
                tr(
                    "卸载失败：ROOT 没权限，也没能写入挂载遮罩",
                    "Failed — no ROOT access and the mount mask couldn't be written"
                )
            }
        )
    }

    /** 恢复：删掉遮罩模块（重启后应用回来）。 */
    suspend fun restore(packageName: String): Boolean {
        val (ok, out) = RootShell.run("rm -rf '/data/adb/modules/$MODULE_PREFIX${sanitize(packageName)}'")
        Log.i(TAG, "restore $packageName -> $ok ${out.take(80)}")
        return ok
    }

    private fun sanitize(pkg: String) = pkg.replace('.', '_').replace('-', '_')

    private fun base64(text: String): String =
        android.util.Base64.encodeToString(text.toByteArray(), android.util.Base64.NO_WRAP)

    /**
     * 写一个「遮罩模块」：`post-fs-data.sh` 在开机早期把应用目录挂成空 tmpfs。
     *
     * 为什么不用 Magisk 的 `.replace`：这台机器是 KernelSU + Magic Mount-rs 这个 metamodule，
     * 它扫描模块时只认能落到文件系统的真实文件，只有 `.replace` 的模块会直接被
     * `mode=ignore` 跳过（实测过，重启后 APK 还在）。用 tmpfs 盖目录不依赖任何挂载实现，
     * 恢复也简单：删掉模块目录重启即可。
     */
    private suspend fun writeMaskModule(packageName: String, dir: String, partition: String?): Boolean {
        val id = MODULE_PREFIX + sanitize(packageName)
        val moduleDir = "/data/adb/modules/$id"
        val target = dir
        val prop = """
            id=$id
            name=PUI removed $packageName
            version=1.0
            versionCode=1
            author=PUI Installer
            description=${tr("彻底卸载系统应用留下的遮罩，删掉本目录并重启即可恢复", "Mount mask left by PUI Installer; delete this folder and reboot to restore")} $packageName
        """.trimIndent()

        val script = listOf(
            "#!/system/bin/sh",
            "# 开机早期把系统分区里的应用目录盖成空 tmpfs，系统就扫不到 APK 了",
            "DIR='$target'",
            "[ -d \"\$DIR\" ] && mount -t tmpfs none \"\$DIR\""
        ).joinToString("\n") + "\n"

        // 用 base64 写文件：脚本里有引号、$、换行，直接拼进 shell 命令很容易被吃掉
        val propB64 = base64(prop + "\n")
        val scriptB64 = base64(script)
        val cmd = buildString {
            append("mkdir -p '$moduleDir' && ")
            append("echo '$propB64' | base64 -d > '$moduleDir/module.prop' && ")
            append("echo '$scriptB64' | base64 -d > '$moduleDir/post-fs-data.sh' && ")
            append("chmod 755 '$moduleDir/post-fs-data.sh' && echo OK")
        }
        val (ok, out) = RootShell.run(cmd)
        Log.i(TAG, "mask module -> $ok ${out.take(80)} dir=$target")
        return ok && out.contains("OK")
    }
}
