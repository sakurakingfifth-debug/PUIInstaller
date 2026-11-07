package com.panl.pui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * PUI 图标：Iconsax Duotone Filled 语言的手绘路径。
 *
 * 构造规则（加新图标必须遵守，否则整屏图标会「花」）：
 *  - 纯实心色块，**不用描边勾轮廓**（这一点和 outline 风格是本质区别）；
 *  - 两层同色：主形 100%（main），辅助形 45%（sub）—— Iconsax duotone 就是深浅两块；
 *  - 圆角给得比较足，边缘柔和；
 *  - 24×24 网格，元素留 4%–6% 安全边距。
 *
 * 颜色默认取壁纸动态主色（[LocalWallpaperAccent]），跟着壁纸变。
 * 需要「挖洞」（同心圆环、月牙、托盘）时用 EvenOdd 填充，不要用描边。
 */

enum class PuiIcon {
    // —— 原始 11 个（PadTaskbarMod 里逐个对齐截图还原的） ——
    /** 尺寸 / 布局：左侧高条 + 右侧上下两个方块 */
    Element,

    /** 其他 / 更多：油漆桶 + 刷子 */
    Brush,

    /** 任务栏高度：浅色竖条 + 上下实心箭头 */
    Height,

    /** 图标大小：浅色外框 + 内部实心方块 */
    IconSize,

    /** 触控区：同心圆盘 */
    Touch,

    /** Dock 圆角：内外两层胶囊 */
    Round,

    /** 强制任务栏：手机机身 + 底部任务栏条 */
    Phone,

    /** 两块圆角方块 + 中间通高竖条 */
    Split,

    /** 竖向滑轨：浅色轨道 + 上下两条实心横轨 */
    SliderVertical,

    /** Dock 光效：同心光晕 + 中心高光 */
    Glow,

    /** 已选中：浅色外圆 + 实心内圆 */
    Selected,

    // —— 扩充的通用图标 ——
    Home, Search, Bell, Star, Heart, Palette, Palette2, PaletteRound, Library, Squares3, RulerPen, Brush4, Edit,
    Grid, List, Folder, Image, Play, Download,
    Share, Lock, User, Clock, Sun, Moon,
    Check, Plus, Close, ChevronRight, Info, Warning,
    Trash, Filter, More
}

/** 24dp 图标，默认取壁纸主色；[tint] 可覆盖。 */
@Composable
fun PuiIconView(icon: PuiIcon, modifier: Modifier = Modifier, tint: Color? = null) {
    val wallpaper = LocalWallpaperAccent.current
    val color = tint
        ?: wallpaper.takeIf { it != Color.Unspecified }
        ?: MaterialTheme.colorScheme.primary
    Canvas(modifier.size(24.dp)) { drawPuiIcon(icon, color) }
}

