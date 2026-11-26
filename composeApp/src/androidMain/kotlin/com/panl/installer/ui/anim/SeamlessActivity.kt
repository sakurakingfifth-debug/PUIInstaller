package com.panl.installer.ui.anim

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 所有页面 Activity 的基类：统一把「退出」接进一镜到底。
 *
 * 为什么拦 [finish] 而不是逐个改 onClose：
 *  - 顶栏返回、系统返回键、手势返回最终都走到 finish()，一处收口全都能盖住；
 *  - `SeamlessNav.back` 内部还会再调一次 finish()，用 [finishing] 防止无限递归。
 */
open class SeamlessActivity : ComponentActivity() {

    private var finishing = false

    override fun finish() {
        if (!finishing) {
            finishing = true
            // back() 会先 override 返回落点，再回调 finish() → 这里打个标记放行
            SeamlessNav.back(this)
            return
        }
        super.finish()
    }
}

/**
 * Compose 里的「从卡片跳转」工具。
 *
 * Compose 没有源 View，所以点击瞬间把窗口画进一张 Bitmap，再按卡片的区域裁一块出来，
 * 交给 SharedViewKit 的 Bitmap 源接口。裁剪比整窗截图更接近卡片的真实视觉，
 * 也不会因为相邻卡片的像素混进动画起点。
 *
 * 用法：
 * ```
 * val nav = rememberSeamlessNavigator()
 * var bounds by remember { mutableStateOf(Rect()) }
 * Row(Modifier.onGloballyPositioned { bounds = it.boundsInRoot() }) {
 *     ... onClick = { nav.open(intent, bounds, radiusPx = 24f * density, color = bgColor) }
 * }
 * ```
 */
class SeamlessNavigator(
    private val activity: Activity?,
    private val root: View
) {
    private val context get() = activity ?: root.context

    /** 单位 px；cards 的圆角一般是 20~24dp，调用方自己乘 density。 */
    fun open(
        intent: Intent,
        boundsInRoot: androidx.compose.ui.geometry.Rect?,
        radiusPx: Float,
        color: Int = 0
    ) {
        if (activity == null || !SeamlessNav.supported || boundsInRoot == null || boundsInRoot.isEmpty) {
            context.startActivity(intent)
            return
        }
        val local = Rect(
            boundsInRoot.left.toInt(), boundsInRoot.top.toInt(),
            boundsInRoot.right.toInt(), boundsInRoot.bottom.toInt()
        )
        val thumb = capture(local)
        // onGloballyPositioned 给的是窗口内坐标；kit 要的是屏幕坐标
        val loc = IntArray(2).also { root.getLocationOnScreen(it) }
        val screen = Rect(
            (boundsInRoot.left + loc[0]).toInt(),
            (boundsInRoot.top + loc[1]).toInt(),
            (boundsInRoot.right + loc[0]).toInt(),
            (boundsInRoot.bottom + loc[1]).toInt()
        )
        SeamlessNav.openFromCard(activity, intent, thumb, screen, radiusPx, color)
    }

    private fun capture(boundsInRoot: Rect): Bitmap? = runCatching {
        val full = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(full))
        val safe = Rect(boundsInRoot).apply {
            left = left.coerceIn(0, full.width - 1)
            top = top.coerceIn(0, full.height - 1)
            right = right.coerceIn(left + 1, full.width)
            bottom = bottom.coerceIn(top + 1, full.height)
        }
        Bitmap.createBitmap(full, safe.left, safe.top, safe.width(), safe.height())
            .also { full.recycle() }
    }.onFailure { Log.w("SeamlessNavigator", "capture failed", it) }.getOrNull()
}

@Composable
fun rememberSeamlessNavigator(): SeamlessNavigator {
    val view = LocalView.current
    val activity = view.context as? Activity
    return remember(view, activity) { SeamlessNavigator(activity, view) }
}
