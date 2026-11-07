package com.panl.pui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * 平台差异层（expect）。
 *
 * PUI 有 4 件事是平台相关的，其余全部是纯 Compose：
 *   1. 壁纸动态取色 —— Android 12+ 才有，桌面退回主色；
 *   2. 触感 —— Android 用 performHapticFeedback，桌面空实现；
 *   3. 状态栏 / 导航栏高度 —— 决定页面顶部留白与导航栏避让；
 *   4. 顶部渐进模糊 —— Android 用 AGSL RuntimeShader，桌面直接不加效果。
 *
 * 加新平台（iOS / Web）时只要补一份 actual，commonMain 一行都不用改。
 */

/** 壁纸动态主色；不支持时返回 [fallback]。 */
@Composable
expect fun rememberWallpaperAccent(
    darkTheme: Boolean,
    dynamicAccent: Boolean,
    fallback: Color
): Color

/** 「刻度」触感：滑块每跨一格用一次。 */
@Composable
expect fun rememberTickHaptic(): () -> Unit

/** 开关切换触感；[checked] 是切换**前**的状态（决定开 / 关的波形）。 */
@Composable
expect fun rememberToggleHaptic(checked: Boolean): () -> Unit

/** 状态栏高度（刘海 / 挖孔机型的真实高度）。 */
@Composable
expect fun rememberStatusBarHeight(): Dp

/** 底部手势条 / 导航栏高度。 */
@Composable
expect fun rememberNavigationBarHeight(): Dp

/**
 * 当前平台能不能做「NavBar 区域的系统高斯模糊」。
 *
 * Android 需要 13+（`RenderEffect.createBlurEffect` + `RuntimeShader`），
 * 桌面恒为 false —— 那时 [PuiFloatingNavBar] 的玻璃填色会自动调高到接近不透明。
 */
expect val PuiPlatformSupportsBlur: Boolean

/**
 * 顶部渐进模糊：越靠屏幕顶端越糊，向下逐渐清晰。
 *
 * 传入 [backdrop] 时，还会在 NavBar 所在区域叠一层「原生高斯模糊 + 跟随变形胶囊蒙版」，
 * 也就是悬浮栏的毛玻璃。顺序不能反：`navBackdropSource` 要在本函数之前。
 *
 * Android 侧用 AGSL 逐像素采样（API 33+，低于该版本自动退化为无效果）；
 * 桌面侧不加效果，靠 [PuiTopFade] 的渐隐遮罩近似。
 */
@Composable
expect fun Modifier.puiProgressiveTopBlur(
    height: Dp,
    maxRadius: Dp = 28.dp,
    backdrop: NavBackdrop? = null
): Modifier

/**
 * 实时模糊：半径 = `maxRadius * progress()`，跟着动画进度走。
 *
 * 和 [puiProgressiveTopBlur] 的区别是「没有形状」—— 整层一起糊，用来把**背景**推到景深里，
 * 比如图标集展开时让底下的页面虚掉，避免半透明的新页面把底下的文字透出来。
 *
 * [progress] 在绘制阶段读取（`graphicsLayer` 的 block 里），**不会引起重组**，
 * 所以可以每帧改半径而不掉帧。progress 回到 0 或接近 1（整层已经被盖住）时自动摘掉效果，
 * 省掉「面板开着时还在给看不见的背景做高斯」那份无谓开销。
 *
 * Android 侧用 `RenderEffect`（API 31+，低于该版本自动退化为无效果）；桌面侧不加效果。
 */
@Composable
expect fun Modifier.puiRealtimeBlur(progress: () -> Float, maxRadius: Dp = 24.dp): Modifier

/** 让状态栏 / 导航栏图标跟随 App 内的深浅色（桌面无系统栏，空实现）。 */@Composable
expect fun PuiSystemBarsAppearance(darkTheme: Boolean)
