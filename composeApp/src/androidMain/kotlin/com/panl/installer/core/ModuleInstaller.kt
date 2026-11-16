package com.panl.installer.core

import android.util.Log
import java.io.File

/**
 * 把 zip 模块交给设备上的 root 方案刷入。
 *
 * 三家命令不同、逻辑一样（参考 InstallerX-Revived 的 ModuleInstallerUtils）：
 *  - Magisk   : `magisk --install-module <zip>`
 *  - KernelSU : `ksud module install <zip>`
 *  - APatch   : `apd module install <zip>`
 *
 * 全部通过 `su -c` 执行，输出原样回传，界面上直接给用户看。
 */
object ModuleInstaller {

    private const val TAG = "ModuleInstaller"

    enum class RootMode { Magisk, KernelSU, APatch, None }

    data class Result(val ok: Boolean, val output: String)

    /** 当前设备用的是哪家 root。 */
    suspend fun detect(): RootMode {
        if (binary(RootMode.Magisk) != null) return RootMode.Magisk
        if (binary(RootMode.KernelSU) != null) return RootMode.KernelSU
        if (binary(RootMode.APatch) != null) return RootMode.APatch
        return RootMode.None
    }

    /**
     * 找到可执行文件的真实路径。
     *
     * 只靠 `command -v` 不够 —— su 会话里的 PATH 常常不含 /data/adb/... ：
     * APatch 的 apd 在 /data/adb/ap/bin/apd，KernelSU 的 ksud 在 /data/adb/ksud，
     * 这些在普通 shell 里 `command -v` 一律找不到，会误报「没有检测到 root」。
     */
    private suspend fun binary(mode: RootMode): String? {
        val name = when (mode) {
            RootMode.Magisk -> "magisk"
            RootMode.KernelSU -> "ksud"
            RootMode.APatch -> "apd"
            RootMode.None -> return null
        }
        val known = when (mode) {
            RootMode.Magisk -> "/data/adb/magisk/magisk"
            RootMode.KernelSU -> "/data/adb/ksud"
            RootMode.APatch -> "/data/adb/ap/bin/apd"
            RootMode.None -> ""
        }
        val (_, out) = RootShell.run(
            "P=\$(command -v $name 2>/dev/null); " +
                "[ -n \"\$P\" ] || { [ -x '$known' ] && P='$known'; }; echo \"PATH=\$P\""
        )
        return out.lineSequence()
            .firstOrNull { it.startsWith("PATH=") }
            ?.removePrefix("PATH=")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun label(mode: RootMode): String = when (mode) {
        RootMode.Magisk -> "Magisk"
        RootMode.KernelSU -> "KernelSU"
        RootMode.APatch -> "APatch"
        RootMode.None -> ""
    }


    /**
     * 刷入；返回 (是否成功, 命令输出)。
     *
     * 默认 5 分钟超时：需要按音量键/电源键的模块会在脚本里无限等待，
     * 没有超时的话界面就永远停在「正在刷入」。
     */
    /** 刷入的四个阶段，界面据此显示进度（不再是一个转圈转到底）。 */
    enum class Stage { DetectRoot, Flash, Verify, Label, Done }

    suspend fun install(
        file: File,
        mode: RootMode,
        moduleId: String? = null,
        timeoutSec: Long = 300,
        onStage: ((Stage, String?) -> Unit)? = null,
        onLine: ((String) -> Unit)? = null
    ): Result {
        // 路径里可能有空格，单引号包起来，内部的单引号按 shell 规则转义
        val path = file.absolutePath.replace("'", "'\\''")
        onStage?.invoke(Stage.DetectRoot, null)
        val bin = binary(mode) ?: return Result(false, "no supported root found")
        onStage?.invoke(Stage.DetectRoot, label(mode))
        val cmd = when (mode) {
            RootMode.Magisk -> "'$bin' --install-module '$path'"
            RootMode.KernelSU -> "'$bin' module install '$path'"
            RootMode.APatch -> "'$bin' module install '$path'"
            RootMode.None -> return Result(false, "no supported root found")
        }
        onStage?.invoke(Stage.Flash, null)
        val (ok, out) = RootShell.run(cmd, timeoutSec, onLine)
        Log.i(TAG, "install(${label(mode)}) -> $ok ${out.take(200)}")
        if (!ok) return Result(false, out)
        // module.prop 是 zstd 压缩的包（新版 LSPosed/Vector）读不到 id，
        // 刷完从 /data/adb/modules 里挑最新建的那个目录当 id
        onStage?.invoke(Stage.Verify, null)
        onLine?.invoke("")
        onLine?.invoke("- [PUI] 校验安装结果…")
        val resolvedId = moduleId?.takeIf { it.isNotBlank() } ?: newestModuleId()
        if (resolvedId == null) {
            onStage?.invoke(Stage.Done, null)
            return Result(true, out)
        }
        // 关键一步：把模块文件的 SELinux 标签归位
        onStage?.invoke(Stage.Label, resolvedId)
        onLine?.invoke("- [PUI] 修复 SELinux 标签：$resolvedId")
        val labelResult = fixLabels(resolvedId)
        onStage?.invoke(Stage.Done, null)
        return Result(true, out + "\n" + labelResult)
    }

    /**
     * 修 SELinux 标签。
     *
     * 从应用（platform_app）里调 `su -c "ksud module install"` 时，解出来的模块文件会**继承调用方的
     * SELinux 类别**（`u:object_r:system_file:s0:c129,c257,c512,c768`）。模块本身没错，但这些类别
     * 会让所有普通应用进程 `search` 被拒 —— 覆盖到 /system_ext 的 APK 于是读不出来：
     *   `Failed to open APK ...: I/O error` → `ResourcesManager` 加载资源失败
     *   → 进程 bind 阶段 `GraphicsEnvironment` 空指针 → 该应用直接打不开（桌面图标也会变成包名）。
     *
     * 所以刷完立刻把整个模块目录 chcon 回 `u:object_r:system_file:s0`（restorecon 兜底）。
     * 标签只在开机挂载时生效，仍需要重启。
     */
    /** /data/adb/modules 下最新的那个目录名（按修改时间）。 */
    private suspend fun newestModuleId(): String? {
        val (ok, out) = RootShell.run("ls -dt /data/adb/modules/*/ 2>/dev/null | head -1")
        if (!ok || out.isBlank()) return null
        return out.trim().trimEnd('/').substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    private suspend fun fixLabels(moduleId: String): String {
        val dir = "/data/adb/modules/$moduleId"
        val cmd = "chcon -R u:object_r:system_file:s0 '$dir' 2>/dev/null || restorecon -R '$dir'"
        val (ok, out) = RootShell.run("$cmd; echo EXIT=\$?")
        Log.i(TAG, "fixLabels($moduleId) -> $ok ${out.take(200)}")
        return if (ok) "已修复模块的 SELinux 标签（仍需重启才生效）" else "修复 SELinux 标签失败：${out.take(200)}"
    }
}
