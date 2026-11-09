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
        if (exists("magisk")) return RootMode.Magisk
        if (exists("ksud")) return RootMode.KernelSU
        if (exists("apd")) return RootMode.APatch
        return RootMode.None
    }

    fun label(mode: RootMode): String = when (mode) {
        RootMode.Magisk -> "Magisk"
        RootMode.KernelSU -> "KernelSU"
        RootMode.APatch -> "APatch"
        RootMode.None -> ""
    }

    private suspend fun exists(bin: String): Boolean {
        val (_, out) = RootShell.run("command -v $bin 2>/dev/null")
        return out.lineSequence().any { it.trim().endsWith("/$bin") || it.trim() == bin }
    }

    /** 刷入；返回 (是否成功, 命令输出)。 */
    suspend fun install(file: File, mode: RootMode): Result {
        // 路径里可能有空格，单引号包起来，内部的单引号按 shell 规则转义
        val path = file.absolutePath.replace("'", "'\\''")
        val cmd = when (mode) {
            RootMode.Magisk -> "magisk --install-module '$path'"
            RootMode.KernelSU -> "ksud module install '$path'"
            RootMode.APatch -> "apd module install '$path'"
            RootMode.None -> return Result(false, "no supported root found")
        }
        val (ok, out) = RootShell.run(cmd)
        Log.i(TAG, "install(${label(mode)}) -> $ok ${out.take(200)}")
        return Result(ok, out)
    }
}
