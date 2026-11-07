package com.panl.pui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * PUI 的卡片家族。
 *
 * PUI 的卡片只有一种「底色 + 圆角」语言：白卡（暗色 #1A1A1A）、20dp 圆角、零阴影。
 * 层级不靠 elevation，靠**底色差**和**留白**：
 *   页面底 #F6F8F7 → 卡片 #FFFFFF → 卡内再分区用 #E9EEE9。
 * 所以这里没有任何 shadow 参数，想强调就用 primaryContainer 铺底。
 */

/** 最基础的白卡。内容自己控制内边距。 */
@Composable
fun PuiCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        // Card 自己就按 shape 裁剪了，外面再包一层 clip 只会多一个图层
        modifier = modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

/** 描边卡：需要和背景拉开一点点时用，边框 1dp `outlineVariant`。 */
@Composable
fun PuiOutlinedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.large),
        content = content
    )
}

enum class PuiNoticeTone { Info, Success, Warning, Danger }

/**
 * 提示卡：图标 + 标题 + 说明，浅色底。
 *
 * 用在页面顶部做「这个开关会影响什么」的解释，比 Dialog 轻。
 */
@Composable
fun PuiNoticeCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    icon: PuiIcon = PuiIcon.Info,
    /** 自定义图标（矢量 / 图片），传了就不画 [icon]。 */
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    tone: PuiNoticeTone = PuiNoticeTone.Info,
    action: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (tone) {
        PuiNoticeTone.Info -> scheme.primaryContainer.copy(alpha = 0.55f)
        PuiNoticeTone.Success -> scheme.primaryContainer
        PuiNoticeTone.Warning -> scheme.tertiaryContainer.copy(alpha = 0.7f)
        PuiNoticeTone.Danger -> scheme.errorContainer
    }
    val onContainer = when (tone) {
        PuiNoticeTone.Info -> scheme.onPrimaryContainer
        PuiNoticeTone.Success -> scheme.onPrimaryContainer
        PuiNoticeTone.Warning -> scheme.onTertiaryContainer
        PuiNoticeTone.Danger -> scheme.onErrorContainer
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (iconPainter != null) {
            androidx.compose.foundation.Image(
                painter = iconPainter,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(onContainer)
            )
        } else {
            PuiIconView(icon, Modifier.size(20.dp), tint = onContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = onContainer,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.82f)
            )
            if (action != null) {
                Spacer(Modifier.height(10.dp))
                action()
            }
        }
    }
}

/** 数据卡：一个大数字 + 标签 + 可选图标。 */
@Composable
fun PuiStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    caption: String = "",
    icon: PuiIcon? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLowest)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (icon != null) {
                PuiIconView(icon, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge,
                color = scheme.onSurface
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        if (caption.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/** 应用卡：图标 + 名称 + 描述 + 箭头，一行一个 App 的列表项。 */
@Composable
fun PuiAppCard(
    icon: PuiIcon,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingText: String? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLowest)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() } else Modifier
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            PuiIconView(icon, Modifier.size(24.dp), tint = scheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary
            )
        } else if (onClick != null) {
            PuiIconView(
                PuiIcon.ChevronRight,
                Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant
            )
        }
    }
}

/** 强调卡：主色渐变铺底 + 白字，一屏最多一张，用于「当前主题」这类概览。 */
@Composable
fun PuiHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    icon: PuiIcon? = null,
    /** 自定义图标（矢量 / 图片），传了就不画 [icon]。 */
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    // 渐变方向跟着明暗走，保证 onPrimary 文字的对比度：
    //  - 亮色：primary 是深洋红，往 onPrimaryContainer（更深的酒红）走；
    //  - 暗色：primary 是浅粉，往 primaryContainer（深玫红）走。
    // 早先统一往 primaryContainer 靠，亮色下末端会淡成浅粉，白字就糊了。
    val gradientEnd = if (scheme.primary.luminance() > 0.5f) {
        lerp(scheme.primary, scheme.primaryContainer, 0.55f)
    } else {
        lerp(scheme.primary, scheme.onPrimaryContainer, 0.22f)
    }
    // 触碰光：手指按住时有一团柔光跟着走，松开淡出
    var glowPos by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    val glowAlpha by animateFloatAsState(
        targetValue = if (touching) 0.26f else 0f,
        animationSpec = tween(durationMillis = if (touching) 120 else 320),
        label = "hero-glow"
    )

    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(scheme.primary, gradientEnd)))
            // 不消费事件，只记录位置：卡片本身可能还有 clickable
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        glowPos = event.changes.first().position
                        touching = event.changes.any { it.pressed }
                    }
                }
            }
            .drawWithContent {
                drawContent()
                if (glowAlpha > 0.01f) {
                    val r = 190.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = glowAlpha),
                                Color.White.copy(alpha = glowAlpha * 0.45f),
                                Color.White.copy(alpha = 0f)
                            ),
                            center = glowPos,
                            radius = r
                        ),
                        radius = r,
                        center = glowPos
                    )
                }
            }
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() } else Modifier
            )
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = scheme.onPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (iconPainter != null) {
                    androidx.compose.foundation.Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            scheme.onPrimary.copy(alpha = 0.9f)
                        )
                    )
                } else if (icon != null) {
                    PuiIconView(icon, Modifier.size(26.dp), tint = scheme.onPrimary.copy(alpha = 0.9f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimary.copy(alpha = 0.82f)
            )
            if (badge != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(scheme.onPrimary.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimary
                    )
                }
            }
        }
    }
}

/** 两列卡片网格里的一格（等宽，由调用方用 Row + weight 组合）。 */
@Composable
fun PuiMiniCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: PuiIcon? = null,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLowest)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() } else Modifier
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            PuiIconView(icon, Modifier.size(22.dp), tint = scheme.primary)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}
