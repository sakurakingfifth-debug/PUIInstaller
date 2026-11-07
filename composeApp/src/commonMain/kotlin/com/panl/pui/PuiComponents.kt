package com.panl.pui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * PUI 原生控件集合。
 *
 * 这里刻意**不用** Material 的 Switch / Slider / Checkbox：
 * - M3 的开关是带图标的胶囊、滑块是「粗轨道 + 竖条把手 + 中间留缝」，都是 Expressive 的特征；
 * - PUI 的开关是细轨道 + 白圆钮，滑块是粗轨道 + 白色圆钮、已选段实心连续；
 * - 勾选是圆角小方块，不是 Material 的方形 checkbox。
 */

// ---------------- 开关 ----------------

@Composable
fun PuiSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val tick = rememberToggleHaptic(checked)
    val trackW = 44.dp
    val trackH = 26.dp
    val knob = 20.dp
    val inset = 3.dp

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> scheme.surfaceVariant.copy(alpha = 0.6f)
            checked -> scheme.primary
            else -> scheme.surfaceVariant
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "pui-switch-track"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) trackW - knob - inset else inset,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "pui-switch-thumb"
    )

    Box(
        modifier
            .size(trackW, trackH)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) {
                tick()
                onChange(!checked)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = thumbX)
                .size(knob)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.7f))
        )
    }
}

// ---------------- 滑块 ----------------

/**
 * 刻度触感步长（单位数）。
 *
 * 判据用「显示值 ÷ 步长」的整数商，不用浮点差阈值 —— 否则会在 43.9→46.1 漏震、
 * 46.1→46.3 连震两次。所有滑块的 range 都是正数，整数除法即向下取整。
 */
private const val SLIDER_HAPTIC_STEP = 3

/**
 * PUI 滑块：**粗轨道 + 白色圆钮**，已选段实心连续、并且往右包住圆钮。
 *
 * 规格来自原版 `PuiSlider`（源码而不是 README 里那句「轨道 4dp」）：
 *  - 轨道 24dp 胶囊、圆钮 16dp、触摸区 36dp；
 *  - 圆钮两端各留 4dp（= (轨道高 − 圆钮)/2），所以 0% 时已选段正好是一个
 *    24dp 的圆、与 16dp 圆钮同心，形成一圈 4dp 的环；100% 时同理靠右。
 *  - 已选段右端画到「圆钮中心 + 4dp」，把圆钮右半边包住，中段看不到接缝。
 *
 * @param segments > 0 时在轨道上画刻度点（不含两端）。
 * @param trackHeight 轨道高度，默认 24dp（`PuiSliderStyles.Pui`）。
 * @param thumbSize 圆钮直径，默认 16dp。小于轨道高度时两端会形成同心圆环（这是规范的样子）。
 */
