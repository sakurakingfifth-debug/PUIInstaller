package com.panl.installer.ui.anim

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.View
import com.itgsa.animation.sharedview.ViewAnimationConfig
import com.itgsa.animation.sharedview.ViewAnimationListener
import com.itgsa.animation.sharedview.ViewAnimationManager
import com.itgsa.animation.sharedview.ViewAnimationType

/**
 * 一镜到底无缝动画（金标联盟 SharedViewKit）的统一入口。
 *
 * 为什么要包一层：
 *  1. 能力判断只做一次 —— [ViewAnimationManager.isViewAnimationSupported] 返回 0 时
 *     说明这台机器（非 ColorOS / 老系统）没声明支持，必须老实退回普通跳转，
 *     否则动画库内部降级路径不可控；
 *  2. Compose 里没有「源 View」，只能把卡片当场渲染成 Bitmap 交给 Bitmap 源接口；
 *  3. 返回动画要在 `finish()` **之前**调 override，且漏掉会表现为「返回动画一帧消失」，
 *     统一收口到 [back] 里避免各页面写法不一致。
 *
 * 参数单位注意：radius 一律 **px**。dp 传进来会被当成 px 消费，导致开合圆角跳变。
 */
object SeamlessNav {

    private const val TAG = "SeamlessNav"

    /** 设备是否支持一镜到底（协议版本 > 0）。首次调用会走一次 Binder，缓存住。 */
    val supported: Boolean by lazy {
        val version = runCatching { ViewAnimationManager.isViewAnimationSupported() }
            .onFailure { Log.w(TAG, "probe failed", it) }
            .getOrDefault(0)
        Log.i(TAG, "isViewAnimationSupported=$version")
        version > 0
    }

    /** 返回动画用的兜底类型：落点不可见/位置变化过大时最稳。 */
    private const val BACK_ANIM = ViewAnimationType.VIEW_ANIM_FOLLOW_SYSTEM

    private val noopListener = object : ViewAnimationListener {
        override fun onAnimationStart(animMode: Int) = Unit
        override fun onAnimationEnd(animMode: Int) = Unit
        override fun onAnimationCancel(animMode: Int) = Unit
    }

    /**
     * 从一张卡片跳到新页面。
     *
     * @param thumb 卡片截图（用 [captureCard] 现场渲染），为 null 时退回普通跳转
     * @param bounds 卡片在屏幕上的位置（px）
     * @param radiusPx 圆角半径（px）
     * @param color 新页面底色；必须等于返回落点页面的底色，否则动画边缘会露出色差
     */
    fun openFromCard(
        context: Context,
        intent: Intent,
        thumb: Bitmap?,
        bounds: Rect?,
        radiusPx: Float,
        color: Int = 0
    ) {
        if (!supported || thumb == null || bounds == null) {
            context.startActivity(intent)
            return
        }
        val options = runCatching {
            ViewAnimationManager.makeViewAnimationOptions(
                thumb,
                bounds,
                radiusPx.coerceAtLeast(0f),
                ViewAnimationConfig().setColor(color).toBundle(),
                noopListener
            )
        }.onFailure { Log.w(TAG, "makeViewAnimationOptions(bitmap) failed", it) }.getOrNull()

        runCatching {
            if (options != null) context.startActivity(intent, options.toBundle())
            else context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "startActivity with animation failed, fallback", it)
            runCatching { context.startActivity(intent) }
        }
    }

    /** 有真实 View 的场合（非 Compose，比如浮窗里点某一行）。 */
    fun openFromView(
        context: Context,
        intent: Intent,
        source: View?,
        radiusPx: Float = 0f,
        color: Int = 0
    ) {
        if (!supported || source == null) {
            context.startActivity(intent)
            return
        }
        val options = runCatching {
            ViewAnimationManager.makeViewAnimationOptions(
                source,
                ViewAnimationConfig()
                    .setRadius(radiusPx.coerceAtLeast(0f))
                    .setColor(color)
                    .toBundle(),
                noopListener
            )
        }.onFailure { Log.w(TAG, "makeViewAnimationOptions(view) failed", it) }.getOrNull()

        runCatching {
            if (options != null) context.startActivity(intent, options.toBundle())
            else context.startActivity(intent)
        }.getOrElse {
            runCatching { context.startActivity(intent) }
        }
    }

    /**
     * 退出当前页面：先告诉系统「返回到哪、用哪种返回动画」，再 finish。
     *
     * 必须在 finish 之前调用；不支持一镜到底时就是普通 finish。
     */
    fun back(activity: Activity) {
        if (supported) {
            runCatching {
                ViewAnimationManager.overrideViewAnimationTransition(
                    activity,
                    ViewAnimationConfig().setBackAnimType(BACK_ANIM).toBundle()
                )
            }.onFailure { Log.w(TAG, "override transition failed", it) }
        }
        activity.finish()
    }

    /** 需要中断动画时用（比如切根任务、从浮窗直接结束） */
    fun cancel() {
        if (!supported) return
        runCatching { ViewAnimationManager.cancelCurrentViewAnimation() }
    }
}
