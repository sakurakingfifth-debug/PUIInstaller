package com.panl.installer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.panl.installer.core.Settings
import com.panl.pui.PuiDarkColors
import com.panl.pui.PuiLightColors
import com.panl.pui.PuiTheme

/**
 * 应用主题：把设置里的外观选项接到 PUI 主题上。
 *
 * 主题色有三种来源：
 *  - Default：PUI 自带的洋红，`accent = null` 走原色板（容器色是手调的，别覆盖）；
 *  - Wallpaper：取系统壁纸取色的 primary（Android 12+）；
 *  - Custom：设置里存的 ARGB。
 *
 * 换色时对 accent 做 320ms 补间，整套配色是渐变过去的，不是「啪」地换掉。
 */
@Composable
fun InstallerTheme(
    /**
     * 是否由主题统一给内容加挖孔避让。
     *
     * 浮窗（InstallActivity）要关掉它：那里内容外面还有一层**半透明遮罩**，
     * 遮罩必须铺满整个窗口（包括挖孔那条缝），否则那条缝没被压暗，
     * 和旁边的背景一比就是一条色差带。这种页面自己给卡片/页面加 [puiCutoutPadding]。
     */
    applyCutoutShift: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val dark = when (settings.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val mode = settings.themeAccentMode
    val wallpaperPrimary = if (mode == "Wallpaper") {
        remember(dark) {
            runCatching {
                if (dark) dynamicDarkColorScheme(context).primary
                else dynamicLightColorScheme(context).primary
            }.getOrNull()
        }
    } else {
        null
    }

    val defaultPrimary = if (dark) PuiDarkColors.primary else PuiLightColors.primary
    val target = when (mode) {
        "Wallpaper" -> wallpaperPrimary ?: defaultPrimary
        "Custom" -> Color(settings.themeAccentArgb)
        else -> defaultPrimary
    }

    val animatedAccent by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 320),
        label = "theme-accent"
    )

    PuiTheme(
        darkTheme = dark,
        // 图标不再跟随壁纸取色：统一用 primary（自定义 ARGB / PUI 洋红）
        dynamicAccent = false,
        // Default 必须传 null：PUI 原生色板里的容器色是手调的，用算法重算会跑偏
        accent = if (mode == "Default") null else animatedAccent,
        content = { if (applyCutoutShift) PuiCutoutShift(content) else content() }
    )
}

/**
 * 挖孔避让：把整个界面往内侧挪开摄像头孔。
 *
 * 有些机器（比如 PadMini）挖孔在**短边**上，横屏时那个孔正好落在屏幕左/右边缘，
 * 而 targetSdk 35+ 强制 edge-to-edge，系统不再帮你把内容让开，界面就压在孔上了。
 *
 * 只取左右两侧的 inset：竖屏时挖孔在顶部，那本来就是状态栏的地盘，
 * 这里再让一次会和各页面的 `statusBarsPadding` 叠加，白白多出一条空白。
 * 没有挖孔 / 挖孔不在左右边时两个值都是 0，这层就等于不存在。
 */
@Composable
fun Modifier.puiCutoutPadding(): Modifier {
    val cutout = WindowInsets.displayCutout
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val left = with(density) { cutout.getLeft(density, layoutDirection).toDp() }
    val right = with(density) { cutout.getRight(density, layoutDirection).toDp() }
    if (left <= 0.dp && right <= 0.dp) return this

    // 内容最宽就 640dp（见 adaptiveWidth），窗口够宽时本来就居中排、离孔还远，
    // 这时候再按孔宽缩一下，整块内容就会**看起来往旁边偏**（用户报的就是这个）。
    // 所以只在「窗口窄到可能真的压到孔」时才让。
    val windowWidth = with(density) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    if (windowWidth - left - right >= 720.dp) return this

    return this.padding(
        start = if (left > 0.dp) left else 0.dp,
        end = if (right > 0.dp) right else 0.dp
    )
}

@Composable
private fun PuiCutoutShift(content: @Composable () -> Unit) {
    val cutout = WindowInsets.displayCutout
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val left = with(density) { cutout.getLeft(density, layoutDirection).toDp() }
    val right = with(density) { cutout.getRight(density, layoutDirection).toDp() }
    // 窗口尺寸变化（进/出分屏、拖拽改窗口大小）时做一次轻微淡入：
    // 系统那边是硬切，重排那一瞬会看到内容跳位、旧画面残影；压暗一点再淡回来就顺了，
    // 顺便把「尺寸刚变、模糊图层还采样着旧画面」的那一两帧盖过去。
    val windowSize = LocalWindowInfo.current.containerSize
    var alpha by remember { mutableStateOf(1f) }
    var lastSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(windowSize) {
        if (lastSize != IntSize.Zero && windowSize != lastSize && windowSize.width > 0) {
            alpha = 0.45f
            animate(
                initialValue = alpha,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            ) { value, _ -> alpha = value }
        }
        lastSize = windowSize
    }

    // 和 puiCutoutPadding 用同一套判断：窗口够宽时内容本来就居中排在安全区里，
    // 再缩一下只会让它看起来往一边偏。只有窄到可能压到孔时才让。
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val needShift = (left > 0.dp || right > 0.dp) && windowWidth - left - right < 720.dp

    Box(
        Modifier
            .fillMaxSize()
            .padding(
                start = if (needShift && left > 0.dp) left else 0.dp,
                end = if (needShift && right > 0.dp) right else 0.dp
            )
            .graphicsLayer { this.alpha = alpha }
    ) {
        content()
    }
}