@Composable
fun PuiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    segments: Int = 0,
    trackHeight: Dp = PuiSliderStyles.Pui.first,
    thumbSize: Dp = PuiSliderStyles.Pui.second
) {
    val scheme = MaterialTheme.colorScheme
    val activeColor = if (enabled) scheme.primary else scheme.outline.copy(alpha = 0.45f)
    val inactiveColor = if (enabled) scheme.surfaceVariant else scheme.surfaceVariant.copy(alpha = 0.5f)

    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    val tick = rememberTickHaptic()
    var lastHapticBucket by remember { mutableIntStateOf(value.roundToInt() / SLIDER_HAPTIC_STEP) }
    var pressed by remember { mutableStateOf(false) }
    var glowPos by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }

    val thumbScale by animateFloatAsState(
        targetValue = if (pressed) 1.10f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "pui-slider-thumb"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (touching) 0.34f else 0f,
        animationSpec = tween(durationMillis = if (touching) 140 else 260),
        label = "pui-slider-glow"
    )

    val density = LocalDensity.current
    val thumbPx = with(density) { thumbSize.toPx() }
    val trackPx = with(density) { trackHeight.toPx() }
    // 圆钮中心的端点位置：至少落在轨道半圆端头内，这样 0% 时环才是同心的。
    // 24dp 轨道 + 16dp 圆钮 → 12dp；细轨道 + 大圆钮 → 取圆钮半径（圆钮与端头齐平）。
    val endInsetPx = maxOf(trackPx / 2f, thumbPx / 2f)
    // Canvas 的 draw lambda 不是 @Composable，主题色必须提前取出来
    val segmentDotColor = scheme.outline.copy(alpha = 0.35f)
    fun valueAt(x: Float, width: Int): Float {
        val usable = (width - endInsetPx * 2).coerceAtLeast(1f)
        val f = ((x - endInsetPx) / usable).coerceIn(0f, 1f)
        return valueRange.start + f * span
    }

    Canvas(
        // 注意这里必须用调用方传进来的 modifier：之前写成 Modifier 重新起头，
        // 调用方加的 padding / weight 全部被丢掉（外面看着「改了没反应」就是这个原因）。
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                fun clampToTrack(p: Offset): Offset {
                    val half = 14.dp.toPx()
                    val cy = size.height / 2f
                    return Offset(
                        p.x.coerceIn(0f, size.width.toFloat()),
                        p.y.coerceIn(cy - half, cy + half)
                    )
                }
                detectTapGestures(
                    onPress = { pos ->
                        pressed = true
                        touching = true
                        glowPos = clampToTrack(pos)
                        tryAwaitRelease()
                        pressed = false
                        touching = false
                    },
                    onTap = { pos ->
                        glowPos = clampToTrack(pos)
                        onValueChange(valueAt(pos.x, size.width))
                    }
                )
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                fun clampToTrack(p: Offset): Offset {
                    val half = 14.dp.toPx()
                    val cy = size.height / 2f
                    return Offset(
                        p.x.coerceIn(0f, size.width.toFloat()),
                        p.y.coerceIn(cy - half, cy + half)
                    )
                }
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        pressed = true
                        touching = true
                        glowPos = clampToTrack(pos)
                        lastHapticBucket = valueAt(pos.x, size.width).roundToInt() / SLIDER_HAPTIC_STEP
                    },
                    onDragEnd = { pressed = false; touching = false },
                    onDragCancel = { pressed = false; touching = false }
                ) { change, _ ->
                    glowPos = clampToTrack(change.position)
                    val v = valueAt(change.position.x, size.width)
                    val bucket = v.roundToInt() / SLIDER_HAPTIC_STEP
                    if (bucket != lastHapticBucket) {
                        lastHapticBucket = bucket
                        tick()
                    }
                    onValueChange(v)
                }
            }
    ) {
        val trackH = trackHeight.toPx()
        val cy = size.height / 2f
        val baseThumbR = thumbPx / 2f
        val thumbR = baseThumbR * thumbScale
        // 圆钮中心的端点位置：取「轨道半径」与「圆钮半径」的较大者。
        //  - 24dp 轨道 + 16dp 圆钮 → 12dp：0% 时已选段（宽 24dp）正好是以 12dp 为心的圆，
        //    与圆钮同心，四周各留 4dp 的环 —— 左右边距相等，不会再一边宽一边窄。
        //  - 细轨道 + 大圆钮 → 圆钮半径：圆钮与轨道端头齐平。
        val endInset = maxOf(trackH / 2f, baseThumbR)
        val left = endInset
        val right = size.width - endInset
        val usable = (right - left).coerceAtLeast(1f)
        val thumbX = left + usable * fraction
        val radius = CornerRadius(trackH / 2f, trackH / 2f)
        val trackTop = cy - trackH / 2f

        // 底轨
        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackH),
            cornerRadius = radius
        )
        // 已选段：左端到轨道头，右端画到「圆钮中心 + endInset」——
        // 也就是把圆钮右半边包住（24/16 时正好是 4dp），中段看不到接缝。
        val activeW = (thumbX + endInset).coerceIn(trackH, size.width)
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, trackTop),
            size = Size(activeW, trackH),
            cornerRadius = radius
        )

        // 刻度点
        if (segments > 0) {
            val dotR = 1.6.dp.toPx()
            for (i in 1..segments) {
                val x = left + usable * (i.toFloat() / (segments + 1))
                drawCircle(
                    color = if (x < thumbX) Color.White.copy(alpha = 0.75f) else segmentDotColor,
                    radius = dotR,
                    center = Offset(x, cy)
                )
            }
        }

        // 触点光效：裁剪在轨道胶囊内，绝不溢出
        if (glowAlpha > 0.01f) {
            val clip = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(0f, trackTop, size.width, trackTop + trackH),
                        radiusX = trackH / 2f,
                        radiusY = trackH / 2f
                    )
                )
            }
            clipPath(clip) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = glowAlpha),
                            Color.White.copy(alpha = glowAlpha * 0.4f),
                            Color.White.copy(alpha = 0f)
                        ),
                        center = glowPos,
                        radius = 56.dp.toPx()
                    ),
                    radius = 56.dp.toPx(),
                    center = glowPos
                )
            }
        }

        // 圆钮：纯白实心，不加投影 —— 原版就是这样，而且它始终压在轨道上，
        // 靠「白 vs 彩色轨道」的对比就够清楚，加阴影反而显脏。
        drawCircle(if (enabled) Color.White else Color.White.copy(alpha = 0.7f), thumbR, Offset(thumbX, cy))
    }
}

