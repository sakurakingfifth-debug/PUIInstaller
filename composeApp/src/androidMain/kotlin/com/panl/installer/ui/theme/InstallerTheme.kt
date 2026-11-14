package com.panl.installer.ui.theme

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
fun InstallerTheme(content: @Composable () -> Unit) {
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
        content = { PuiCutoutShift(content) }
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
private fun PuiCutoutShift(content: @Composable () -> Unit) {
    val cutout = WindowInsets.displayCutout
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val left = with(density) { cutout.getLeft(density, layoutDirection).toDp() }
    val right = with(density) { cutout.getRight(density, layoutDirection).toDp() }
    Box(
        Modifier
            .fillMaxSize()
            .padding(start = if (left > 0.dp) left else 0.dp, end = if (right > 0.dp) right else 0.dp)
    ) {
        content()
    }
}