private fun DrawScope.drawPuiIcon(icon: PuiIcon, color: Color) {
    val s = size.minDimension
    val main = color                        // 主形：实心
    val sub = color.copy(alpha = 0.45f)     // 辅助形：同色 45%

    fun p(x: Float, y: Float) = Offset(s * x, s * y)

    /** 圆角矩形色块。 */
    fun rr(l: Float, t: Float, r: Float, b: Float, rad: Float, c: Color) = drawRoundRect(
        color = c,
        topLeft = p(l, t),
        size = Size(s * (r - l), s * (b - t)),
        cornerRadius = CornerRadius(s * rad, s * rad)
    )

    /** 圆盘。 */
    fun disc(cx: Float, cy: Float, rad: Float, c: Color) = drawCircle(c, s * rad, p(cx, cy))

    /** 圆环（EvenOdd 挖洞，不是描边）。 */
    fun ring(cx: Float, cy: Float, rOut: Float, rIn: Float, c: Color) {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(s * (cx - rOut), s * (cy - rOut), s * (cx + rOut), s * (cy + rOut)))
            addOval(Rect(s * (cx - rIn), s * (cy - rIn), s * (cx + rIn), s * (cy + rIn)))
        }
        drawPath(path, c)
    }

    /**
     * 直接画 SVG path 字符串（24 单位坐标系）。
     * 少数图标直接取自图标库素材，用基础图元拼不出来，就原样喂进来。
     */
    fun raw(d: String, c: Color, evenOdd: Boolean = false) {
        val path = PathParser().parsePathString(d).toPath()
        if (evenOdd) path.fillType = PathFillType.EvenOdd
        scale(s / 24f, pivot = Offset.Zero) { drawPath(path, c) }
    }

    /** 圆头粗线。 */
    fun bar(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, c: Color) =
        drawLine(c, p(x1, y1), p(x2, y2), strokeWidth = s * w, cap = StrokeCap.Round)

    /** 三角形（实心）。 */
    fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, c: Color) {
        drawPath(
            Path().apply {
                moveTo(s * x1, s * y1)
                lineTo(s * x2, s * y2)
                lineTo(s * x3, s * y3)
                close()
            }, c
        )
    }

    /** 圆弧描边（给锁扣、表盘这类必须用弧的地方）。 */
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float, w: Float, c: Color) =
        drawArc(
            color = c,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = p(cx - r, cy - r),
            size = Size(s * 2 * r, s * 2 * r),
            style = Stroke(width = s * w, cap = StrokeCap.Round)
        )

    /** 自定义路径。 */
    fun path(c: Color, build: Path.() -> Unit) = drawPath(Path().apply(build), c)

    /** 正多角星。 */
    fun star(cx: Float, cy: Float, rOut: Float, rIn: Float, c: Color) {
        val path = Path()
        val points = 5
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) rOut else rIn
            val a = -PI / 2 + i * PI / points
            val x = s * (cx + (r * cos(a)).toFloat())
            val y = s * (cy + (r * sin(a)).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, c)
    }

    when (icon) {
        // ---------------- 原始 11 个 ----------------

        PuiIcon.Element -> {
            rr(0.104f, 0.083f, 0.458f, 0.917f, 0.10f, main)
            rr(0.542f, 0.083f, 0.896f, 0.458f, 0.10f, sub)
            rr(0.542f, 0.542f, 0.896f, 0.917f, 0.10f, sub)
        }

        // 油漆桶 + 刷子：按 brush-2.svg 的四条路径逐点还原（原始坐标 ÷24）
        PuiIcon.Brush -> {
            val body = Path().apply {
                moveTo(s * 0.8404f, s * 0.2208f)
                lineTo(s * 0.8204f, s * 0.5838f)
                cubicTo(s * 0.8179f, s * 0.5833f, s * 0.8154f, s * 0.5833f, s * 0.8125f, s * 0.5833f)
                lineTo(s * 0.1875f, s * 0.5833f)
                cubicTo(s * 0.1846f, s * 0.5833f, s * 0.1821f, s * 0.5833f, s * 0.1796f, s * 0.5838f)
                lineTo(s * 0.1596f, s * 0.2208f)
                cubicTo(s * 0.1521f, s * 0.1471f, s * 0.2100f, s * 0.0833f, s * 0.2838f, s * 0.0833f)
                lineTo(s * 0.7163f, s * 0.0833f)
                cubicTo(s * 0.7900f, s * 0.0833f, s * 0.8479f, s * 0.1471f, s * 0.8404f, s * 0.2208f)
                close()
            }
            drawPath(body, sub)

            rr(0.3017f, 0.0833f, 0.3642f, 0.3229f, 0.03125f, main)
            rr(0.4688f, 0.0833f, 0.5313f, 0.1979f, 0.03125f, main)

            val rim = Path().apply {
                moveTo(s * 0.3958f, s * 0.8125f)
                lineTo(s * 0.3958f, s * 0.7500f)
                lineTo(s * 0.1875f, s * 0.7500f)
                cubicTo(s * 0.1646f, s * 0.7500f, s * 0.1438f, s * 0.7408f, s * 0.1288f, s * 0.7254f)
                cubicTo(s * 0.1133f, s * 0.7104f, s * 0.1042f, s * 0.6896f, s * 0.1042f, s * 0.6667f)
                cubicTo(s * 0.1042f, s * 0.6238f, s * 0.1375f, s * 0.5879f, s * 0.1796f, s * 0.5838f)
                cubicTo(s * 0.1821f, s * 0.5833f, s * 0.1846f, s * 0.5833f, s * 0.1875f, s * 0.5833f)
                lineTo(s * 0.8125f, s * 0.5833f)
                cubicTo(s * 0.8154f, s * 0.5833f, s * 0.8179f, s * 0.5833f, s * 0.8204f, s * 0.5838f)
                cubicTo(s * 0.8404f, s * 0.5854f, s * 0.8579f, s * 0.5942f, s * 0.8713f, s * 0.6079f)
                cubicTo(s * 0.8883f, s * 0.6246f, s * 0.8975f, s * 0.6483f, s * 0.8954f, s * 0.6742f)
                cubicTo(s * 0.8917f, s * 0.7179f, s * 0.8521f, s * 0.7500f, s * 0.8079f, s * 0.7500f)
                lineTo(s * 0.6042f, s * 0.7500f)
                lineTo(s * 0.6042f, s * 0.8125f)
                cubicTo(s * 0.6042f, s * 0.8700f, s * 0.5575f, s * 0.9167f, s * 0.5000f, s * 0.9167f)
                cubicTo(s * 0.4425f, s * 0.9167f, s * 0.3958f, s * 0.8700f, s * 0.3958f, s * 0.8125f)
                close()
            }
            drawPath(rim, main)
        }

        // 并排两支箭头：左实心上、右浅色下
        PuiIcon.Height -> {
            rr(0.29f, 0.30f, 0.41f, 0.72f, 0.06f, main)
            tri(0.35f, 0.10f, 0.51f, 0.36f, 0.19f, 0.36f, main)
            rr(0.59f, 0.28f, 0.71f, 0.70f, 0.06f, sub)
            tri(0.65f, 0.90f, 0.49f, 0.64f, 0.81f, 0.64f, sub)
        }

        PuiIcon.IconSize -> {
            rr(0.04f, 0.04f, 0.96f, 0.96f, 0.24f, sub)
            rr(0.30f, 0.30f, 0.70f, 0.70f, 0.10f, main)
        }

        // 同心三层：外环 + 触点 + 淡光晕
        //
        // 原实现是「实心盘 → 45% 盘 → 实心盘」，后画的 45% 盘叠在不透明实心盘上
        // 合成出来还是实色，三层根本看不出来（渲染出来是一个纯色圆）。
        // 这里改成 EvenOdd 圆环，才真的能看出「触点 / 目标」。
        PuiIcon.Touch -> {
            disc(0.50f, 0.50f, 0.46f, color.copy(alpha = 0.12f))
            ring(0.50f, 0.50f, 0.44f, 0.30f, main)
            disc(0.50f, 0.50f, 0.14f, main)
        }

        PuiIcon.Round -> {
            rr(0.02f, 0.22f, 0.98f, 0.78f, 0.28f, sub)
            rr(0.16f, 0.34f, 0.84f, 0.66f, 0.16f, main)
        }

        PuiIcon.Phone -> {
            rr(0.167f, 0.083f, 0.833f, 0.917f, 0.080f, sub)
            rr(0.417f, 0.198f, 0.583f, 0.260f, 0.031f, main)
            disc(0.500f, 0.731f, 0.073f, main)
        }

        PuiIcon.Split -> {
            rr(0.59375f, 0.08333f, 0.65625f, 0.91667f, 0.03125f, main)
            drawPath(
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(s * 0.08333f, s * 0.16667f, s * 0.50000f, s * 0.83333f),
                            topLeft = CornerRadius(s * 0.16667f),
                            bottomLeft = CornerRadius(s * 0.16667f),
                        )
                    )
                }, sub
            )
            drawPath(
                Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(s * 0.62500f, s * 0.16667f, s * 0.91667f, s * 0.83333f),
                            topRight = CornerRadius(s * 0.16667f),
                            bottomRight = CornerRadius(s * 0.16667f),
                        )
                    )
                }, sub
            )
            rr(0.26042f, 0.32292f, 0.32292f, 0.67708f, 0.03125f, main)
        }

        PuiIcon.SliderVertical -> {
            rr(0.115f, 0.192f, 0.885f, 0.808f, 0.125f, sub)
            rr(0.180f, 0.083f, 0.820f, 0.146f, 0.031f, main)
            rr(0.180f, 0.854f, 0.820f, 0.917f, 0.031f, main)
        }

        PuiIcon.Glow -> {
            disc(0.50f, 0.50f, 0.46f, color.copy(alpha = 0.12f))
            rr(0.455f, 0.02f, 0.545f, 0.17f, 0.045f, sub)
            rr(0.455f, 0.83f, 0.545f, 0.98f, 0.045f, sub)
            rr(0.02f, 0.455f, 0.17f, 0.545f, 0.045f, sub)
            rr(0.83f, 0.455f, 0.98f, 0.545f, 0.045f, sub)
            disc(0.50f, 0.50f, 0.23f, main)
        }

        PuiIcon.Selected -> {
            disc(0.50f, 0.50f, 0.46f, sub)
            disc(0.50f, 0.50f, 0.24f, main)
        }

        // ---------------- 扩充图标 ----------------

        // 房子：屋顶实心三角 + 浅色墙体 + 实心门
        PuiIcon.Home -> {
            rr(0.19f, 0.40f, 0.81f, 0.90f, 0.10f, sub)
            tri(0.50f, 0.06f, 0.03f, 0.52f, 0.97f, 0.52f, main)
            rr(0.42f, 0.62f, 0.58f, 0.90f, 0.04f, main)
        }

        // 放大镜：圆环 + 圆头手柄
        PuiIcon.Search -> {
            ring(0.42f, 0.42f, 0.32f, 0.19f, main)
            bar(0.60f, 0.60f, 0.86f, 0.86f, 0.13f, main)
        }

        // 铃铛：钟形实心 + 浅色钟身 + 实心铃舌
        PuiIcon.Bell -> {
            path(sub) {
                moveTo(s * 0.50f, s * 0.07f)
                cubicTo(s * 0.72f, s * 0.07f, s * 0.80f, s * 0.24f, s * 0.80f, s * 0.44f)
                lineTo(s * 0.80f, s * 0.62f)
                lineTo(s * 0.90f, s * 0.72f)
                lineTo(s * 0.10f, s * 0.72f)
                lineTo(s * 0.20f, s * 0.62f)
                lineTo(s * 0.20f, s * 0.44f)
                cubicTo(s * 0.20f, s * 0.24f, s * 0.28f, s * 0.07f, s * 0.50f, s * 0.07f)
                close()
            }
            disc(0.50f, 0.83f, 0.085f, main)
            disc(0.50f, 0.07f, 0.055f, main)
        }

        // 五角星
        PuiIcon.Star -> star(0.50f, 0.53f, 0.46f, 0.19f, main)

        // 心形
        PuiIcon.Heart -> path(main) {
            moveTo(s * 0.50f, s * 0.87f)
            cubicTo(s * 0.41f, s * 0.78f, s * 0.07f, s * 0.58f, s * 0.07f, s * 0.34f)
            cubicTo(s * 0.07f, s * 0.17f, s * 0.21f, s * 0.09f, s * 0.34f, s * 0.09f)
            cubicTo(s * 0.42f, s * 0.09f, s * 0.47f, s * 0.13f, s * 0.50f, s * 0.18f)
            cubicTo(s * 0.53f, s * 0.13f, s * 0.58f, s * 0.09f, s * 0.66f, s * 0.09f)
            cubicTo(s * 0.79f, s * 0.09f, s * 0.93f, s * 0.17f, s * 0.93f, s * 0.34f)
            cubicTo(s * 0.93f, s * 0.58f, s * 0.59f, s * 0.78f, s * 0.50f, s * 0.87f)
            close()
        }

        // 编辑：取自 Iconsax（Edit）。圆角方底 40%，铅笔不透明。
        PuiIcon.Edit -> {
            raw(EDIT_BG, main.copy(alpha = main.alpha * 0.40f))
            raw(EDIT_PENCIL, main)
        }

        // 刷子 4：取自 Iconsax（Brush 4）。刷头不透明、手柄和连接管 40%。
        // 先画灰的再画黑的 —— 刷头要压在连接管上面。
        PuiIcon.Brush4 -> {
            raw(BRUSH4_REST, main.copy(alpha = main.alpha * 0.40f))
            raw(BRUSH4_HEAD, main)
        }

        // 尺与笔：取自 Iconsax（Ruler Pen）。两个主体 40%，刻度 100% —— 刻度要压在主体上面，
        // 所以先画主体再画刻度，顺序不能反。
        PuiIcon.RulerPen -> {
            raw(RULER_PEN_BODY, main.copy(alpha = main.alpha * 0.40f))
            raw(RULER_PEN_TICKS, main)
        }

        // 三个方块：取自 Iconsax（3 Squares）。三块透明度不同（0.4 / 0.6 / 1），
        // 靠这个拉开前后层次，所以按原值画、不套 sub。
        PuiIcon.Squares3 -> {
            raw(SQUARES3_A, main.copy(alpha = main.alpha * 0.40f))
            raw(SQUARES3_B, main.copy(alpha = main.alpha * 0.60f))
            raw(SQUARES3_C, main)
        }

        // 圆角调色盘：直接取自图标库（Palette Round）。
        // 两段盘身都带缺口，只能分开画 —— 第一段是 evenOdd，第二段是普通填充。
        PuiIcon.PaletteRound -> {
            raw(PALETTE_ROUND_A, sub, evenOdd = true)
            raw(PALETTE_ROUND_B, sub)
            raw(PALETTE_ROUND_C, main)
        }

        // 书堆：直接取自图标库（Library），三层叠放 + 底部一道横槽（evenOdd 挖空）
        PuiIcon.Library -> raw(LIBRARY_STACK, main, evenOdd = true)

        // 调色盘 2：直接取自图标库（Pallete 2），盘身半透明 + 四个颜料点
        PuiIcon.Palette2 -> {
            raw(PALETTE2_BODY, sub)
            raw(PALETTE2_DOTS, main)
        }

        // 调色盘：浅色盘身 + 三个颜料点 + 拇指孔
        PuiIcon.Palette -> {
            disc(0.50f, 0.50f, 0.44f, sub)
            disc(0.33f, 0.33f, 0.070f, main)
            disc(0.56f, 0.27f, 0.070f, main)
            disc(0.72f, 0.46f, 0.070f, main)
            disc(0.41f, 0.69f, 0.095f, main)
        }

        // 四宫格
        PuiIcon.Grid -> {
            rr(0.08f, 0.08f, 0.44f, 0.44f, 0.10f, main)
            rr(0.56f, 0.08f, 0.92f, 0.44f, 0.10f, sub)
            rr(0.08f, 0.56f, 0.44f, 0.92f, 0.10f, sub)
            rr(0.56f, 0.56f, 0.92f, 0.92f, 0.10f, main)
        }

        // 列表：圆点 + 横条
        PuiIcon.List -> {
            for (i in 0..2) {
                val y = 0.22f + i * 0.28f
                disc(0.14f, y, 0.065f, main)
                bar(0.33f, y, 0.90f, y, 0.10f, sub)
            }
        }

        // 文件夹：实心标签 + 浅色袋身
        PuiIcon.Folder -> {
            rr(0.08f, 0.30f, 0.92f, 0.84f, 0.10f, sub)
            rr(0.08f, 0.20f, 0.46f, 0.36f, 0.055f, main)
        }

        // 图片：浅色画框 + 实心山 + 太阳
        PuiIcon.Image -> {
            rr(0.06f, 0.14f, 0.94f, 0.86f, 0.14f, sub)
            tri(0.24f, 0.74f, 0.48f, 0.42f, 0.72f, 0.74f, main)
            disc(0.68f, 0.35f, 0.070f, main)
        }

        // 播放：浅色圆 + 实心三角
        PuiIcon.Play -> {
            disc(0.50f, 0.50f, 0.44f, sub)
            tri(0.42f, 0.29f, 0.42f, 0.71f, 0.76f, 0.50f, main)
        }

        // 下载：实心箭头 + 浅色托盘
        PuiIcon.Download -> {
            bar(0.50f, 0.10f, 0.50f, 0.52f, 0.12f, main)
            tri(0.50f, 0.76f, 0.27f, 0.49f, 0.73f, 0.49f, main)
            rr(0.14f, 0.82f, 0.86f, 0.90f, 0.04f, sub)
        }

        // 分享：三个节点 + 两条连线
        PuiIcon.Share -> {
            bar(0.35f, 0.45f, 0.67f, 0.27f, 0.065f, sub)
            bar(0.35f, 0.55f, 0.67f, 0.73f, 0.065f, sub)
            disc(0.76f, 0.22f, 0.115f, main)
            disc(0.76f, 0.78f, 0.115f, main)
            disc(0.26f, 0.50f, 0.115f, main)
        }

        // 锁：浅色锁体 + 实心锁扣 + 实心锁孔
        PuiIcon.Lock -> {
            arc(0.50f, 0.44f, 0.235f, 180f, 180f, 0.11f, main)
            rr(0.17f, 0.44f, 0.83f, 0.90f, 0.12f, sub)
            disc(0.50f, 0.63f, 0.070f, main)
            bar(0.50f, 0.63f, 0.50f, 0.76f, 0.055f, main)
        }

        // 用户：实心头像 + 浅色肩部
        PuiIcon.User -> {
            disc(0.50f, 0.33f, 0.19f, main)
            path(sub) {
                moveTo(s * 0.14f, s * 0.92f)
                lineTo(s * 0.14f, s * 0.82f)
                cubicTo(s * 0.14f, s * 0.67f, s * 0.29f, s * 0.58f, s * 0.50f, s * 0.58f)
                cubicTo(s * 0.71f, s * 0.58f, s * 0.86f, s * 0.67f, s * 0.86f, s * 0.82f)
                lineTo(s * 0.86f, s * 0.92f)
                close()
            }
        }

        // 时钟：浅色表盘 + 实心指针
        PuiIcon.Clock -> {
            ring(0.50f, 0.50f, 0.44f, 0.345f, sub)
            bar(0.50f, 0.50f, 0.50f, 0.26f, 0.075f, main)
            bar(0.50f, 0.50f, 0.67f, 0.60f, 0.075f, main)
        }

        // 太阳：实心圆 + 八道光线
        PuiIcon.Sun -> {
            disc(0.50f, 0.50f, 0.225f, main)
            for (i in 0 until 8) {
                val a = i * PI / 4
                val c = cos(a).toFloat()
                val sn = sin(a).toFloat()
                bar(0.50f + c * 0.33f, 0.50f + sn * 0.33f, 0.50f + c * 0.46f, 0.50f + sn * 0.46f, 0.075f, sub)
            }
        }

        // 月牙：外圆减内圆（EvenOdd）
        PuiIcon.Moon -> {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(Rect(s * 0.08f, s * 0.08f, s * 0.92f, s * 0.92f))
                addOval(Rect(s * 0.40f, s * 0.00f, s * 1.10f, s * 0.70f))
            }
            drawPath(path, main)
        }

        // 对勾：浅色圆 + 实心勾
        PuiIcon.Check -> {
            disc(0.50f, 0.50f, 0.44f, sub)
            drawPath(
                Path().apply {
                    moveTo(s * 0.28f, s * 0.52f)
                    lineTo(s * 0.43f, s * 0.68f)
                    lineTo(s * 0.73f, s * 0.33f)
                },
                main,
                style = Stroke(width = s * 0.115f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        PuiIcon.Plus -> {
            bar(0.50f, 0.15f, 0.50f, 0.85f, 0.13f, main)
            bar(0.15f, 0.50f, 0.85f, 0.50f, 0.13f, main)
        }

        PuiIcon.Close -> {
            bar(0.21f, 0.21f, 0.79f, 0.79f, 0.13f, main)
            bar(0.79f, 0.21f, 0.21f, 0.79f, 0.13f, main)
        }

        PuiIcon.ChevronRight -> {
            bar(0.38f, 0.22f, 0.66f, 0.50f, 0.12f, main)
            bar(0.66f, 0.50f, 0.38f, 0.78f, 0.12f, main)
        }

        // 信息：浅色圆 + 实心 i
        PuiIcon.Info -> {
            disc(0.50f, 0.50f, 0.44f, sub)
            disc(0.50f, 0.28f, 0.065f, main)
            bar(0.50f, 0.42f, 0.50f, 0.74f, 0.10f, main)
        }

        // 警告：浅色三角 + 实心叹号
        //
        // 同样不能用「实心三角 + 45% 叹号」—— 45% 叠在实色三角上等于实色，叹号会消失。
        PuiIcon.Warning -> {
            tri(0.50f, 0.07f, 0.04f, 0.90f, 0.96f, 0.90f, sub)
            bar(0.50f, 0.36f, 0.50f, 0.60f, 0.10f, main)
            disc(0.50f, 0.75f, 0.055f, main)
        }

        // 垃圾桶：实心盖 + 浅色桶身 + 实心竖纹
        PuiIcon.Trash -> {
            rr(0.37f, 0.08f, 0.63f, 0.18f, 0.04f, main)
            rr(0.10f, 0.18f, 0.90f, 0.27f, 0.045f, main)
            rr(0.20f, 0.31f, 0.80f, 0.92f, 0.08f, sub)
            bar(0.40f, 0.44f, 0.40f, 0.78f, 0.06f, main)
            bar(0.60f, 0.44f, 0.60f, 0.78f, 0.06f, main)
        }

        // 筛选：三条递减横条
        PuiIcon.Filter -> {
            rr(0.09f, 0.25f, 0.91f, 0.34f, 0.045f, main)
            rr(0.21f, 0.45f, 0.79f, 0.54f, 0.045f, sub)
            rr(0.34f, 0.65f, 0.66f, 0.74f, 0.045f, main)
        }

        // 更多：三个圆点
        PuiIcon.More -> {
            disc(0.20f, 0.50f, 0.085f, main)
            disc(0.50f, 0.50f, 0.085f, main)
            disc(0.80f, 0.50f, 0.085f, sub)
        }
    }
}

// ---------------- 取自图标库的原始路径 ----------------

/** Solar「Pallete 2」的盘身。 */
private const val PALETTE2_BODY =
    "M10.8468 21.9342C5.86713 21.3624 2 17.1328 2 12 2 6.47715 6.47715 2 12 2c5.5228.0 10 4.47715 10 10 0 5.1565-3.2827 4.7325-6.0865 4.3703C14.2964 16.1614 12.8386 15.9731 12.2619 16.888 11.8674 17.5136 12.2938 18.2938 12.8168 18.8168 13.4703 19.4703 13.4703 20.5297 12.8168 21.1832 12.2938 21.7062 11.5816 22.0186 10.8468 21.9342z"

/** Solar「Pallete 2」的四个颜料点。 */
private const val PALETTE2_DOTS =
    "M11.085 7C11.085 7.82843 10.4134 8.5 9.585 8.5 8.75658 8.5 8.085 7.82843 8.085 7S8.75658 5.5 9.585 5.5C10.4134 5.5 11.085 6.17157 11.085 7zM6.5 13.0002c.82843.0 1.5-.6715 1.5-1.5.0-.8284-.67157-1.5-1.5-1.5s-1.5.6716-1.5 1.5c0 .8285.67157 1.5 1.5 1.5zM17.5 13.0002c.828399999999999.0 1.5-.6715 1.5-1.5.0-.8284-.671600000000002-1.5-1.5-1.5s-1.5.6716-1.5 1.5c0 .8285.671600000000002 1.5 1.5 1.5zM14.5 8.50023c.8284.0 1.5-.67157 1.5-1.5s-.6716-1.5-1.5-1.5-1.5.67157-1.5 1.5.6716 1.5 1.5 1.5z"

/** Solar「Library」：三层书堆，最后一段用 evenOdd 挖出横槽。 */
private const val LIBRARY_STACK =
    "M17.8618 4.72266C19.2524 4.72267 20.3924 5.56273 20.7729 6.67676C20.7808 6.69984 20.7892 6.72279 20.7964 6.7461C20.3982 6.62553 19.9834 6.54694 19.564 6.49317C18.4837 6.35469 17.1185 6.35539 15.5327 6.35547H8.64014C7.05435 6.35539 5.68918 6.35472 4.60889 6.49317C4.18946 6.54694 3.77458 6.62555 3.37646 6.7461C3.38366 6.72289 3.39108 6.69975 3.39893 6.67676C3.77945 5.56272 4.92041 4.72266 6.31104 4.72266H17.8618ZM15.4897 2C15.7222 1.99995 15.9013 2.00039 16.0571 2.01563C17.1646 2.12414 18.071 2.78961 18.4556 3.68653H5.54443C5.929 2.78944 6.83612 2.12399 7.94385 2.01563C8.09959 2.00041 8.27791 1.99995 8.51025 2H15.4897ZM8.67239 7.54199H15.3276C18.7024 7.54199 20.3898 7.54199 21.3377 8.52882C22.2855 9.51565 22.0625 11.0403 21.6165 14.0895L21.1935 16.9811C20.8437 19.3723 20.6689 20.5679 19.7717 21.2839C18.8745 21.9999 17.5512 21.9999 14.9046 21.9999H9.09536C6.44881 21.9999 5.12553 21.9999 4.22834 21.2839C3.33115 20.5679 3.15626 19.3723 2.80648 16.9811L2.38351 14.0895C1.93748 11.0403 1.71447 9.51565 2.66232 8.52882C3.61017 7.54199 5.29758 7.54199 8.67239 7.54199ZM8 18.0001C8 17.5859 8.3731 17.2501 8.83333 17.2501H15.1667C15.6269 17.2501 16 17.5859 16 18.0001C16 18.4143 15.6269 18.7501 15.1667 18.7501H8.83333C8.3731 18.7501 8 18.4143 8 18.0001Z"

/** Solar「Palette Round」：主盘身（evenOdd 挖缺口）。 */
private const val PALETTE_ROUND_A =
    "M6 2C8.20914 2 10 3.79086 10 6V7.90039L13.2842 4.61621C14.8627 3.03793 17.4215 3.03792 19 4.61621C20.5551 6.17129 20.5815 8.68397 19.0596 10.2715L13.2217 16.3613L9.24805 20.3359C9.25891 20.3209 9.26766 20.3043 9.27832 20.2891C8.55516 21.3228 7.35733 22 6 22C3.79086 22 2 20.2091 2 18V6C2 3.79086 3.79086 2 6 2Z"

/** Solar「Palette Round」：右上那一段折叠的盘身。 */
private const val PALETTE_ROUND_B =
    "M13.2842 4.61615C14.8627 3.03794 17.4215 3.03794 19 4.61615C20.5551 6.17123 20.5815 8.68488 19.0596 10.2724L13.2217 16.3613L9.24707 20.3359C9.72057 19.6787 10 18.8718 10 17.9999V7.90033L13.2842 4.61615Z"

/** Solar「Palette Round」：右下盘身 + 颜料点。 */
private const val PALETTE_ROUND_C =
    "M17.8994 14C20.1086 14 21.8994 15.7909 21.8994 18C21.8994 20.2091 20.1086 22 17.8994 22H6C7.35277 22 8.54757 21.3277 9.27148 20.2998C9.26322 20.3115 9.25643 20.3243 9.24805 20.3359L13.2217 16.3613L15.4863 14H17.8994ZM6 17C6.55228 17 7 17.4477 7 18C7 18.5523 6.55228 19 6 19C5.44772 19 5 18.5523 5 18C5 17.4477 5.44772 17 6 17Z"

/** Iconsax「3 Squares」：最下面一块（40%）。 */
private const val SQUARES3_A =
    "M12 17V19.38C12 21.25 11.25 22 9.37 22H4.62C2.75 22 2 21.25 2 19.38V14.63C2 12.75 2.75 12 4.62 12H7V14.37C7 16.25 7.75 17 9.62 17H12Z"

/** Iconsax「3 Squares」：中间一块（60%）。 */
private const val SQUARES3_B =
    "M17 12V14.37C17 16.25 16.25 17 14.37 17H9.62C7.75 17 7 16.25 7 14.37V9.62C7 7.75 7.75 7 9.62 7H12V9.37C12 11.25 12.75 12 14.62 12H17Z"

/** Iconsax「3 Squares」：最上面一块（不透明）。 */
private const val SQUARES3_C =
    "M22 4.62V9.37C22 11.25 21.25 12 19.37 12H14.62C12.75 12 12 11.25 12 9.37V4.62C12 2.75 12.75 2 14.62 2H19.37C21.25 2 22 2.75 22 4.62Z"

/** Iconsax「Ruler Pen」：右侧尺身 + 左侧笔身（都是 40%）。 */
private const val RULER_PEN_BODY =
    "M21.4702 5V19C21.4702 20.66 20.1202 22 18.4702 22H14.4702C12.8102 22 11.4702 20.66 11.4702 19V5C11.4702 3.34 12.8102 2 14.4702 2H18.4702C20.1202 2 21.4702 3.34 21.4702 5ZM8.43979 4.95V17.91C8.43979 18.36 8.24979 19.05 8.01979 19.43L7.19979 20.79C6.25979 22.37 4.71979 22.37 3.76979 20.79L2.95979 19.43C2.71979 19.05 2.52979 18.36 2.52979 17.91V4.95C2.52979 3.33 3.85978 2 5.48978 2C7.10979 2 8.43979 3.33 8.43979 4.95Z"

/** Iconsax「Ruler Pen」：五道刻度（不透明）。 */
private const val RULER_PEN_TICKS =
    "M17.2202 6C17.2202 6.41 16.8802 6.75 16.4702 6.75H11.4702V5.25H16.4702C16.8802 5.25 17.2202 5.59 17.2202 6ZM15.0302 10C15.0302 10.41 14.6902 10.75 14.2802 10.75H11.4702V9.25H14.2802C14.6902 9.25 15.0302 9.59 15.0302 10ZM17.2202 14.0092C17.2102 14.4192 16.8802 14.7492 16.4702 14.7492C16.4602 14.7492 16.4602 14.7492 16.4602 14.7492L11.4702 14.6992V13.1992L16.4702 13.2492C16.8902 13.2492 17.2202 13.5892 17.2202 14.0092ZM16.1202 18C16.1202 18.41 15.7902 18.75 15.3702 18.75H11.4702V17.25H15.3702C15.7902 17.25 16.1202 17.59 16.1202 18ZM8.43979 6.25H2.52979V7.75H8.43979V6.25Z"

/** Iconsax「Brush 4」：刷头（不透明）。 */
private const val BRUSH4_HEAD =
    "M15.5194 2.9C14.9194 2.3 14.0494 2 12.9294 2H7.34938C5.09938 2 3.85938 3.24 3.85938 5.49C3.85938 6.61 4.15938 7.48 4.75938 8.08C5.35938 8.68 6.22937 8.98 7.34938 8.98H12.9294C15.1794 8.98 16.4194 7.74 16.4194 5.49C16.4194 4.37 16.1194 3.5 15.5194 2.9Z"

/** Iconsax「Brush 4」：连接管 + 手柄（40%）。 */
private const val BRUSH4_REST =
    "M10.6001 15.02H9.67008C8.72008 15.02 7.58008 15.38 7.58008 17.11V19.9C7.58008 21.63 8.72008 21.99 9.67008 21.99H10.6001C11.5501 21.99 12.6901 21.63 12.6901 19.9V17.11C12.7001 15.39 11.5601 15.02 10.6001 15.02ZM16.6492 4.79004H16.3692C16.3992 5.01004 16.4192 5.24004 16.4192 5.49004C16.4192 5.74004 16.3992 5.97004 16.3692 6.19004H16.6492C18.7392 6.19004 18.7392 6.47004 18.7392 8.28004V10.14C18.7392 11.95 18.7392 12.23 16.6492 12.23H12.9292C9.93922 12.23 9.50922 13.65 9.44922 15.03C9.51922 15.03 9.59922 15.02 9.66922 15.02H10.5992C10.6792 15.02 10.7592 15.03 10.8492 15.03C10.8992 14.02 11.1892 13.62 12.9292 13.62H16.6492C19.8992 13.62 20.1392 12.3 20.1392 10.13V8.28004C20.1392 6.11004 19.8992 4.79004 16.6492 4.79004Z"

/** Iconsax「Edit」：圆角方底（40%）。 */
private const val EDIT_BG =
    "M15.48 3H7.52C4.07 3 2 5.06 2 8.52V16.47C2 19.94 4.07 22 7.52 22H15.47C18.93 22 20.99 19.94 20.99 16.48V8.52C21 5.06 18.93 3 15.48 3Z"

/** Iconsax「Edit」：铅笔笔尖 + 笔身（不透明）。 */
private const val EDIT_PENCIL =
    "M21.0195 2.97979C19.2295 1.17979 17.4795 1.13979 15.6395 2.97979L14.5095 4.09979C14.4095 4.19979 14.3795 4.33979 14.4195 4.46979C15.1195 6.91979 17.0795 8.87979 19.5295 9.57979C19.5595 9.58979 19.6095 9.58979 19.6395 9.58979C19.7395 9.58979 19.8395 9.54979 19.9095 9.47979L21.0195 8.35979C21.9295 7.44979 22.3795 6.57979 22.3795 5.68979C22.3795 4.78979 21.9295 3.89979 21.0195 2.97979ZM17.8591 10.4198C17.5891 10.2898 17.3291 10.1598 17.0891 10.0098C16.8891 9.88984 16.6891 9.75984 16.4991 9.61984C16.3391 9.51984 16.1591 9.36984 15.9791 9.21984C15.9591 9.20984 15.8991 9.15984 15.8191 9.07984C15.5091 8.82984 15.1791 8.48984 14.8691 8.11984C14.8491 8.09984 14.7891 8.03984 14.7391 7.94984C14.6391 7.83984 14.4891 7.64984 14.3591 7.43984C14.2491 7.29984 14.1191 7.09984 13.9991 6.88984C13.8491 6.63984 13.7191 6.38984 13.5991 6.12984C13.4691 5.84984 13.3691 5.58984 13.2791 5.33984L7.89912 10.7198C7.54912 11.0698 7.20912 11.7298 7.13912 12.2198L6.70912 15.1998C6.61912 15.8298 6.78912 16.4198 7.17912 16.8098C7.50912 17.1398 7.95912 17.3098 8.45912 17.3098C8.56912 17.3098 8.67912 17.2998 8.78912 17.2898L11.7591 16.8698C12.2491 16.7998 12.9091 16.4698 13.2591 16.1098L18.6391 10.7298C18.3891 10.6498 18.1391 10.5398 17.8591 10.4198Z"