/**
 * 滑块的两种既定规格（轨道高 to 圆钮直径）。
 *
 * - [Pui]：原版规格，24dp 粗轨道 + 16dp 圆钮，0% / 100% 时形成一圈 4dp 的同心圆环；
 * - [Thin]：备选的细轨道（8dp + 22dp），圆钮比轨道大，两端与轨道端头齐平。
 */
object PuiSliderStyles {
    val Pui: Pair<Dp, Dp> = 24.dp to 16.dp
    val Thin: Pair<Dp, Dp> = 8.dp to 22.dp
}

// ---------------- 分段控件 ----------------

/**
 * 胶囊分段控件：外框浅底，选中项是一块浮起的白胶囊。
 *
 * 和 M3 的 SegmentedButton 的区别是**没有分隔线、没有边框**，靠底色差区分。
 */
@Composable
fun PuiSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * 手指拖动时的连续位置（0f..items.lastIndex）。传了它就用它定位选中胶囊，
     * 松手后传回 null，胶囊会自己弹到 [selectedIndex]。用来做「跟手滑动」的分段控件。
     */
    dragFraction: Float? = null
) {
    val scheme = MaterialTheme.colorScheme
    val pad = 3.dp
    // 切换选项时给一次刻度触感（和滑块刻度同一套 CLOCK_TICK 波形）
    val tick = rememberTickHaptic()

    // 选中胶囊是**一块独立的浮层**，切换时在轨道上滑过去（而不是每格各淡入淡出一个背景）。
    // 各格等宽，所以直接用 BoxWithConstraints 的 maxWidth / 格数算位置，不用 SubcomposeLayout。
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerHighest)
    ) {
        val itemW = (maxWidth - pad * 2) / items.size
        // 用 Animatable 而不是 animateDpAsState：松手时要从手指停的位置接着弹，
        // animateDpAsState 会先跳回旧位置再动画，看着就是「闪一下」
        val pos = remember { Animatable(selectedIndex.toFloat()) }
        LaunchedEffect(dragFraction, selectedIndex) {
            if (dragFraction != null) {
                pos.snapTo(dragFraction)          // 拖动中：严格跟手，不做插值
            } else {
                pos.animateTo(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 480f)
                )
            }
        }
        val shownOffset = itemW * pos.value

        Box(
            Modifier
                .offset(x = pad + shownOffset, y = pad)
                .width(itemW)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(scheme.surfaceContainerLowest)
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(pad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val fg by animateColorAsState(
                    when {
                        !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                        selected -> scheme.onSurface
                        else -> scheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 180),
                    label = "pui-seg-fg"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled
                        ) {
                            // 点当前已选中的那格不震，只有真的切换才给反馈
                            if (index != selectedIndex) tick()
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = fg,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ---------------- 标签 / 徽标 ----------------

@Composable
fun PuiChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: PuiIcon? = null,
    onClick: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
        label = "pui-chip-bg"
    )
    val fg = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Row(
        modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() } else Modifier
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) PuiIconView(icon, Modifier.size(16.dp), tint = fg)
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@Composable
fun PuiBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError
) {
    Box(
        modifier
            .height(18.dp)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

// ---------------- 按钮 ----------------

enum class PuiButtonStyle { Filled, Tonal, Ghost, Text, Danger }

/**
 * PUI 按钮：统一 46dp 高、16dp 圆角，靠底色区分层级。
 *
 * - [PuiButtonStyle.Filled] 主色实心，一屏最多一个；
 * - [PuiButtonStyle.Tonal] 主色浅底，次级动作；
 * - [PuiButtonStyle.Ghost] 灰底无边框（原 `PuiGhostButton`）；
 * - [PuiButtonStyle.Text] 纯文字；
 * - [PuiButtonStyle.Danger] 实心错误色，删除类操作。**用实心而不是浅底**：
 *   洋红主题下 `errorContainer` 和 tonal 的浅粉几乎同色，一眼分不出主次。
 */
@Composable
fun PuiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PuiButtonStyle = PuiButtonStyle.Filled,
    icon: PuiIcon? = null,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val container = when (style) {
        PuiButtonStyle.Filled -> scheme.primary
        PuiButtonStyle.Tonal -> scheme.primaryContainer
        PuiButtonStyle.Ghost -> scheme.surfaceContainerHighest
        PuiButtonStyle.Text -> Color.Transparent
        PuiButtonStyle.Danger -> scheme.error
    }
    val content = when (style) {
        PuiButtonStyle.Filled -> scheme.onPrimary
        PuiButtonStyle.Tonal -> scheme.onPrimaryContainer
        PuiButtonStyle.Ghost -> scheme.onSurfaceVariant
        PuiButtonStyle.Text -> scheme.primary
        PuiButtonStyle.Danger -> scheme.onError
    }
    val dim = if (enabled) 1f else 0.45f

    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) { onClick() }
            .padding(horizontal = 18.dp)
            .alpha(dim),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            PuiIconView(icon, Modifier.size(18.dp), tint = content)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

/** 圆形图标按钮（36dp / 44dp 两档）。 */
@Composable
fun PuiIconButton(
    icon: PuiIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tint: Color = LocalWallpaperAccent.current.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.primary
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        PuiIconView(icon, Modifier.size(size * 0.5f), tint = tint)
    }
}

