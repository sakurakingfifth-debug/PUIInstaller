package com.panl.installer.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 安装器自己的日志。
 *
 * 只记**异常和失败**，不记流水账：安装会话创建失败、系统返回失败、ROOT 兜底失败、
 * 删安装包失败、以及任何没被捕获的崩溃（[installCrashHandler]）。
 * 目的很单一 —— 用户报「装不上 / 删不掉」时，让 ta 能把这一段直接分享出来。
 *
 * 存储：`files/logs/installer.log`，超过 [MAX_BYTES] 就截断保留后半段（只留一份，不做轮转）。
 */
object InstallerLog {

    private const val TAG = "InstallerLog"
    private const val FILE_NAME = "installer.log"
    private const val MAX_BYTES = 256 * 1024

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private fun dir(context: Context) = File(context.filesDir, "logs").apply { mkdirs() }

    fun file(context: Context) = File(dir(context), FILE_NAME)

    /** 记一条。 [error] 有值时会把堆栈一起写进去。 */
    fun record(context: Context, tag: String, message: String, error: Throwable? = null) {
        val line = buildString {
            append(stamp.format(Date()))
            append(" [").append(tag).append("] ")
            append(message)
            if (error != null) {
                append('\n').append(Log.getStackTraceString(error).trimEnd())
            }
            append("\n\n")
        }
        Log.i(TAG, "$tag: $message")
        runCatching {
            synchronized(this) {
                val f = file(context)
                if (f.exists() && f.length() + line.length > MAX_BYTES) {
                    // 只留后半段，够看最近发生的事就行
                    val keep = runCatching {
                        val text = f.readText()
                        text.substring((text.length / 2).coerceAtMost(text.length))
                    }.getOrDefault("")
                    f.writeText("…（更早的日志已截断）…\n\n$keep")
                }
                f.appendText(line)
            }
        }.onFailure { Log.w(TAG, "write failed", it) }
    }

    /** 读全部日志；没有就是空串。 */
    fun read(context: Context): String = runCatching {
        val f = file(context)
        if (f.exists()) f.readText() else ""
    }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * 分享用的正文：头部带上版本 / 系统信息，后面接日志。
     * 太长会被 Binder 截断，这里先砍到 300 KB。
     */
    fun shareText(context: Context): String {
        val version = runCatching {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            "${pi.versionName} (${pi.longVersionCode})"
        }.getOrDefault("?")
        val header = buildString {
            append("PUI Installer ").append(version).append('\n')
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(" · Android ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("—— 日志 ——\n")
        }
        val body = read(context).ifBlank { "（没有记录到异常）" }
        val text = header + body
        return if (text.length > 300_000) text.take(300_000) + "\n…（已截断）" else text
    }

    /**
     * 兜底崩溃日志：任何没被 catch 的异常都会先写一条再交回原来的处理器。
     *
     * 系统应用在 direct boot 阶段被拉起时 filesDir 可能不可写，整段包 runCatching。
     */
    fun installCrashHandler(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                record(app, "CRASH", "未捕获异常 @ ${thread.name}", throwable)
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }
}
