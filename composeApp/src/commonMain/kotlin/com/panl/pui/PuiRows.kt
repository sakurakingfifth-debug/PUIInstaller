package com.panl.pui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * PUI 设置页的「页面骨架」组件。
 *
 * 一套典型用法：
 *
 * ```kotlin
 * Column(Modifier.padding(horizontal = 16.dp)) {
 *     PuiPageTitle("任务栏客制化")
 *     Spacer(Modifier.height(12.dp))
 *     PuiGroup("尺寸") {
 *         PuiSwitchRow(PuiIcon.Height, "自定义尺寸", "开启后可调高度", on, { on = it }, showDivider = true)
 *         PuiSliderRow(PuiIcon.Height, "高度", height, on, 20, 100, "dp", showDivider = false) { height = it }
 *     }
 * }
 * ```
 *
 * 分隔线由调用方控制（`showDivider`）：**末行传 false，其余传 true**。
 * 增删行时必须同时改相邻行的取值，否则会多一条或少一条线。
 */

/** 页面大标题。左边距 4dp 是相对页面 16dp 内边距的补偿，视觉上对齐卡片内容。 */
@Composable
fun PuiPageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

/** 小节标题（比 [PuiGroup] 的分组标题更重，用于页面内的一级分区）。 */
@Composable
fun PuiSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/**
 * 分组：灰色小标题 + 一张白卡。
 *
 * 卡片底色用 `surfaceContainerLowest`（亮色纯白 / 暗色 `#1A1A1A`），
 * 圆角走 `MaterialTheme.shapes.large`（PUI = 20dp）。
 */
@Composable
fun PuiGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (!title.isNullOrEmpty()) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

/** 行首图标：24dp，颜色取壁纸动态主色（见 [LocalWallpaperAccent]）。 */
@Composable
fun PuiRowIcon(icon: PuiIcon, modifier: Modifier = Modifier) {
    PuiIconView(icon = icon, modifier = modifier.size(24.dp))
}

/** 行间分隔线。默认缩进 54dp（让开 24dp 图标 + 14dp 间距 + 16dp 内边距）。 */
@Composable
fun PuiDivider(startIndent: Dp = 54.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )
}

/**
 * 开关行：图标 + 标题 + 副标题 + 开关。
 *
 * @param enabled 置灰时整行降到 alpha 0.45、开关不可点。
 *   注意**只联动 UI，不改状态** —— 关掉父级不会清掉子项的值。
 * @param showDivider 末行传 false。
 */
@Composable
fun PuiSwitchRow(
    icon: PuiIcon?,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    showDivider: Boolean,
    enabled: Boolean = true,
    /** 标题**下方**的小徽标（高危这类）。放在标题右边会把标题挤断行，所以单起一行。 */
    titleBadge: (@Composable () -> Unit)? = null,
    /** 标题**右边**的小徽标（Beta 这类短标签）。标题过长会省略，不会挤断。 */
    titleInlineBadge: (@Composable () -> Unit)? = null
) {
    val dim = if (enabled) 1f else 0.45f
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconPainter != null) {
                androidx.compose.foundation.Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).alpha(dim),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        LocalWallpaperAccent.current.takeIf { it != Color.Unspecified }
                            ?: MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.width(14.dp))
            } else if (icon != null) {
                PuiRowIcon(icon, Modifier.alpha(dim))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (titleInlineBadge != null) {
                        Spacer(Modifier.width(8.dp))
                        titleInlineBadge()
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim)
                    )
                }
                if (titleBadge != null) {
                    Spacer(Modifier.height(6.dp))
                    titleBadge()
                }
            }
            Spacer(Modifier.width(12.dp))
            PuiSwitch(checked = checked, onChange = onChange, enabled = enabled)
        }
        // 分隔线缩进跟着图标走：用 painter 画官方 SVG 的行也有图标，不能只认 icon
        if (showDivider) PuiDivider(if (icon == null && iconPainter == null) 16.dp else 54.dp)
    }
}

/**
 * 滑块行：图标 + 标签 + 右侧数值 + 滑块。
 *
 * @param suffix 数值后缀（`"dp"` / `"%"`），直接拼在数值后面。
 * @param enabled 置灰时数值与轨道变灰、滑块不可拖。
 * @param showDivider 末行传 false。
 */
