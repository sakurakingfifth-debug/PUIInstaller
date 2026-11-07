package com.panl.installer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        content = content
    )
}