// ---------------- 输入框 ----------------

/**
 * 圆角填充输入框：`surfaceContainerHighest` 底、16dp 圆角、48dp 高。
 * 没有下划线、没有描边 —— PUI 的输入框靠底色和页面区分。
 */
@Composable
fun PuiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: PuiIcon? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            PuiIconView(
                leadingIcon,
                Modifier.size(20.dp),
                tint = scheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 胶囊搜索框（24dp 圆角），常用于页面顶部。 */
@Composable
fun PuiSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = PuiLocale.search
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuiIconView(PuiIcon.Search, Modifier.size(19.dp), tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------- 进度 / 勾选 ----------------

@Composable
fun PuiProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val scheme = MaterialTheme.colorScheme
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "pui-progress")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val r = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(scheme.surfaceVariant, size = size, cornerRadius = r)
        if (animated > 0f) {
            drawRoundRect(
                color,
                size = Size(size.width * animated, size.height),
                cornerRadius = r
            )
        }
    }
}

/** 圆角方形勾选框，22dp。 */
@Composable
fun PuiCheckbox(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (checked) scheme.primary else Color.Transparent,
        label = "pui-check-bg"
    )
    Box(
        modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .then(
                if (checked) Modifier else Modifier.border(
                    1.6.dp,
                    if (enabled) scheme.outline.copy(alpha = 0.55f) else scheme.outline.copy(alpha = 0.25f),
                    RoundedCornerShape(7.dp)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) { onChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(Modifier.size(14.dp)) {
                drawPath(
                    Path().apply {
                        moveTo(size.width * 0.14f, size.height * 0.52f)
                        lineTo(size.width * 0.42f, size.height * 0.78f)
                        lineTo(size.width * 0.88f, size.height * 0.24f)
                    },
                    Color.White,
                    style = Stroke(
                        width = size.width * 0.17f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/** 单选圆点，22dp。 */
@Composable
fun PuiRadioDot(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(
        if (selected) scheme.primary else scheme.outline.copy(alpha = 0.55f),
        label = "pui-radio-ring"
    )
    val dotScale by animateFloatAsState(
        if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "pui-radio-dot"
    )
    Canvas(
        modifier
            .size(22.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled
            ) { onClick() }
    ) {
        val stroke = 1.8.dp.toPx()
        drawCircle(ring, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke))
        if (dotScale > 0.01f) {
            // 内点要比外环小一圈（留 3.5dp 空隙），否则看起来是一整个实心圆
            drawCircle(
                ring,
                radius = (size.minDimension / 2f - stroke - 3.5.dp.toPx()) * dotScale
            )
        }
    }
}

/** 头像：首字母 + 主色浅底。 */
@Composable
fun PuiAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

// ---------------- 搜索框 ----------------

/**
 * PUI 搜索框：全宽胶囊，聚焦时输入框收窄、右侧滑出「取消」。
 *
 * 复刻自 ColorOS 设置页的搜索条：
 *  - 静息：全宽，左侧放大镜 + 占位文字；
 *  - 聚焦：输入框让出右侧空间，「取消」胶囊横向展开 + 淡入；
 *  - 有内容：框内右侧出现清除按钮；
 *  - 点取消 / 键盘完成：清空、收键盘、失焦，输入框自己涨回去。
 *
 * @param query 当前文本
 * @param onQueryChange 文本变化
 * @param placeholder 占位文字
 * @param onSearch 键盘「搜索」键回调（可选）
 */
@Composable
fun PuiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = PuiLocale.searchApps,
    onSearch: ((String) -> Unit)? = null,
    /** 出图工具用：进页面就带着一段「拖动位移」，方便截果冻拉伸的样子。 */
    initialPull: Offset = Offset.Zero
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    // 暗色 #252525（和纯黑底拉开一点）；亮色纯白 #FFFFFF
    val fill = if (dark) Color(0xFF252525) else Color(0xFFFFFFFF)
    val hint = scheme.onSurfaceVariant.copy(alpha = 0.72f)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = scheme.onSurface,
        fontWeight = FontWeight.Medium
    )

    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 「取消」剥离进度：0 = 完全贴合在搜索栏里，1 = 独立成胶囊
    val p by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "search-cancel-split"
    )

    // 果冻：拖到哪就沿哪个方向拉伸、垂直方向压扁，松手用带阻尼的弹簧弹回去。
    // 用 Offset 而不是单个 Float —— 只记横向位移的话，往下拖是完全没反馈的。
    // 手势块是 restricted suspend 作用域，不能直接调 Animatable，所以用外部 scope。
    val pull = remember { Animatable(initialPull, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    // 触碰光：跟着手指走的一团柔光
    var glowPos by remember { mutableStateOf(Offset.Zero) }
    var pressing by remember { mutableStateOf(false) }
    val glowAlpha by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressing) 100 else 300),
        label = "search-glow"
    )
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun close() {
        onQueryChange("")
        focusManager.clearFocus()
        keyboard?.hide()
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .graphicsLayer {
                    // 横向纵向归一到同一个「拉满距离」，两个方向的手感才一致
                    val maxPull = 96.dp.toPx()
                    val v = pull.value
                    val kx = (abs(v.x) / maxPull).coerceIn(0f, 1f)
                    val ky = (abs(v.y) / maxPull).coerceIn(0f, 1f)
                    // 沿拖动轴拉伸、垂直轴压扁；斜着拖就是两个方向各来一点
                    scaleX = 1f + kx * 0.11f - ky * 0.07f
                    scaleY = 1f + ky * 0.11f - kx * 0.07f
                }
                .clip(CircleShape)
                .background(fill)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        glowPos = down.position
                        pressing = true
                        var dx = 0f
                        var dy = 0f
                        var dragging = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            glowPos = change.position
                            if (!change.pressed) break

                            // 直接用 position - previousPosition：输入框自己也会消费事件
                            // （点按、选词），而 positionChange() 在被消费后返回 0，果冻就完全不动了
                            val delta = change.position - change.previousPosition
                            dx += delta.x
                            dy += delta.y
                            // 过了触摸斜率才算拖：轻点仍然交给 clickable 去抢焦点
                            if (!dragging && dx * dx + dy * dy > viewConfiguration.touchSlop * viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (dragging) {
                                // 吃掉事件：外层列表就不会把这次拖动当成滚动。
                                // 必须在这里消费 —— 内层在 Main 阶段先于外层 scrollable 拿到事件。
                                change.consume()
                                scope.launch { pull.snapTo(Offset(dx, dy)) }
                            }
                        }
                        pressing = false
                        // dampingRatio 小于 1 才会有回弹，这就是「果冻」的来源
                        scope.launch {
                            pull.animateTo(Offset.Zero, spring(dampingRatio = 0.42f, stiffness = 380f))
                        }
                    }
                }
                .drawWithContent {
                    // 光画在底色之上、内容之下，不会糊住文字
                    if (glowAlpha > 0.01f) {
                        val tint = if (dark) Color.White else Color.Black
                        val r = 120.dp.toPx()
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    tint.copy(alpha = glowAlpha * if (dark) 0.16f else 0.09f),
                                    tint.copy(alpha = 0f)
                                ),
                                center = glowPos,
                                radius = r
                            ),
                            radius = r,
                            center = glowPos
                        )
                    }
                    drawContent()
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PuiIconView(PuiIcon.Search, Modifier.size(20.dp), tint = hint)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        placeholder,
                        style = textStyle,
                        color = hint,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearch?.invoke(query)
                        keyboard?.hide()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                )
            }
            // 有内容才显示清除按钮
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurfaceVariant.copy(alpha = 0.22f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    PuiIconView(
                        PuiIcon.Close,
                        Modifier.size(10.dp),
                        tint = if (dark) Color.Black.copy(alpha = 0.75f) else Color.White
                    )
                }
            }
        }

        // 取消：像气泡一样从搜索栏右端「剥离」出来。
        //
        // 拆开看是三个同时进行的量（都由同一个 p 驱动）：
        //  1. 间距 0 → 10dp：p 小的时候两个胶囊是**贴着**的，视觉上像一个花生，中间那段就是「粘连的颈」；
        //  2. 取消的布局宽度 0 → cancelW：输入框靠 weight 自动被挤窄，两者一起完成「分裂」；
        //  3. 取消自身从 0.5 倍缩放 + 透明长到原尺寸，被父级裁掉的部分自然形成「刚冒出来」的形状。
        // 弹簧的 dampingRatio < 1，所以分裂完会轻轻回弹一下。
        val cancelW = 84.dp
        val gap = 10.dp
        if (p > 0.001f) {
            Spacer(Modifier.width(gap * p))
            Box(
                Modifier
                    .width(cancelW * p)
                    .height(44.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    Modifier
                        // requiredWidth：不受父级 0→84dp 的约束影响，否则文字会被挤成竖排
                        .requiredWidth(cancelW)
                        .height(44.dp)
                        .graphicsLayer {
                            // 以右边缘为原点缩放：右边缘固定在页面右边距，左边缘往左「鼓」出去，
                            // 和搜索栏同色重叠时视觉上连成一片，这就是剥离前那一瞬的「粘连」。
                            val sc = 0.45f + 0.55f * p
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            scaleX = sc
                            scaleY = sc
                            // 早期就基本不透明，才能和搜索栏「融为一体」
                            alpha = (p * 3f).coerceIn(0f, 1f)
                        }
                        .clip(CircleShape)
                        .background(fill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { close() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        PuiLocale.cancel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                }
            }
        }
    }
}
