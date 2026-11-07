package com.panl.installer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.panl.installer.i18n.tr
import com.panl.pui.rememberTickHaptic
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ARGB 取色器：SV 面板 + 色相条 + 透明度条 + A/R/G/B/HEX 输入。
 *
 * HEX 框就是普通输入框，长按可以粘贴整段 ARGB（支持 #AARRGGBB / #RRGGBB / 不带 #）。
 */
@Composable
fun ArgbPickerDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
    /** 背后的灰色遮罩；关掉就只剩模糊。 */
    scrim: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    var argb by remember { mutableIntStateOf(initial) }

    // 色相单独存一份：ARGB 只有 8 位，色相在 0/360 边界和灰色上会丢信息，
    // 每次都从颜色反推会导致「拖到最右边突然跳回最左边」。
    var hue by remember { mutableFloatStateOf(argbToHsv(initial)[0]) }

    val hsv = remember(argb) { argbToHsv(argb) }
    val sat = hsv[1]
    val value = hsv[2]
    val alpha = (argb ushr 24) and 0xFF

    fun update(h: Float = hue, s: Float = sat, v: Float = value, a: Int = alpha) {
        hue = h
        argb = hsvToArgb(h, s, v, a)
    }

    /** 直接改 ARGB（输入框）时，色相要跟着新颜色走。 */
    fun setArgb(newArgb: Int) {
        argb = newArgb
        hue = argbToHsv(newArgb)[0]
    }

    Dialog(onDismissRequest = onDismiss) {
        val dialogView = LocalView.current
        SideEffect {
            runCatching {
                (dialogView.parent as? DialogWindowProvider)?.window
                    ?.setDimAmount(if (scrim) 0.25f else 0f)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerLow)
                .padding(20.dp)
        ) {
            Text(
                tr("颜色选择器", "Color picker"),
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            // 饱和度 / 明度面板
            SvPanel(
                hue = hue,
                sat = sat,
                value = value,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp)),
                onChange = { s, v -> update(s = s, v = v) }
            )

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 预览：棋盘格 + 当前色
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(1.dp, scheme.outlineVariant, CircleShape)
                ) {
                    Checkerboard(Modifier.matchParentSize())
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Color(argb))
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    GradientBar(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                Color.Blue, Color.Magenta, Color.Red
                            )
                        ),
                        fraction = hue / 360f,
                        onChange = { f -> update(h = (f * 360f).coerceIn(0f, 359.99f)) }
                    )
                    Spacer(Modifier.height(10.dp))
                    GradientBar(
                        brush = Brush.horizontalGradient(
                            listOf(Color(argb).copy(alpha = 0f), Color(argb).copy(alpha = 1f))
                        ),
                        fraction = alpha / 255f,
                        checker = true,
                        onChange = { f -> update(a = (f * 255f).toInt().coerceIn(0, 255)) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // A% / R / G / B / HEX
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(
                    label = "A%",
                    value = (alpha * 100f / 255f).toInt().toString(),
                    modifier = Modifier.width(46.dp),
                    onCommit = { input ->
                        input.toIntOrNull()?.let { pct ->
                            update(a = (pct.coerceIn(0, 100) * 255f / 100f).toInt())
                        }
                    }
                )
                NumberField(
                    label = "R",
                    value = ((argb shr 16) and 0xFF).toString(),
                    modifier = Modifier.width(38.dp),
                    onCommit = { input ->
                        input.toIntOrNull()?.let { r ->
                            setArgb((argb and 0xFF00FFFF.toInt()) or (r.coerceIn(0, 255) shl 16))
                        }
                    }
                )
                NumberField(
                    label = "G",
                    value = ((argb shr 8) and 0xFF).toString(),
                    modifier = Modifier.width(38.dp),
                    onCommit = { input ->
                        input.toIntOrNull()?.let { g ->
                            setArgb((argb and 0xFFFF00FF.toInt()) or (g.coerceIn(0, 255) shl 8))
                        }
                    }
                )
                NumberField(
                    label = "B",
                    value = (argb and 0xFF).toString(),
                    modifier = Modifier.width(38.dp),
                    onCommit = { input ->
                        input.toIntOrNull()?.let { b ->
                            setArgb((argb and 0xFFFFFF00.toInt()) or b.coerceIn(0, 255))
                        }
                    }
                )
                NumberField(
                    label = "HEX",
                    value = "%08X".format(argb),
                    modifier = Modifier.weight(1f),
                    onCommit = { input -> parseArgb(input)?.let { setArgb(it) } }
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(tr("取消", "Cancel"), color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onApply(argb) }) {
                    Text(tr("确定", "OK"), color = scheme.primary)
                }
            }
        }
    }
}

