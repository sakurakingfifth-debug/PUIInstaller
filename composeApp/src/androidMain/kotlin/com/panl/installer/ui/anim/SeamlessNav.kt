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
        // kit 内部靠厂商实现（com.androidex.*）落地，缺了它 protocol 照样返回 100 但什么都不会动
        val vendor = runCatching {
            Class.forName("com.androidex.animation.sharedview.ViewAnimationManager")
            true
        }.getOrDefault(false)
        Log.i(TAG, "isViewAnimationSupported=$version vendorImpl=$vendor")
        version > 0
    }

    /**
     * 返回落点：跳转时把「从哪张卡片出发」记下来，退出时才能原路回去。
     *
     * key 是目标 Activity 的类名 —— 我们自己页面之间都是显式 Intent，一定有 component。
     */
    private data class ReturnTarget(val bounds: Rect, val radiusPx: Float, val color: Int)
    private val returnTargets = java.util.concurrent.ConcurrentHashMap<String, ReturnTarget>()

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

        // 记下这张卡片的位置：目标页退出时要按它回弹（不记的话退出只能走系统默认动画）
        intent.component?.className?.let { cls ->
            returnTargets[cls] = ReturnTarget(Rect(bounds), radiusPx, color)
        }

        Log.i(TAG, "open bitmap=${thumb != null} bounds=$bounds radius=$radiusPx options=${options != null}")
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
            val target = returnTargets[activity.javaClass.name]
            val config = ViewAnimationConfig()
            if (target != null) {
                // 有落点：带上位置/圆角/底色，让 kit 原路缩回那张卡片。
                // **不要**再设 backAnimType —— 默认就是反向，设成 FOLLOW_SYSTEM 反而退化成系统动画（3.8.51 的坑）
                config.setBounds(target.bounds)
                    .setRadius(target.radiusPx)
                    .setColor(target.color)
            } else {
                // 不知道从哪来的（通知、分享、外部 intent）：淡出最稳
                config.setBackAnimType(ViewAnimationType.VIEW_ANIM_FADE_OUT)
            }
            val bundle = config.toBundle()
            // 兜底类型：落点被遮挡/找不到时不要硬弹回，直接淡出
            bundle.putInt(ViewAnimationManager.EXTRA_FALLBACK_ANIM_TYPE, ViewAnimationType.VIEW_ANIM_FADE_OUT)
            runCatching {
                ViewAnimationManager.overrideViewAnimationTransition(activity, bundle)
                Log.i(TAG, "back override: target=${target != null}")
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