@Composable
fun PuiSliderRow(
    icon: PuiIcon?,
    /** 官方 SVG 图标（和 [PuiSwitchRow] 同一套画法），传了就不看 [icon]。 */
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    label: String,
    value: Int,
    enabled: Boolean,
    min: Int,
    max: Int,
    suffix: String,
    showDivider: Boolean,
    /** 标题下面那行小字，不传就只有标题。 */
    subtitle: String = "",
    /** 自定义右上角显示的数值文案，不传就是「数值 + 单位」。 */
    valueText: ((Int) -> String)? = null,
    step: Int = 10,
    onChange: (Int) -> Unit
) {
    val accent by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pui-slider-accent"
    )
    // 置灰时整行减淡（和 PuiSwitchRow 一致）：只变数值颜色的话，
    // 行看起来还是「可操作」，和旁边的置灰开关行不在一个语义上。
    val dim = if (enabled) 1f else 0.45f
    Column {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(dim)) {
                if (iconPainter != null) {
                    androidx.compose.foundation.Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            LocalWallpaperAccent.current.takeIf { it != Color.Unspecified }
                                ?: MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.width(14.dp))
                } else if (icon != null) {
                    PuiRowIcon(icon)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    valueText?.invoke(value) ?: "$value $suffix",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
            }
            PuiSlider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(min, max)) },
                valueRange = min.toFloat()..max.toFloat(),
                enabled = enabled,
                modifier = Modifier
                    // 标题行和滑块之间的留白。取 12dp 是为了**上下均匀**：
                    // 滑块的 Canvas 高 36dp、轨道 24dp，所以轨道上下各有 6dp 的内边距。
                    //   上：12 + 6 = 18dp（标题底 → 轨道顶）
                    //   下： 6 + 12 = 18dp（轨道底 → 行底 / 分隔线）
                    .padding(top = 12.dp)
                    .alpha(dim)
            )
        }
        // 分隔线缩进跟着图标走：用 painter 画官方 SVG 的行也有图标，不能只认 icon
        if (showDivider) PuiDivider(if (icon == null && iconPainter == null) 16.dp else 54.dp)
    }
}

/**
 * 通用列表行：图标 + 标题 +（副标题）+ 右侧内容（文字 / 箭头）。
 *
 * 这是设置页里最常用的一行，[PuiSwitchRow] / [PuiSliderRow] 都是它的特化。
 */
@Composable
fun PuiListRow(
    icon: PuiIcon?,
    /** 自定义图标（矢量 / 图片）。传了它就不画 [icon]，着色与 [PuiIcon] 一致。 */
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String = "",
    /** 标题右侧的小徽标（例如 Beta 标签）。不传就没有。 */
    titleBadge: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val dim = if (enabled) 1f else 0.45f
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null && enabled) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClick() } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconPainter != null) {
                androidx.compose.foundation.Image(
                    painter = iconPainter,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).alpha(dim),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        LocalWallpaperAccent.current.takeIf { it != Color.Unspecified }
                            ?: MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.width(14.dp))
            } else if (icon != null) {
                PuiRowIcon(icon, Modifier.alpha(dim))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface.copy(alpha = dim),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (titleBadge != null) {
                        Spacer(Modifier.width(8.dp))
                        titleBadge()
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = dim),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailingText != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    trailingText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant.copy(alpha = dim)
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                PuiIconView(
                    PuiIcon.ChevronRight,
                    Modifier.size(18.dp).alpha(dim),
                    tint = scheme.onSurfaceVariant
                )
            }
        }
        // 分隔线缩进跟着图标走：用 painter 画官方 SVG 的行也有图标，不能只认 icon
        if (showDivider) PuiDivider(if (icon == null && iconPainter == null) 16.dp else 54.dp)
    }
}

/** 单选行：整行可点，右侧圆点。 */
@Composable
fun PuiRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    subtitle: String = "",
    icon: PuiIcon? = null,
    enabled: Boolean = true
) {
    val dim = if (enabled) 1f else 0.45f
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled
                ) { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PuiRowIcon(icon, Modifier.alpha(dim))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface.copy(alpha = dim)
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = dim)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            PuiRadioDot(selected = selected, onClick = onClick, enabled = enabled)
        }
        // 分隔线缩进跟着图标走：用 painter 画官方 SVG 的行也有图标，不能只认 icon
        if (showDivider) PuiDivider(if (icon == null) 16.dp else 54.dp)
    }
}

/** 勾选行：整行可点，右侧圆角方块。 */
@Composable
fun PuiCheckRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
    subtitle: String = "",
    icon: PuiIcon? = null,
    enabled: Boolean = true
) {
    val dim = if (enabled) 1f else 0.45f
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled
                ) { onChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                PuiRowIcon(icon, Modifier.alpha(dim))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface.copy(alpha = dim)
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant.copy(alpha = dim)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            PuiCheckbox(checked = checked, onChange = onChange, enabled = enabled)
        }
        // 分隔线缩进跟着图标走：用 painter 画官方 SVG 的行也有图标，不能只认 icon
        if (showDivider) PuiDivider(if (icon == null) 16.dp else 54.dp)
    }
}

/** 幽灵按钮：浅底 + 无边框，用于「重置」「强制停止」这类次级操作。高度 46dp、圆角 16dp。 */
@Composable
fun PuiGhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一行里的多个幽灵按钮（等宽）。 */
@Composable
fun PuiGhostButtonRow(
    items: List<String>,
    modifier: Modifier = Modifier,
    onClick: (Int) -> Unit
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { index, label ->
            PuiGhostButton(label, Modifier.weight(1f)) { onClick(index) }
        }
    }
}
