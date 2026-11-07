package com.panl.pui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

/*
 * PUI 设计系统的主题层（Compose Multiplatform 版）。
 *
 * 视觉基调：浅灰底 + 纯白卡片 + **Viva Magenta（非凡洋红）**主色，
 * 圆角克制（卡片 20dp）、字阶克制，接近 ColorOS 原生设置页，
 * 而不是 Material 3 Expressive 那种「大字大圆角」。
 *
 * 主色取自 PANTONE 18-1750 Viva Magenta（#BE3455）。
 *
 * 与原 Android 版（com.panl.pui）的区别只有一处：
 * 壁纸动态取色（dynamicLight/DarkColorScheme）依赖 Android 12+ 的 API，
 * 这里抽成 expect 的 [rememberWallpaperAccent]，Android 侧走真实现，桌面侧退回主色。
 */

/**
 * 壁纸取色（系统动态主色）。
 *
 * 主题本身用固定的洋红调色板，但 [PuiIconView] 的图标要「跟随壁纸颜色」，
 * 所以单独把动态主色透出来。系统不支持动态取色（API < 31）或调用方关掉时退回
 * [Color.Unspecified]，组件内部会用 `colorScheme.primary` 兜底。
 */
val LocalWallpaperAccent = staticCompositionLocalOf { Color.Unspecified }

// ---------------- 字阶 ----------------

/**
 * PUI 字阶：标题比 Material 3 默认更克制，行高略紧。
 * 只覆盖实际用到的档位，其余沿用 Material 3 默认值。
 */
val PuiTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp)
)

// ---------------- 形状 ----------------

/**
 * PUI 形状：卡片圆角 **20dp**（`large`），比 M3 Expressive 的 28dp 小一档。
 * 组件里凡是 `MaterialTheme.shapes.large` 的地方（卡片、提示卡）都走这一档，
 * 改这一个值等于改所有卡片。
 */
val PuiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ---------------- 配色 ----------------

/**
 * PUI 品牌色：**Viva Magenta（非凡洋红）**，PANTONE 18-1750，`#BE3455`。
 *
 * 主色从原来的 OPPO 绿换成这套洋红之后，容器色（primaryContainer / secondary / tertiary）
 * 也一并挪到同一色系：主容器是浅粉，次级是低饱和的藕紫灰，三级用暖棕作补色。
 */
val PuiMagenta = Color(0xFFBE3455)

/** 亮色配色：浅灰背景 `#F0F1F3`（中性偏冷），卡片纯白 `#FFFFFF`（`surfaceContainer*` 全为 `#FFFFFF`）。 */
val PuiLightColors: ColorScheme = lightColorScheme(
    primary = PuiMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = Color(0xFF3F0016),
    secondary = Color(0xFF75565D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E0),
    onSecondaryContainer = Color(0xFF2B151A),
    tertiary = Color(0xFF7A5735),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDBA),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF0F1F3),
    onBackground = Color(0xFF1C1B1C),
    surface = Color(0xFFF0F1F3),
    onSurface = Color(0xFF1C1B1C),
    surfaceVariant = Color(0xFFE9DDE0),
    onSurfaceVariant = Color(0xFF4C4346),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF0E7E9),
    outline = Color(0xFF7C7275),
    outlineVariant = Color(0xFFE3D6D9),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410E0B)
)

/** 暗色配色：背景纯黑，卡片 `#1A1A1A`。 */
val PuiDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB1C1),
    onPrimary = Color(0xFF66002A),
    primaryContainer = Color(0xFF8E1039),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Color(0xFFE4BDC4),
    onSecondary = Color(0xFF43292F),
    secondaryContainer = Color(0xFF5B3F45),
    onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = Color(0xFFECBE8E),
    onTertiary = Color(0xFF442A0A),
    tertiaryContainer = Color(0xFF5F401D),
    onTertiaryContainer = Color(0xFFFFDDBA),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E1E2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE6E1E2),
    surfaceVariant = Color(0xFF4C4346),
    onSurfaceVariant = Color(0xFFD0C4C7),
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF2A2426),
    outline = Color(0xFF9A8F92),
    outlineVariant = Color(0xFF4E4447),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * 用给定的品牌色生成一套配色，其余令牌（背景 / 卡片 / 文字 / 分隔线）保持不变。
 *
 * 这是给「换主题色」用的：PUI 的骨架是灰底白卡，只有 primary 系列会跟着品牌色走。
 */
