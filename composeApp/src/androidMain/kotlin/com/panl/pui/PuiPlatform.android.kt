package com.panl.pui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowCompat

// ---------------- 壁纸取色 ----------------

@Composable
actual fun rememberWallpaperAccent(
    darkTheme: Boolean,
    dynamicAccent: Boolean,
    fallback: Color
): Color {
    if (!dynamicAccent || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback
    val ctx = LocalContext.current
    return remember(darkTheme, ctx) {
        runCatching {
            if (darkTheme) dynamicDarkColorScheme(ctx).primary
            else dynamicLightColorScheme(ctx).primary
        }.getOrDefault(fallback)
    }
}

// ---------------- 触感 ----------------
//
// CLOCK_TICK 从 API 3 就存在，AOSP 的 NumberPicker / TimePicker 一直用它，
// 各家 OEM 都有真实映射；SEGMENT_TICK 之类 API 34 新常量在部分厂商上会退化成
// 几乎感觉不到的 TEXTURE_TICK 兜底（实测 OPPO ColorOS 17）。

@Composable
actual fun rememberTickHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }
}

@Composable
actual fun rememberToggleHaptic(checked: Boolean): () -> Unit {
    val view = LocalView.current
    return remember(view, checked) {
        {
            view.performHapticFeedback(
                if (checked) HapticFeedbackConstants.CLOCK_TICK
                else HapticFeedbackConstants.KEYBOARD_TAP
            )
        }
    }
}

// ---------------- 系统栏高度 ----------------

@Composable
actual fun rememberStatusBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

