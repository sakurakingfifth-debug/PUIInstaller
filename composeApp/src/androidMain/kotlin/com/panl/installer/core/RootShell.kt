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

    /** 跑一条命令，返回 (是否成功, 输出)。 */
    suspend fun run(command: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            val code = p.waitFor()
            Log.i(TAG, "su -c $command -> $code ${out.take(160)}")
            (code == 0) to out
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
