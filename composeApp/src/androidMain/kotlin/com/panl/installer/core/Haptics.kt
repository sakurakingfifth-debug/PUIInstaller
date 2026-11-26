package com.panl.installer.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.panl.pui.HapticScene

/**
 * 应用内统一的触感出口。
 *
 * 每个场景的强度存在设置里（0~100，0 = 关），这里负责把百分比换成振幅：
 *  - 有振幅控制的马达（线性马达）：`VibrationEffect.createOneShot(ms, amplitude)`，
 *    amplitude 1~255 按百分比线性映射；
 *  - 只有开关型马达（老设备）：退回 `performHapticFeedback` 的默认颗粒度，
 *    强度只能走「震/不震」。
 *
 * 为什么不用 `View.performHapticFeedback` 统一做：它拿不到振幅，强度不可调。
 */
object Haptics {

    private const val TAG = "Haptics"

    /** 各场景的默认震动时长（毫秒）。安装完成是两段。 */
    private const val EDGE_MS = 12L
    private const val TOGGLE_MS = 16L
    private const val SELECT_MS = 10L
    private const val INSTALL_MS = 18L
    private const val INSTALL_GAP = 70L

    fun play(context: Context, scene: HapticScene) {
        val percent = percentOf(context, scene)
        if (percent <= 0) return
        if (scene == HapticScene.INSTALL) {
            twoPulse(context, percent)
        } else {
            single(context, durationOf(scene), percent)
        }
    }

    /** 设置页试听：直接用给定百分比，不读设置。 */
    fun preview(context: Context, scene: HapticScene, percent: Int) {
        if (percent <= 0) return
        if (scene == HapticScene.INSTALL) twoPulse(context, percent)
        else single(context, durationOf(scene), percent)
    }

    fun installSuccess(context: Context) = play(context, HapticScene.INSTALL)

    fun installFailed(context: Context) {
        single(context, 30L, 55)
    }

    // ---------------- 内部 ----------------

    private fun durationOf(scene: HapticScene) = when (scene) {
        HapticScene.EDGE -> EDGE_MS
        HapticScene.TOGGLE -> TOGGLE_MS
        HapticScene.SELECT -> SELECT_MS
        HapticScene.INSTALL -> INSTALL_MS
    }

    private fun percentOf(context: Context, scene: HapticScene): Int = runCatching {
        val s = Settings.get(context)
        when (scene) {
            HapticScene.EDGE -> s.hapticEdge
            HapticScene.TOGGLE -> s.hapticToggle
            HapticScene.SELECT -> s.hapticSelect
            HapticScene.INSTALL -> s.hapticInstall
        }
    }.getOrDefault(0)

    /** 百分比 → 振幅（1~255）；0 已在上层拦掉。 */
    private fun amplitude(percent: Int): Int =
        (percent.coerceIn(1, 100) * 255 / 100).coerceIn(1, 255)

    private fun single(context: Context, ms: Long, percent: Int) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude(percent)))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }.onFailure { Log.w(TAG, "single failed", it) }
    }

    /** 安装完成：两下短震，第二下略轻。 */
    private fun twoPulse(context: Context, percent: Int) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val a1 = amplitude(percent)
        val a2 = (a1 * 78 / 100).coerceAtLeast(1)
        runCatching {
            if (vibrator.hasAmplitudeControl()) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, INSTALL_MS, INSTALL_GAP, INSTALL_MS + 8),
                        intArrayOf(0, a1, 0, a2),
                        -1
                    )
                )
            } else {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, INSTALL_MS, INSTALL_GAP, INSTALL_MS + 8),
                        -1
                    )
                )
            }
        }.onFailure { Log.w(TAG, "twoPulse failed", it) }
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
