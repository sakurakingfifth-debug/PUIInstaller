package com.panl.pui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * 页面悬浮栏（底部悬浮 Tab 栏）：整条 NavBar 触摸时像 iOS 一样弹跳，上下左右都会被手指拉伸。
 *
 * 结构（两层，都在同一条 NavBar 上）：
 *   ① 模糊层：「页面图层自带原生 RenderEffect 模糊 + 跟随 NavBar 的胶囊蒙版」（见 NavBackdrop），
 *      蒙版随 NavBar 一起平移 / 拉伸 / 压扁，所以被拉出来的地方同样有模糊；
 *   ② 玻璃层：填色跟随主题明暗（亮 #B3FFFFFF / 暗 #B3000000）+ 一道很淡的顶部高光 + 细描边。
 *      填色的 alpha 决定模糊的可见度：0xFF 会把 ① 完全遮住（渲染照常，肉眼看不到）；
 *      当前 0xB3，模糊清晰可见；想更隐蔽就调大 alpha，不用改别的地方。
 *   没有选中背景（showLens = false），选中状态由文字 / 图标颜色表达。
 *
 * 弹跳（整条 NavBar）：
 *  - 按下：整体轻微放大（1.05），并朝按下的位置（左右、上下）「顶」一下，再弹簧回弹；
 *  - 拖动：远端固定、近端跟着手指走；手指拖到 NavBar 之外越远、拖得越快，拉得越长；
 *          横向最多 MAX_PULL_X（18%），纵向最多 MAX_PULL_Y（35%）；
 *          横向拉长时纵向略压扁，纵向拉长时横向略收窄；
 *  - 松手：两个方向各自带着当前速度回弹，过冲到另一侧再晃几下，最后回正（点按同理）；
 *  - 越过 Tab 分界有触感反馈；标签反向缩放，文字 / 图标不随 NavBar 变形；
 *  - 外部切换页面（滑动 Pager 等）时，当前 Tab 文字颜色平滑跟过去。
 *
 * 弹簧参数取自 PUI 示例 APK 里的两组回弹弹簧（反编译常量）：
 *   阻尼 0.45 / 刚度 246.74（响应 0.4s）、阻尼 0.40 / 刚度 157.91（响应 0.5s）；另有不回弹的 1.0 / 322.27。
 *
 * 接入（NavBar 必须和「页面内容」是兄弟，不能放在它里面）：
 *
 *     val backdrop = rememberNavBackdrop()
 *     Box(Modifier.fillMaxSize()) {
 *         HorizontalPager(
 *             modifier = Modifier.fillMaxSize()
 *                 .navBackdropSource(backdrop)      // 录位置（放最外层）
 *                 .puiProgressiveTopBlur(blurH, backdrop = backdrop),
 *             ...
 *         )
 *         PuiFloatingNavBar(..., backdrop = backdrop, modifier = Modifier.align(BottomCenter))
 *     }
 *   不传 backdrop 时没有模糊，退化为一层半透明 surface 色，以免背后内容直接透出来。
 *
 * 跨平台说明：
 *  - 蒙版与 RenderEffect 是 Android 专有的，放在 androidMain 的 actual 里；
 *    桌面 / Android 13 以下 [PuiPlatformSupportsBlur] = false，玻璃填色自动调高到接近不透明，
 *    观感仍然成立（只是没有「透过毛玻璃」的效果）。
 *  - 触感用 [rememberTickHaptic]（CLOCK_TICK），和库里的滑块刻度一致。
 */

/** 悬浮栏尺寸：页面底部留白和预览都用它，必须和 PuiFloatingNavBar 的默认值一致。 */
object PuiBarMetrics {
    /** 2 个 Tab 时的整条宽度（88 × 2 + 6 × 2）。 */
    val width = 188.dp
    val height = 56.dp
    val itemWidth = 88.dp
    val bottomMargin = 14.dp
    val padding = 6.dp

    fun widthFor(count: Int): Dp = itemWidth * count + padding * 2
}

/** NavBar 左右 / 上下回弹：阻尼 0.45，响应 0.4s（会过冲到另一侧再回正）。 */
private val BarBounceSpring = spring<Float>(dampingRatio = 0.45f, stiffness = 246.74f)

