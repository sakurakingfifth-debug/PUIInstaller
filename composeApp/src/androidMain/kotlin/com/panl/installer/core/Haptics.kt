package com.panl.installer.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * 应用内统一的触感出口。
 *
 * 轻交互（滚到边界、点选项、切开关）走 View 的 `performHapticFeedback`，由系统按
 * 「触感反馈」开关决定强度；这里只补**安装完成**这种一次性事件 —— 它需要明确一点的
 * 两下短震，用 Vibrator 直接下发波形，比 CLOCK_TICK 更“有分量”。
 */
object Haptics {

    private const val TAG = "Haptics"

    /** 安装完成：两下短震（0ms → 18ms 强 → 70ms 静默 → 26ms 稍弱）。 */
    fun installSuccess(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 18, 70, 26),
                intArrayOf(0, 255, 0, 190),
                -1
            )
            vibrator.vibrate(effect)
        }.onFailure { Log.w(TAG, "installSuccess failed", it) }
    }

    /** 安装失败：一下短促（区别于成功，别让人误以为装好了）。 */
    fun installFailed(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(30, 140))
        }.onFailure { Log.w(TAG, "installFailed failed", it) }
    }

    private fun vibrator(context: Context): Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()
}