fun ColorScheme.puiWithAccent(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.55f) Color(0xFF101613) else Color.White
    val container = if (dark) lerp(accent, Color.Black, 0.62f) else lerp(accent, Color.White, 0.80f)
    val onContainer = if (dark) lerp(accent, Color.White, 0.72f) else lerp(accent, Color.Black, 0.62f)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = lerp(accent, if (dark) Color.Black else Color.White, 0.45f)
    )
}

/**
 * 主题入口。
 *
 * ```kotlin
 * PuiTheme {
 *     // 你的界面
 * }
 * ```
 *
 * @param darkTheme 是否暗色，默认跟随系统。
 * @param dynamicAccent 是否用壁纸动态取色作为 [PuiIconView] 的图标色（Android 12+）。
 *   关掉则图标用 `colorScheme.primary`。注意这**只影响图标**，界面主色始终是 PUI 调色板。
 * @param accent 覆盖品牌色（`null` = 用 Viva Magenta）。只改 primary 系列，灰底白卡不变。
 */
@Composable
fun PuiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicAccent: Boolean = true,
    accent: Color? = null,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) PuiDarkColors else PuiLightColors
    val scheme = if (accent != null) base.puiWithAccent(accent, darkTheme) else base
    val wallpaperAccent = rememberWallpaperAccent(darkTheme, dynamicAccent, scheme.primary)

    // 状态栏 / 导航栏图标跟着 App 内的深浅色走，不跟系统
    PuiSystemBarsAppearance(darkTheme)

    CompositionLocalProvider(LocalWallpaperAccent provides wallpaperAccent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PuiTypography,
            shapes = PuiShapes,
            content = content
        )
    }
}

/**
 * 主题切换的「圆形展开」过渡。
 *
 * 用法：把整个界面包起来，`key` 传当前明暗（或任何想触发过渡的值）。
 *
 * ```kotlin
 * PuiTheme(darkTheme = dark) {
 *     PuiThemeReveal(key = dark) {
 *         AppContent()
 *     }
 * }
 * ```
 *
 * 实现要点：
 *  - 不复制两套内容树（那样两边的 remember 状态会各算各的，滚动位置、滑块值都会丢），
 *    而是**只保留一棵树**：换主题那一帧内容已经是新配色，再用一个从小到大的圆形裁剪把它「揭」出来，
 *    圆外的底色直接用新主题的 background，所以视觉上就是新界面从中间展开。
 *  - `remember(key)` 重建 Animatable 且初值为 0：换 key 的那一帧半径就已经是 0，
 *    不会先闪一下完整界面再重播。
 *  - `progress.value` 只在 `drawWithContent` 里读，属于 draw 阶段，
 *    动画每帧只重绘、不重组。
 */
@Composable
fun PuiThemeReveal(
    key: Any,
    modifier: Modifier = Modifier,
    /** 圆心（根坐标）。传 null 用屏幕中心；传入点击位置就从那儿展开。 */
    origin: Offset? = null,
    durationMillis: Int = 700,
    content: @Composable () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    var initialized by remember { mutableStateOf(false) }
    val progress = remember(key) { Animatable(if (initialized) 0f else 1f) }

    LaunchedEffect(key) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }

    Box(modifier.fillMaxSize().background(bg)) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val p = progress.value
                    if (p >= 1f) {
                        drawContent()
                    } else {
                        // 圆心可以是点击位置，所以半径要取到**最远那个角**的距离，
                        // 不能再用半对角线（那样偏心时会盖不满）。
                        val c = origin ?: center
                        val maxR = maxOf(
                            hypot(c.x, c.y),
                            hypot(size.width - c.x, c.y),
                            hypot(c.x, size.height - c.y),
                            hypot(size.width - c.x, size.height - c.y)
                        )
                        val circle = Path().apply { addOval(Rect(c, maxR * p)) }
                        clipPath(circle) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
        ) {
            content()
        }
    }
}