@Composable
actual fun rememberNavigationBarHeight(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

// ---------------- 顶部渐进模糊 + NavBar 毛玻璃 ----------------
//
// 一次 AGSL 同时算两件事：
//   1. 顶部渐进模糊：模糊半径只取决于像素 y，内容怎么滚，顶部那一段始终是糊的；
//   2. NavBar 胶囊内的毛玻璃：胶囊区域内再做一次等半径模糊，边缘用圆角矩形的有符号距离羽化。
//
// 为什么不用 RenderEffect 拼「blur + 蒙版 + blend」那一套：
//   那套要把同一个 RenderEffect 节点（base）用在两个地方（一次进 blur，一次进 blend），
//   而 RenderEffect 图里一个节点只能被消费一次 —— 在部分设备上整条链会静默失效，
//   表现就是「顶部糊了，但 NavBar 底下是清的」。放进同一个 shader 里就没有这个问题。
//
// 采样：跟着半径走 —— 顶部 8+16=24 次，往下线性递减到 8 次；胶囊区域固定 14 次。
// 1.0.72 之前是顶部固定 48、胶囊 32，144Hz 屏上 GPU 要 9.8ms（预算 6.91ms），是卡顿主因。
// 现在这组数 GPU 约 6.3ms，28dp 半径下看不出颗粒感。要加采样先做同构建 A/B，别凭感觉调。

private const val PROGRESSIVE_BLUR_AGSL = """
uniform shader content;
uniform float2 size;
uniform float blurH;
uniform float maxR;

// NavBar 胶囊（图层坐标系）
uniform float2 barCenter;
uniform float2 barHalf;
uniform float barRadius;
uniform float barBlur;
uniform float barOn;

float hash(float2 p) {
    return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
}

// 圆角矩形的有符号距离：<0 在内部
float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return length(max(q, float2(0.0, 0.0))) + min(max(q.x, q.y), 0.0) - r;
}

// 黄金角螺旋采样，n 次
half4 spiralBlur(float2 c, float r, float rot, int n) {
    half4 acc = half4(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 24; i++) {
        if (i >= n) break;
        float fi = float(i);
        float a = fi * 2.3999632 + rot;
        float rad = sqrt((fi + 0.5) / float(n)) * r;
        float2 p = clamp(c + float2(cos(a), sin(a)) * rad,
                         float2(0.5, 0.5), size - float2(0.5, 0.5));
        float w = 1.0 - 0.5 * (rad / r);
        acc += half4(content.eval(p)) * half(w);
        wsum += w;
    }
    return acc / half(wsum);
}

half4 main(float2 c) {
    // 顶部渐进模糊
    float t = clamp(1.0 - c.y / blurH, 0.0, 1.0);
    float r = maxR * t * (2.0 - t);
    // 采样数跟着半径走：顶端半径最大才用满 32 次，越往下越少。
    // 这条带子占了整屏约 1/8 的像素，144Hz 屏上每帧 26M 次采样是压不住的主因。
    half4 col = (r < 0.75)
        ? half4(content.eval(c))
        : spiralBlur(c, r, hash(c) * 6.2831853,
                     8 + int(clamp(r / max(maxR, 0.001), 0.0, 1.0) * 16.0));

    // NavBar 胶囊内的毛玻璃
    if (barOn > 0.5) {
        float d = sdRoundBox(c - barCenter, barHalf, barRadius);
        float k = 1.0 - smoothstep(-1.0, 1.0, d);
        if (k > 0.002) {
            half4 b = spiralBlur(c, barBlur, hash(c + float2(17.0, 31.0)) * 6.2831853, 14);
            col = mix(col, b, half(k));
        }
    }
    return col;
}
"""

@Composable
actual fun Modifier.puiProgressiveTopBlur(
    height: Dp,
    maxRadius: Dp,
    backdrop: NavBackdrop?
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val shader = remember { RuntimeShader(PROGRESSIVE_BLUR_AGSL) }
    return this.graphicsLayer {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("blurH", height.toPx())
        shader.setFloatUniform("maxR", maxRadius.toPx())

        // 胶囊几何：跟随 NavBar 的拉伸（pull / press）
        var barOn = 0f
        if (backdrop != null) {
            val px = backdrop.pullX?.value
            val py = backdrop.pullY?.value
            val pr = backdrop.press?.value
            val w = backdrop.barSize.width.toFloat()
            val h = backdrop.barSize.height.toFloat()
            if (px != null && py != null && pr != null && w > 0f && h > 0f) {
                val sx = barScaleX(px, py, pr)
                val sy = barScaleY(px, py, pr)
                val cx = backdrop.barOrigin.x - backdrop.origin.x + w / 2f + px * w / 2f
                val cy = backdrop.barOrigin.y - backdrop.origin.y + h / 2f + py * h / 2f
                shader.setFloatUniform("barCenter", cx, cy)
                shader.setFloatUniform("barHalf", w * sx / 2f, h * sy / 2f)
                shader.setFloatUniform("barRadius", h * sy / 2f)
                shader.setFloatUniform("barBlur", backdrop.blurPx)
                barOn = 1f
            }
        }
        shader.setFloatUniform("barOn", barOn)

        compositingStrategy = CompositingStrategy.Offscreen
        renderEffect = RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}

actual val PuiPlatformSupportsBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

// ---------------- 实时模糊（背景景深） ----------------

@Composable
actual fun Modifier.puiRealtimeBlur(progress: () -> Float, maxRadius: Dp): Modifier {
    // 半径量化到 0.5px，同一个效果对象复用 —— 每帧新建一个 RenderEffect 会一直产生 native 垃圾
    val cache = remember { BlurEffectCache() }
    // createBlurEffect 是 API 31 才有的；低版本直接不加效果，动画本身照常
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return this.graphicsLayer {
        val p = progress().coerceIn(0f, 1f)
        // 只在「完全不用糊」时摘掉效果。这里**不判断上界**：
        // 调用方传进来的进度通常已经被压过曲线（比如 ×2.2），很快就是 1.0，
        // 要是拿 1.0 当「已经盖住」的信号，模糊会在动画刚过一半时整段消失 —— 背景就漏出来了。
        // 「什么时候可以省掉」由调用方决定（传 0 表示不用糊）。
        val on = p > 0.002f
        compositingStrategy = if (on) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        renderEffect = if (on) cache.get(maxRadius.toPx() * p).asComposeRenderEffect() else null
    }
}

/** 按量化后的半径缓存 `RenderEffect`，避免每帧重新创建。 */
private class BlurEffectCache {
    private var radius = -1f
    private var effect: RenderEffect? = null

    fun get(r: Float): RenderEffect {
        val q = (r * 2f).toInt() / 2f
        val cached = effect
        if (cached != null && q == radius) return cached
        radius = q
        return RenderEffect.createBlurEffect(q, q, android.graphics.Shader.TileMode.CLAMP)
            .also { effect = it }
    }
}

// ---------------- 系统栏图标明暗 ----------------

@Composable
actual fun PuiSystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    SideEffect {
        // Compose 的 LocalView.context 常常是 ContextThemeWrapper，不能直接 as Activity
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