/** NavBar 按下放大 / 松手回正：阻尼 0.40，响应 0.5s。 */
private val BarPressSpring = spring<Float>(dampingRatio = 0.40f, stiffness = 157.91f)

/** 选中背景 / 文字颜色：不回弹。阻尼 1.0，响应 0.35s。 */
private val SettleSpring = spring<Float>(dampingRatio = 1f, stiffness = 322.27f)
private val SettleColorSpring = spring<Color>(dampingRatio = 1f, stiffness = 322.27f)

/** 拖动时 NavBar 的拉伸比例上限：横向 18%，纵向 35%（NavBar 较矮，纵向给得更大）。 */
private const val MAX_PULL_X = 0.18f
private const val MAX_PULL_Y = 0.35f

/** 按下时的整体放大倍数。 */
private const val PRESS_SCALE = 1.05f

/**
 * 玻璃本体的填色：亮色 `#B3FFFFFF` / 暗色 `#B3000000`。
 *
 * alpha = `0xB3`（179/255 ≈ 70%），剩下约 30% 让下面那层原生高斯模糊透出来。
 * alpha 就是「模糊可见度」的总开关：调到 `0xFF` 就完全遮住，调到 `0xF0` 模糊只隐约可见。
 */
private val GLASS_FILL_LIGHT = Color(0xB3FFFFFF)
private val GLASS_FILL_DARK = Color(0xB3000000)

/** 平台不支持模糊时的填色：几乎不透明（98%），避免背后文字直接透出来显脏。 */
private val GLASS_FILL_LIGHT_OPAQUE = Color(0xFAFFFFFF)
private val GLASS_FILL_DARK_OPAQUE = Color(0xFA000000)

/**
 * 底部投影：只在**下沿**铺，上 / 左 / 右都不铺。
 *
 * 亮色用淡灰（黑）、暗色用淡白（白），叠在页面上自然成为对应的浅色。
 *
 * 三个参数共同决定「多小、多柔」：
 *  - [BOTTOM_SHADOW_HEIGHT]：总下沉量。越小越贴边、沿圆角绕得越低；
 *  - [BOTTOM_SHADOW_LAYERS]：同形状逐层下沉再叠加，每层都是硬边，
 *    叠起来后靠近栏体处累积最浓、往外递减 —— 这是用纯绘制模拟高斯发散；
 *  - [BOTTOM_SHADOW_EDGE_FADE]：两端淡出比例，让阴影在碰到左右边缘之前就消失，
 *    否则胶囊的圆角会把阴影「带」到左右两侧，看起来像描边。
 */
private val BOTTOM_SHADOW_LIGHT = Color.Black
private val BOTTOM_SHADOW_DARK = Color.White
private val BOTTOM_SHADOW_HEIGHT = 3.dp
private const val BOTTOM_SHADOW_LAYERS = 4
private const val BOTTOM_SHADOW_EDGE_FADE = 0.14f

/** 每层透明度：叠加 4 层后，靠近栏体处约为 0.11（亮）/ 0.12（暗）。 */
private const val BOTTOM_SHADOW_ALPHA_LIGHT = 0.0275f
private const val BOTTOM_SHADOW_ALPHA_DARK = 0.0325f

// ───────────────────────── 模糊背景源 ─────────────────────────

/**
 * NavBar 毛玻璃的共享状态。
 *
 * 页面内容那一层（pager）自己带一个 RenderEffect —— 系统原生高斯模糊 + 胶囊蒙版，
 * 蒙版跟着 NavBar 的位置 / 拉伸 / 压扁走，所以只有 NavBar 后面那一块是糊的，其余部分保持清晰。
 * NavBar 把自己的几何信息写进这里，pager 的 graphicsLayer 读它。
 *
 * 这里只放**跨平台的状态**；真正的蒙版位图与 RenderEffect 组合在 Android 的 actual 里
 * （[maskCache] 就是给那边缓存 Bitmap 用的槽位）。
 */
