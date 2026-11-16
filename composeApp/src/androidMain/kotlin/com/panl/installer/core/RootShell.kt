package com.panl.installer.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 极简 su 执行器。
 *
 * 只给一种情况用：包被系统标成「必需组件」时，PackageManager 会硬拒卸载更新
 * （日志里 `Attempted to delete system required package`，callingUid 是 0 也拒），
 * 这时只能直接删掉 /data/app 里的更新副本，重启后由系统自己回退到出厂版本。
 */
object RootShell {

    private const val TAG = "RootShell"

    /**
     * 跑一条命令，返回 (是否成功, 输出)。
     *
     * [timeoutSec] > 0 时最多等这么久，超时就 kill 掉并把已收到的输出带回去 ——
     * 有些模块的脚本会一直等音量键/电源键，没超时就会永远卡住。
     */
    suspend fun run(
        command: String,
        timeoutSec: Long = 0,
        onLine: ((String) -> Unit)? = null
    ): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                val out = StringBuilder()
                // 单独线程读，主线程才能带超时地 waitFor
                val reader = Thread {
                    runCatching {
                        p.inputStream.bufferedReader().forEachLine { line ->
                            synchronized(out) { out.appendLine(line) }
                            onLine?.invoke(line)
                        }
                    }
                }.apply { isDaemon = true; start() }

                val finished = if (timeoutSec > 0) {
                    p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)
                } else {
                    p.waitFor()
                    true
                }
                if (!finished) {
                    runCatching { p.destroyForcibly() }
                    reader.join(1000)
                    val text = synchronized(out) { out.toString() }.trim()
                    Log.w(TAG, "su -c $command 超时 ${timeoutSec}s，已中止")
                    return@withContext false to
                        ("[超时 ${timeoutSec}s，已中止]\n" + text).trim()
                }
                reader.join(1500)
                val text = synchronized(out) { out.toString() }.trim()
                Log.i(TAG, "su -c $command -> ${p.exitValue()} ${text.take(160)}")
                (p.exitValue() == 0) to text
            }.getOrElse {
                Log.w(TAG, "su failed: $command", it)
                false to (it.message ?: it.javaClass.simpleName)
            }
        }

    /**
     * 更新副本所在的目录，例如 `/data/app/~~xxx==/com.foo-yyy==`。
     * 返回 null 表示这个包没有更新副本（APK 就在系统分区里），不能删。
     */
    fun updateDirOf(context: Context, packageName: String): File? {
        val src = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).sourceDir
        }.getOrNull() ?: return null
        if (!src.startsWith("/data/app/")) return null
        val dir = File(src).parentFile ?: return null
        // 目录名固定是「包名-随机后缀」，认不出来就别动手
        if (!dir.name.startsWith("$packageName-")) return null
        return dir
    }

    /** 删掉更新副本；重启后 PMS 扫描不到它，就会回退到系统分区里的出厂版本。 */
    suspend fun removeUpdateDir(context: Context, packageName: String): Boolean {
        val dir = updateDirOf(context, packageName) ?: return false
        val (ok, _) = run("rm -rf '${dir.absolutePath}'")
        return ok && !dir.exists()
    }

    suspend fun reboot(): Boolean = run("reboot").first
}
