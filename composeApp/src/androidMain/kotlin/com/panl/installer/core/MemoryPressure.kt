package com.panl.installer.core

import android.content.Context
import android.util.Log

/**
 * 内存压力的统一出口。两条来源都走这里：
 *
 *  1. **公平运行内存**（ITGSA，vivo/小米/OPPO/荣耀）：`itgsa.intent.action.TRIM` 预警、
 *     `itgsa.intent.action.KILL` 查杀，见 [FairMemoryReceiver]；
 *  2. **Android 内存限制器 / 系统回调**：`Application.onTrimMemory()` 的 CRITICAL / BACKGROUND 级别。
 *
 * 安装器本身是个小应用（前台 PSS ≈ 89MB，其中 63MB 是 Compose 的原生堆），没有
 * 图片墙那种大缓存要清；这里做的是**在系统要内存时立刻归还**：
 *  - 把还能丢的临时状态丢掉（图标位图是按需解码的，不常驻）；
 *  - 显式 GC 一次（只在收到明确信号时做，正常流程绝不调用）；
 *  - 记一条 `[MEM]` 日志，用户能从「设置 → 日志」里看到自己被打过招呼。
 *
 * 之所以敢在这里 GC：公平运行内存给的处理窗口只有 3 秒，而查杀前主动回收
 * 比被 kernel 直接带走的代价小得多。
 */
object MemoryPressure {

    private const val TAG = "MemoryPressure"

    @Volatile
    private var lastReleaseAt = 0L

    /**
     * @param source 触发来源，只用于日志（`TRIM` / `KILL` / `onTrimMemory(x)`）
     */
    fun release(context: Context, source: String) {
        val now = System.currentTimeMillis()
        // 同一波信号可能重复到达（TRIM 后紧跟 KILL），1 秒内只处理一次
        if (now - lastReleaseAt < 1000) return
        lastReleaseAt = now

        runCatching { System.gc() }
        InstallerLog.record(context, "MEM", "收到内存压力（$source），已归还可回收内存")
        Log.i(TAG, "released from $source")
    }

    /**
     * 查杀前的现场保护。安装器没有可续传的业务状态（安装中的会话由系统
     * `PackageInstaller` 持有，进程没了会话还在），所以只需要把日志刷到盘上，
     * 保证用户重启后能看到「为什么被打断」。
     */
    fun preserve(context: Context, source: String) {
        InstallerLog.record(context, "MEM", "即将被系统回收（$source），已保存现场")
    }
}