class NavBackdrop internal constructor(
    /** 原生高斯模糊半径（px）。可改：改它会让 pager 图层失效并重新渲染。 */
    blurPx: Float
) {
    internal var blurPx by mutableStateOf(blurPx)

    /** 页面内容区左上角在根坐标系中的位置。 */
    internal var origin by mutableStateOf(Offset.Zero)

    /** NavBar（未变形）左上角在根坐标系中的位置。 */
    internal var barOrigin by mutableStateOf(Offset.Zero)
    internal var barSize by mutableStateOf(IntSize.Zero)

    /** NavBar 的弹跳状态；pager 的 graphicsLayer 读 .value，所以拉伸动画的每一帧都会刷新蒙版。 */
    internal var pullX by mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
    internal var pullY by mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
    internal var press by mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)

    /** 平台侧的蒙版缓存（Android 存 Bitmap），桌面恒为 null。 */
    internal var maskCache: Any? = null
}

/** 创建毛玻璃源。[blurRadius] 是系统原生高斯模糊的半径。 */
@Composable
fun rememberNavBackdrop(blurRadius: Dp = 24.dp): NavBackdrop {
    val density = LocalDensity.current
    val px = with(density) { blurRadius.toPx() }
    return remember(px) { NavBackdrop(px) }
}

/** 套在「NavBar 后面的页面内容」上：只负责报告内容区位置，NavBar 不能放在它里面。 */
fun Modifier.navBackdropSource(backdrop: NavBackdrop): Modifier =
    this.onGloballyPositioned { backdrop.origin = it.positionInRoot() }

// ───────────────────────── 变形公式 ─────────────────────────
// pullX / pullY 带符号：正 = 向右 / 向下拉伸，负 = 向左 / 向上拉伸；弹簧过冲时会自然晃到另一侧。

internal fun barScaleX(px: Float, py: Float, press: Float) =
    (1f + abs(px)) * (1f - abs(py) * 0.15f) * press

internal fun barScaleY(px: Float, py: Float, press: Float) =
    (1f + abs(py)) * (1f - abs(px) * 0.25f) * press

// ───────────────────────── NavBar ─────────────────────────