/** 饱和度（横）× 明度（纵）面板。 */
@Composable
private fun SvPanel(
    hue: Float,
    sat: Float,
    value: Float,
    modifier: Modifier = Modifier,
    onChange: (Float, Float) -> Unit
) {
    val pure = remember(hue) { Color.hsv(hue, 1f, 1f) }
    // 用 rememberUpdatedState 拿最新回调：pointerInput 只挂一次，不会因为颜色变化重启而打断拖动
    val onColorChange by rememberUpdatedState(onChange)
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // 用位移累加而不是绝对坐标：手指划到面板外时，系统给的绝对坐标
                // 会跑到别的坐标系里（表现为「一出去就变黑」），位移则始终是本地增量
                var x = down.position.x
                var y = down.position.y
                fun report() {
                    onColorChange(
                        (x / size.width).coerceIn(0f, 1f),
                        (1f - y / size.height).coerceIn(0f, 1f)
                    )
                }
                report()
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    val delta = change.positionChange()
                    // 每步都夹回面板内：否则手指划出去后累加值一直涨，
                    // 回来时要先把「多出去的那段」走完，看起来像卡在黑色里
                    x = (x + delta.x).coerceIn(0f, size.width.toFloat())
                    y = (y + delta.y).coerceIn(0f, size.height.toFloat())
                    report()
                    change.consume()
                }
            }
        }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        // 圆点往里缩一个半径：贴边时不会被圆角裁掉一半
        val r = 9.dp.toPx()
        val cx = (sat * size.width).coerceIn(r, size.width - r)
        val cy = ((1f - value) * size.height).coerceIn(r, size.height - r)
        drawCircle(
            color = Color.White,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

/** 色相 / 透明度滑条：带棋盘格底和白色滑块。 */
@Composable
private fun GradientBar(
    brush: Brush,
    fraction: Float,
    onChange: (Float) -> Unit,
    checker: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val onFractionChange by rememberUpdatedState(onChange)
    // 拖动时按位移打点：太密会糊成一片震动，10dp 一格刚好有「咔哒」感
    val tick = rememberTickHaptic()
    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(11.dp))
    ) {
        if (checker) Checkerboard(Modifier.matchParentSize())
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var x = down.position.x
                        var lastTickX = x
                        val stepPx = 10.dp.toPx()
                        fun report(force: Boolean = false) {
                            onFractionChange((x / size.width).coerceIn(0f, 1f))
                            if (force || abs(x - lastTickX) >= stepPx) {
                                lastTickX = x
                                tick()
                            }
                        }
                        report(force = true)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            x = (x + change.positionChange().x)
                                .coerceIn(0f, size.width.toFloat())
                            report()
                            change.consume()
                        }
                    }
                }
        ) {
            drawRect(brush)
            val x = fraction.coerceIn(0f, 1f) * size.width
            val half = 8.dp.toPx()
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(
                    (x - half).coerceIn(0f, size.width - half * 2),
                    -1.dp.toPx()
                ),
                size = Size(half * 2, size.height + 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
    }
}

/** 棋盘格底（表示透明）。 */
@Composable
private fun Checkerboard(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = 8.dp.toPx()
        drawRect(Color(0xFFFFFFFF))
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(
                    color = Color(0xFFD9D9D9),
                    topLeft = Offset(x, y),
                    size = Size(cell, cell)
                )
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

/** 带下划线的小输入框（A% / R / G / B / HEX）。 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 输入过程中不覆盖用户输入；颜色变了（拖面板）才同步回显
    var text by remember(value) { mutableStateOf(value) }

    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
        BasicTextField(
            value = text,
            onValueChange = { input ->
                val cleaned = input.filter { it.isLetterOrDigit() || it == '#' }.uppercase()
                text = cleaned
                onCommit(cleaned)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = scheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(scheme.outlineVariant)
        )
    }
}

/** 解析 #AARRGGBB / #RRGGBB / AARRGGBB / RRGGBB。 */
fun parseArgb(input: String): Int? {
    val raw = input.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (raw.length != 6 && raw.length != 8) return null
    val v = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) (0xFF000000L or v).toInt() else v.toInt()
}

/** ARGB → [色相 0..360, 饱和度, 明度]。 */
private fun argbToHsv(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == r -> 60f * (((g - b) / d) % 6f)
        mx == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val s = if (mx == 0f) 0f else d / mx
    return floatArrayOf(h, s, mx)
}

/** HSV(+alpha) → ARGB。 */
private fun hsvToArgb(h: Float, s: Float, v: Float, a: Int): Int {
    val rgb = Color.hsv(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)).toArgb()
    return ((a.coerceIn(0, 255)) shl 24) or (rgb and 0x00FFFFFF)
}
