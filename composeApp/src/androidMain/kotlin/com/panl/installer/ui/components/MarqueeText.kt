package com.panl.installer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 一行文本：放不下就轮播，并且两端做渐隐，不让文字在边缘被一刀切断。
 *
 * 只有真的超出宽度时才加渐隐层——短文本（`x.apk`）不该被压暗两头。
 * 渐隐用 DstIn 合成实现：先画文字，再用一条「两端透明、中间不透明」的黑条
 * 乘掉 alpha。两个必须注意的点：
 *  1. 这层要套 `CompositingStrategy.Offscreen` 的 graphicsLayer，否则 DstIn 会把
 *     背后的卡片背景一起啃成透明；
 *  2. 这层必须排在 `basicMarquee` **外层**，排在里层会跟着文字一起位移，
 *     看起来就是「渐变追着字跑」。
 *
 * @param alignment 文本没溢出时的摆放位置（居中/靠右的标题、右对齐的版本号都要用）
 * @param fadeWidth 两端渐隐的宽度
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterStart,
    fadeWidth: Dp = 18.dp
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        val widthPx = constraints.maxWidth
        val overflows = remember(text, style, widthPx) {
            widthPx > 0 && measurer.measure(
                text = AnnotatedString(text),
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            ).size.width > widthPx
        }
        val fadePx = with(LocalDensity.current) { fadeWidth.toPx() }

        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .then(
                    if (!overflows) {
                        Modifier
                    } else {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val f = (fadePx / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.4f)
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        f to Color.Black,
                                        1f - f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    }
                )
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 1200,
                    repeatDelayMillis = 1600,
                    spacing = MarqueeSpacing(48.dp)
                )
        )
    }
}
