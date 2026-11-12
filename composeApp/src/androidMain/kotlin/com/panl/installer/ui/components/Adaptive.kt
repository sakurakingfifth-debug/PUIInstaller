package com.panl.installer.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 大屏（Tier 2 自适应）用的宽度：手机上照旧铺满，平板 / 折叠屏展开 / 桌面窗口里收窄居中。
 *
 * 依据自适应质量指南 —— 「不要拉伸界面元素去填满多余空间」、「不要把面板或工作表设为全宽」。
 *
 * 注意顺序：`widthIn` 必须在 `fillMaxWidth` 之前。
 * 反过来写（先 fill 再 widthIn）外层已经把宽度钉死，内层收不回来。
 */
@Composable
fun Modifier.adaptiveWidth(max: Dp = 640.dp): Modifier =
    this.widthIn(max = max).fillMaxWidth()

/** 安装页正文的推荐上限：单列信息超过 ~640dp 之后每行都是大片留白，反而更难读。 */
val InstallContentMaxWidth: Dp = 640.dp

/**
 * 浮窗（确认卡）的宽度。
 *
 * < 840dp（手机竖屏、小折叠）：保持原来的通栏底卡，不改观感；
 * >= 840dp（平板、展开的大折叠、桌面窗口）：收到 520dp 居中，避免整张卡横贯全屏。
 */
@Composable
fun Modifier.sheetWidth(): Modifier {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val max = if (screenWidthDp >= 840) 520.dp else 640.dp
    return this.widthIn(max = max).fillMaxWidth()
}