@Composable
fun PuiFloatingNavBar(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 页面内容的模糊源；不传就没有模糊，退化为一层半透明底色。半径在 rememberNavBackdrop 里设。 */
    backdrop: NavBackdrop? = null,
    /** 默认关闭：NavBar 只有一层玻璃，没有选中背景。 */
    showLens: Boolean = false,
    itemWidth: Dp = PuiBarMetrics.itemWidth,
    barHeight: Dp = PuiBarMetrics.height,
    /** 每个 Tab 的图标；不传就按索引兜底。 */
    icons: List<PuiIcon> = emptyList(),
    /**
     * 自定义图标槽：传了就不用 [icons]。
     * 给的是 (下标, 当前着色)，调用方可以塞 ImageVector / 矢量资源，不再受 [PuiIcon] 枚举限制。
     */
    iconContent: (@Composable (index: Int, tint: Color) -> Unit)? = null,
    /**
     * 玻璃底色不透明度倍率，0f = 全透明、1f = 预设值。
     * 只作用于填充色，模糊和描边 / 投影不受影响。
     */
    glassAlpha: Float = 1f,
) {
    require(items.isNotEmpty()) { "items 不能为空" }
    val n = items.size
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val tick = rememberTickHaptic()

    val padding = PuiBarMetrics.padding
    val itemW = with(density) { itemWidth.toPx() }
    val padPx = with(density) { padding.toPx() }
    val maxSlot = itemW * (n - 1)

    // 整条 NavBar 的弹跳
    val pullX = remember { Animatable(0f) }
    val pullY = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    // 可选的选中背景位置（showLens 才用）
    val lens = remember { Animatable(selectedIndex.coerceIn(0, n - 1) * itemW) }
    var dragging by remember { mutableStateOf(false) }
    var hoverIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, n - 1)) }
    // NavBar（未变形的外层）左上角在根坐标系中的位置
    var barOrigin by remember { mutableStateOf(Offset.Zero) }

    // 把弹跳状态交给 backdrop：pager 图层里的模糊蒙版要跟着 NavBar 一起拉伸
    SideEffect {
        backdrop?.pullX = pullX
        backdrop?.pullY = pullY
        backdrop?.press = press
    }

    // 触点光效
    var glowPos by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    val glowAlpha by animateFloatAsState(
        targetValue = if (touching) 0.30f else 0f,
        animationSpec = tween(durationMillis = if (touching) 140 else 260),
        label = "navGlow",
    )

    // 选中背景的透明度：按下时略微变亮（它不放大、不回弹，只负责跟手和落位）
    val lensAlpha by animateFloatAsState(
        targetValue = if (touching) 0.26f else 0.16f,
        animationSpec = tween(durationMillis = if (touching) 120 else 240),
        label = "lensAlpha",
    )

    // pointerInput 的 key 只有 (n, itemW)，手势块只在首次组合时创建一次，
    // 直接读 selectedIndex / onSelect 会一直是旧值（滑到第 2 页后点第 1 个 Tab 不切页）。
    // 一律用 rememberUpdatedState 保证读到最新。
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // 外部切换页面：当前 Tab 平滑跟过去
    LaunchedEffect(selectedIndex, itemW) {
        if (!dragging) {
            val target = selectedIndex.coerceIn(0, n - 1)
            hoverIndex = target
            if (showLens) lens.animateTo(target * itemW, SettleSpring)
        }
    }

    /** 手指 x → 所在槽位左边缘（夹在首尾之间）。 */
    fun slotLeft(x: Float) = (x - padPx - itemW / 2f).coerceIn(0f, maxSlot)

    fun nearest(x: Float): Int = ((x + itemW / 2f) / itemW).toInt().coerceIn(0, n - 1)

    val shape = RoundedCornerShape(percent = 50)
    val colors = MaterialTheme.colorScheme
    // 跟随**主题**明暗（不是系统明暗）：演示页里可以在 App 内切深浅
    val dark = colors.background.luminance() < 0.5f
    val canBlur = backdrop != null && PuiPlatformSupportsBlur
    val glassBase = when {
        !canBlur && dark -> GLASS_FILL_DARK_OPAQUE
        !canBlur -> GLASS_FILL_LIGHT_OPAQUE
        dark -> GLASS_FILL_DARK
        else -> GLASS_FILL_LIGHT
    }
    // 用户可调的不透明度：乘在预设 alpha 上，0% 时完全透明
    val glassFill = glassBase.copy(alpha = glassBase.alpha * glassAlpha.coerceIn(0f, 1f))
    // 填色是纯白 / 纯黑，描边得反着来才看得见：亮色用淡黑细线，暗色用淡白细线
    val glassEdge = if (dark) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.08f)
    // 底部投影：亮色淡灰、暗色淡白
    val bottomShadow = if (dark) BOTTOM_SHADOW_DARK else BOTTOM_SHADOW_LIGHT
    val bottomShadowAlpha = if (dark) BOTTOM_SHADOW_ALPHA_DARK else BOTTOM_SHADOW_ALPHA_LIGHT

    // 外层：只负责收手势、报位置，本身不变形（手势坐标稳定，拉伸不会把触摸区一起拉走）
    Box(
        modifier
            .width(itemWidth * n + padding * 2)
            .height(barHeight)
            .onGloballyPositioned {
                barOrigin = it.positionInRoot()
                backdrop?.barOrigin = barOrigin
                backdrop?.barSize = it.size
            }
            .pointerInput(n, itemW) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val halfW = w / 2f
                    val halfH = h / 2f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    dragging = true

                    /** 手指位置 + 速度 → NavBar 的横向拉伸量（带符号）。 */
                    fun pullXFor(x: Float, vx: Float): Float {
                        val base = ((x - halfW) / halfW).coerceIn(-2f, 2f) * 0.07f
                        val vel = (vx / 8000f).coerceIn(-1f, 1f) * 0.05f
                        return (base + vel).coerceIn(-MAX_PULL_X, MAX_PULL_X)
                    }

                    /** 手指位置 + 速度 → NavBar 的纵向拉伸量；手指在 NavBar 之外越远拉得越长。 */
                    fun pullYFor(y: Float, vy: Float): Float {
                        val base = ((y - halfH) / halfH).coerceIn(-5f, 5f) * 0.06f
                        val vel = (vy / 8000f).coerceIn(-1f, 1f) * 0.05f
                        return (base + vel).coerceIn(-MAX_PULL_Y, MAX_PULL_Y)
                    }

                    // 按下：整体放大一点，并朝按下的位置顶一下。
                    // 记住按下位置 —— 松手时用它判目标索引，不依赖任何动画进度（快速点按也准）。
                    var lastX = down.position.x
                    touching = true
                    glowPos = down.position
                    hoverIndex = nearest(slotLeft(lastX))
                    scope.launch { press.animateTo(PRESS_SCALE, BarPressSpring) }
                    scope.launch { pullX.animateTo(pullXFor(lastX, 0f), BarBounceSpring) }
                    scope.launch { pullY.animateTo(pullYFor(down.position.y, 0f), BarBounceSpring) }
                    if (showLens) scope.launch { lens.animateTo(slotLeft(lastX), SettleSpring) }

                    var lastPX = pullX.value
                    var lastPY = pullY.value
                    var lastT = down.uptimeMillis
                    var velX = 0f
                    var velY = 0f

                    drag(down.id) { change ->
                        lastX = change.position.x
                        touching = true
                        glowPos = change.position
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val v = tracker.calculateVelocity()
                        // 低通：NavBar 略带滞后地被拉着走，有橡皮筋的粘滞感
                        val nx = lastPX + (pullXFor(change.position.x, v.x) - lastPX) * 0.4f
                        val ny = lastPY + (pullYFor(change.position.y, v.y) - lastPY) * 0.4f
                        val dt = (change.uptimeMillis - lastT).coerceAtLeast(1L) / 1000f
                        velX = (nx - lastPX) / dt
                        velY = (ny - lastPY) / dt
                        lastPX = nx
                        lastPY = ny
                        lastT = change.uptimeMillis
                        val lx = slotLeft(lastX)
                        scope.launch {
                            pullX.snapTo(nx)
                            pullY.snapTo(ny)
                            if (showLens) lens.snapTo(lx)
                        }
                        val idx = nearest(lx)
                        if (idx != hoverIndex) {
                            hoverIndex = idx
                            tick()
                        }
                        change.consume()
                    }

                    // 松手：两个方向各自带着当前速度回弹（过冲到另一侧再晃回正）
                    touching = false
                    val releaseV = tracker.calculateVelocity().x
                    val idx = nearest(slotLeft(lastX) + releaseV * 0.08f)
                    hoverIndex = idx
                    dragging = false
                    scope.launch { press.animateTo(1f, BarPressSpring) }
                    scope.launch { pullX.animateTo(0f, BarBounceSpring, initialVelocity = velX) }
                    scope.launch { pullY.animateTo(0f, BarBounceSpring, initialVelocity = velY) }
                    if (showLens) scope.launch { lens.animateTo(idx * itemW, SettleSpring) }
                    if (idx != currentSelected) currentOnSelect(idx)
                }
            }
    ) {
        // 内层：整条 NavBar（毛玻璃 + 标签）一起变形。模糊画在这一层里面，所以拉伸出去的部分同样有模糊。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val px = pullX.value
                    val py = pullY.value
                    scaleX = barScaleX(px, py, press.value)
                    scaleY = barScaleY(px, py, press.value)
                    translationX = px * size.width / 2f   // 远端固定、近端跟着手指走
                    translationY = py * size.height / 2f
                }
                // 底部投影：把同一个胶囊逐层向下平移、叠着画在背后，露出来的月牙就是「只有底部的阴影」。
                // 不能用 Modifier.shadow —— 它是四周均匀扩散的，做不到「上左右不加」。
                //
                // 柔化靠两层手段叠加：
                //  ① 多层不同下沉量叠加 —— 靠近栏体处被覆盖最多次、最浓，往外递减；
                //  ② 每层再套一道左右淡出的横向渐变 —— 阴影在碰到左右边缘前就没了，
                //     否则胶囊圆角会把阴影「带」到两侧，看着像描边。
                // 位置放在 clip 之前，所以这一层不被胶囊裁掉（否则往下多出来的部分会被切平）。
                .drawBehind {
                    val spread = BOTTOM_SHADOW_HEIGHT.toPx()
                    val r = size.height / 2f            // 胶囊半径 = 高度的一半
                    val step = spread / BOTTOM_SHADOW_LAYERS
                    val solid = bottomShadow.copy(alpha = bottomShadowAlpha)
                    val clear = bottomShadow.copy(alpha = 0f)
                    for (i in 1..BOTTOM_SHADOW_LAYERS) {
                        val d = step * i
                        val capsule = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(0f, d, size.width, size.height + d),
                                    radiusX = r,
                                    radiusY = r,
                                )
                            )
                        }
                        drawPath(
                            capsule,
                            Brush.horizontalGradient(
                                0f to clear,
                                BOTTOM_SHADOW_EDGE_FADE to solid,
                                (1f - BOTTOM_SHADOW_EDGE_FADE) to solid,
                                1f to clear,
                                startX = 0f,
                                endX = size.width,
                            )
                        )
                    }
                }
                // clip 放在阴影之后：阴影不被裁，底色 / 高光 / 光效 / 子内容都裁在胶囊里
                .clip(shape)
        ) {
            // 模糊不在这里画：pager 的图层自带「NavBar 形状蒙版 + 原生模糊」，透过下面半透明的玻璃本体显出来。

            // 玻璃本体：填色 + 一道很淡的顶部高光 + 细描边
            Box(
                Modifier
                    .fillMaxSize()
                    .background(glassFill, shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)
                        ),
                        shape,
                    )
                    .border(0.5.dp, glassEdge, shape)
            )

            // 第二层：选中背景 —— 半透明着色胶囊，只负责跟手 / 落位，不放大、不回弹，按下只略微变亮
            if (showLens) {
                Box(
                    Modifier
                        .padding(padding)
                        .offset { IntOffset(lens.value.roundToInt(), 0) }
                        .size(itemWidth, barHeight - padding * 2)
                        .background(colors.primary.copy(alpha = lensAlpha), shape)
                )
            }

            // 触点光效：画在玻璃之上、标签之下。位置要从「外层坐标」反算回内层局部坐标，
            // 否则 NavBar 一拉伸光斑就跟着跑偏。
            if (glowAlpha > 0.01f) {
                Canvas(Modifier.fillMaxSize()) {
                    val px = pullX.value
                    val py = pullY.value
                    val sx = barScaleX(px, py, press.value)
                    val sy = barScaleY(px, py, press.value)
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val tx = px * size.width / 2f
                    val ty = py * size.height / 2f
                    val local = Offset(
                        cx + (glowPos.x - cx - tx) / sx,
                        cy + (glowPos.y - cy - ty) / sy
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = glowAlpha),
                                Color.White.copy(alpha = glowAlpha * 0.45f),
                                Color.White.copy(alpha = 0f)
                            ),
                            center = local,
                            radius = 76.dp.toPx()
                        ),
                        radius = 76.dp.toPx(),
                        center = local
                    )
                }
            }

            // 标签：反向缩放，文字 / 图标不随 NavBar 变形
            Row(
                Modifier
                    .padding(padding)
                    .graphicsLayer {
                        scaleX = 1f / barScaleX(pullX.value, pullY.value, press.value)
                        scaleY = 1f / barScaleY(pullX.value, pullY.value, press.value)
                    }
            ) {
                items.forEachIndexed { i, label ->
                    val color by animateColorAsState(
                        if (i == hoverIndex) colors.primary else colors.onSurface.copy(alpha = 0.72f),
                        animationSpec = SettleColorSpring,
                        label = "navLabel$i",
                    )
                    Column(
                        Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .semantics(mergeDescendants = true) {
                                role = Role.Tab
                                selected = i == hoverIndex
                                onClick {
                                    currentOnSelect(i)
                                    true
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // ColorOS 17 风格：图标在上、文字在下
                        if (iconContent != null) {
                            iconContent(i, color)
                        } else {
                            PuiIconView(
                                icon = icons.getOrElse(i) {
                                    if (i == 0) PuiIcon.Element else PuiIcon.Brush
                                },
                                tint = color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(text = label, color = color, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/** 顶部模糊带高度 = 状态栏高度 + [extra]。 */
@Composable
fun rememberPuiTopBlurHeight(extra: Dp = 64.dp): Dp = rememberStatusBarHeight() + extra

/**
 * 顶部渐隐遮罩：从 [color]（默认 surface）的 [topAlpha] 渐变到完全透明，与模糊区域等高。
 * 放在页面根 Box 里、滚动内容**之后**、导航栏**之前**。
 *
 * 参数与原版 `PuiTopBlur.kt` 保持一致：surface + 0.69。
 */
@Composable
fun BoxScope.PuiTopFade(
    height: Dp,
    color: Color = MaterialTheme.colorScheme.surface,
    topAlpha: Float = 0.69f
) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = topAlpha), color.copy(alpha = 0f))
                )
            )
    )
}

/** 页面底部留白：让内容能滚出导航栏的遮挡。 */
val PuiNavContentPadding: Dp = 104.dp
