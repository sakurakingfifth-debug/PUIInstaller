package com.panl.installer.ui.install

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.panl.installer.R
import com.panl.installer.core.ApkInfo
import com.panl.installer.core.ApkDownloader
import com.panl.installer.ui.components.InstallInfoChips
import com.panl.installer.core.Formatters
import com.panl.installer.core.Settings
import com.panl.installer.core.VersionRelation
import com.panl.installer.ui.InstallActivity
import com.panl.installer.ui.components.AppIconView
import com.panl.installer.ui.components.BottomActionBar
import com.panl.installer.ui.components.PuiIndeterminateBar
import com.panl.installer.ui.components.MarqueeText
import com.panl.installer.ui.components.RelationBadge
import com.panl.installer.ui.components.sheetMaxWidth
import com.panl.installer.ui.components.sheetWidth
import com.panl.installer.ui.components.StatusGlyph
import com.panl.installer.ui.components.puiCardBackdropBlur
import com.panl.pui.PuiButton
import com.panl.pui.PuiChip
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiCard
import com.panl.pui.PuiDialog
import com.panl.pui.PuiDivider
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconButton
import com.panl.pui.PuiIconView
import com.panl.pui.PuiProgressBar
import com.panl.pui.PuiSegmentedControl
import androidx.compose.material3.Icon
import com.panl.installer.i18n.tr

/**
 * 浮窗式安装页。
 *
 * 视觉参考 ColorOS 的安装弹窗：窗口本身半透明，系统对身后界面做背景模糊，
 * 卡片贴在底部（或居中）浮起来，顶部一根拖拽条 + 关闭/标题/设置三段式头部。
 *
 * 与全屏版的区别只在**容器**：信息密度压缩（版本改成两行紧凑卡、签名只留风险提示），
 * 交互与状态机完全复用 [InstallController]。
 */
@Composable
fun InstallSheet(controller: InstallController, onClose: () -> Unit) {
    val context = LocalContext.current
    // 错误页的「重新选择文件」：换一个文件重新读
    val reselect = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { picked -> picked?.let { controller.openPicked(it) } }

    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    val densityObj = LocalDensity.current
    val state = controller.state
    val atBottom = settings.sheetPosition != "Center"

    // ── 拖动展开 ────────────────────────────────────────────────────────────
    // 0 = 浮窗，1 = 全屏样式。用 Animatable 而不是普通 state：
    // 松手后的回弹/展开动画要能被下一次按下打断（stop()），拖到一半往回拖也能立刻改写进度。
    // 进度用普通 state：拖动时同步写（每帧直接改），松手再用 animate() 补到 0/1。
    // 用 Animatable + 每个拖动事件 launch 一个 snapTo 会互相踩，最后一步动画会被吃掉，停在半路。
    var expandP by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    // 浮窗贴住内容时的自然高度：展开动画拿它当起点（第一帧量到之前先不插手布局）
    var naturalH by remember { mutableStateOf(0.dp) }
    // 拖满屏需要的距离。卡片本来就贴着屏幕中线，手指没多少向上的余量，
    // 定得太大就会出现「怎么拖都展不开」；140dp ≈ 半个卡片高，提一下就到位。
    val expandSpan = 140.dp
    val expandSpanPx = with(LocalDensity.current) { expandSpan.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        // 弹簧回弹会越过 1（过冲），直接拿去算 padding/圆角会变成负数 → 崩。
        // 这里统一夹紧；过冲的那点视觉损失可以忽略。
        val p = expandP.coerceIn(0f, 1f)
        // 两套内容交叉淡入淡出：紧凑版先退，全屏版后半程再进，中途不会两块都糊着
        val sheetAlpha = 1f - smoothStep(0.30f, 0.80f, p)
        val pageAlpha = smoothStep(0.45f, 0.95f, p)

        // 浮窗 → 全屏的几何插值
        val margin = if (atBottom) 0.dp else 14.dp
        val sheetW = minOf(sheetMaxWidth(), screenW - margin * 2)
        val cardW = androidx.compose.ui.unit.lerp(sheetW, screenW, p)
        val cardH = androidx.compose.ui.unit.lerp(naturalH, screenH, p)
        val radius = androidx.compose.ui.unit.lerp(30.dp, 0.dp, p)
        val bottomPad = androidx.compose.ui.unit.lerp(22.dp, 0.dp, p)
        // 贴底模式让开系统栏的那点留白，展开时要收回去，否则全屏后底部会多出一条
        val navPad = with(densityObj) {
            androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(this).toDp()
        } * (1f - p)

        // 遮罩：系统级模糊已经糊过一遍，这里只压一层很淡的黑。
        // 删除确认弹出来时压深一点 —— 弹窗窗口的 dim 只作用在浮窗自己这层上，
        // 身后的应用不会变暗，靠这层把整屏一起压下去才像真正的模态。
        val dim by androidx.compose.animation.core.animateFloatAsState(
            targetValue = when {
                settings.noScrim -> 0f
                controller.deleteDialogVisible -> 0.42f
                else -> 0.18f
            },
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
            label = "sheet-scrim"
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim * (1f - p)))
                .pointerInput(Unit) { detectTapGestures { onClose() } }
        )

        val shape = RoundedCornerShape(
            topStart = radius,
            topEnd = radius,
            bottomEnd = if (atBottom) 0.dp else radius,
            bottomStart = if (atBottom) 0.dp else radius
        )

        Box(
            Modifier
                .align(if (atBottom) Alignment.BottomCenter else Alignment.Center)
                // 系统栏留白只加在**贴底**的时候：让卡片避开手势条 / 桌面模式的任务栏。
                // 居中时千万别让 —— 一让整张卡就被顶上去，屏幕上下的留白不对称，看着就是「整体不居中」。
                // 也不能加在卡片内部：那样按钮行会把底部 inset 当成自己的空白，按钮被顶上、
                // 卡片下半截空一大块（3.7.53 及以前就是这么错的）。
                .then(if (atBottom) Modifier.padding(bottom = navPad) else Modifier)
                // 尺寸：p=0 时还是原来的「收窄 + 贴合内容」；一开始拖就切成显式宽高，
                // 一路插值到铺满整屏（对齐方式不变，所以看起来是从中间长开的）
                .then(
                    if (p > 0f && naturalH > 0.dp) {
                        Modifier.width(cardW).height(cardH)
                    } else {
                        Modifier.padding(horizontal = margin).sheetWidth()
                    }
                )
                .onSizeChanged {
                    // 只在收起状态量：展开时的尺寸是我们自己设的，记下来会把起点带偏
                    if (expandP == 0f) naturalH = with(densityObj) { it.height.toDp() }
                }
                // 删除确认弹出来时，整张卡片先糊掉：不然白底弹窗叠在白底卡片上分不出层次，
                // 看起来像「弹窗被塞进了卡片里」（用户报的就是这个）
                .puiCardBackdropBlur(controller.deleteDialogVisible, (settings.sheetBlurPx / density).dp)
                .clip(shape)
                .background(
                    androidx.compose.ui.graphics.lerp(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        MaterialTheme.colorScheme.background,
                        smoothStep(0.5f, 1f, p)
                    )
                )
                // 吃掉卡片上的点击，避免穿透到遮罩
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            // 内容层。展开时铺满整张卡，拖拽条浮在它上面（不占布局高度），
            // 否则整页内容会少掉 16dp，版本卡底部正好被卡片的圆角裁掉。
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (p > 0f) Modifier.fillMaxHeight() else Modifier)
                    // 卡片内统一留底：居中时曾经是 0，按钮直接压在卡片下边缘上
                    .padding(bottom = bottomPad)
            ) {
                Spacer(Modifier.height(androidx.compose.ui.unit.lerp(16.dp, 0.dp, p)))

            // 卡片高度**跟着内容走**：以前这里写死 470dp，平板横屏时卡片比内容高出一大截，
            // 按钮下面空出一条（用户圈出来的那块）；各状态内容高度本来就不一样，写死没有意义。
            // 只留一个上限：窗口太矮（手机横屏、桌面模式小窗）时别让卡片超出屏幕。
            BoxWithConstraints(
                Modifier.fillMaxWidth().then(if (p > 0f) Modifier.weight(1f) else Modifier)
            ) {
                val maxContentHeight =
                    if (maxHeight.value.isFinite()) minOf(SHEET_MAX_CONTENT_H, maxHeight) else SHEET_MAX_CONTENT_H
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (p > 0f) Modifier.fillMaxHeight()
                        else Modifier.heightIn(max = maxContentHeight)
                    ),
                contentAlignment = Alignment.Center
            ) {

            // 紧凑版（浮窗原样）。展开时只淡出，不参与后面的布局计算
            Box(Modifier.fillMaxWidth().graphicsLayer { alpha = sheetAlpha }) {

            // 和全屏版同一套思路，按状态类型去重（进度刷新不触发转场），
            // 额外用 SizeTransform 把卡片高度也一起补间，否则换页时卡片会瞬间弹一下
            androidx.compose.animation.AnimatedContent(
                targetState = state,
                contentKey = { st ->
                    // 模块刷入的「确认 / 刷入中 / 成功 / 失败」是同一个类，
                    // 用类当 key 的话状态切换不会播动画，这里按阶段拆开
                    if (st is InstallUiState.ModuleConfirm) {
                        when {
                            st.flashing -> "module-flashing"
                            st.output != null && st.failed -> "module-failed"
                            st.output != null -> "module-done"
                            else -> "module-confirm"
                        }
                    } else {
                        st::class
                    }
                },
                transitionSpec = {
                    (
                        androidx.compose.animation.fadeIn(
                            androidx.compose.animation.core.tween(durationMillis = 220)
                        ) + androidx.compose.animation.scaleIn(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
                            initialScale = 0.98f
                        )
                    ).togetherWith(
                        androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(durationMillis = 140)
                        )
                    ).using(
                        androidx.compose.animation.SizeTransform(
                            clip = false,
                            sizeAnimationSpec = { _, _ ->
                                androidx.compose.animation.core.tween(durationMillis = 240)
                            }
                        )
                    )
                },
                label = "sheet-state"
            ) { st ->
                when (st) {
                    is InstallUiState.Ready -> ReadySheet(
                        info = st.info,
                        atBottom = atBottom,
                        onClose = onClose,
                        onInstall = { controller.startInstall() }
                    )

                    is InstallUiState.Installing -> InstallingSheet(st, onClose)

                    is InstallUiState.Downloading -> DownloadingSheet(st, controller, onClose)

                    is InstallUiState.Success -> SuccessSheet(st, controller, onClose)

                    is InstallUiState.Failed -> FailedSheet(st, controller, onClose)

                    is InstallUiState.ModuleConfirm -> ModuleSheet(st, controller, onClose)

                    is InstallUiState.SessionConfirm -> SessionSheet(st, controller, onClose)

                    is InstallUiState.Batch -> BatchBody(st, controller, onClose, compact = true)

                    is InstallUiState.Error -> MessageSheet(
                        title = st.title,
                        message = st.message,
                        icon = PuiIcon.Warning,
                        danger = true,
                        onClose = onClose,
                        painter = painterResource(R.drawable.ic_danger),
                        actionText = st.openUrl?.let { tr("用浏览器下载", "Open in browser") }
                            ?: tr("重新选择文件", "Pick another file"),
                        onAction = st.openUrl?.let { url -> { controller.openInBrowser(url) } }
                            ?: { reselect.launch(arrayOf("*/*")) }
                    )

                    else -> MessageSheet(
                        title = tr("准备中", "Preparing"),
                        message = tr("正在读取安装包…", "Reading the APK…"),
                        icon = PuiIcon.Download,
                        onClose = onClose,
                        painter = painterResource(R.drawable.ic_direct_inbox)
                    )
                }
            }
            }

            // 全屏版：真正的 InstallScreen，直接按当前卡片尺寸去排（不是缩放），
            // 所以拖到底就是原本的全屏样式；展开过半才让它进场，中途不会两块内容互相干扰。
            if (p > 0.44f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = pageAlpha }) {
                    InstallScreen(controller = controller, onClose = onClose)
                }
            }
            }
            }
            }

            DragHandle(
                modifier = Modifier
                    // 展开后把这根条压到状态栏下面：贴在最顶上的话，往下拖会被系统当成下拉通知栏
                    .padding(top = 40.dp * p)
                    .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            // 打断：正在跑的展开/回弹立刻停在当前进度，接下来的拖动从这里接着走
                            settleJob?.cancel()
                            tracker.resetTracking()
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            // 跟手：同步改状态。这里**不要 launch 协程** ——
                            // 每个事件起一个协程，最后那个会晚一拍把松手后的动画取消掉（实测停在半路）
                            expandP = (expandP - dy / expandSpanPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val vy = tracker.calculateVelocity().y
                            // 快速上滑 → 展开；快速下滑 → 收回；慢拖动看过没过一半
                            val open = when {
                                vy < -900f -> true
                                vy > 900f -> false
                                else -> expandP >= 0.5f
                            }
                            settleJob = scope.launch {
                                animate(
                                    initialValue = expandP,
                                    targetValue = if (open) 1f else 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = 0.82f,
                                        stiffness = 380f
                                    )
                                ) { value, _ -> expandP = value }
                            }
                        },
                        onDragCancel = {
                            settleJob = scope.launch {
                                animate(
                                    initialValue = expandP,
                                    targetValue = if (expandP >= 0.5f) 1f else 0f,
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 220)
                                ) { value, _ -> expandP = value }
                            }
                        }
                    )
                }
            )
        }
    }

    // 删除确认：浮窗版以前**没有**渲染这个对话框，于是「删除前确认」永远等不到用户点确认，
    // 表现就是安装包既不删也不问（用户报的第二个问题）。和全屏版保持同一套文案。
    // 位置跟着浮窗走：浮窗居中就居中，浮窗贴底就一起下沉，别让弹窗和卡片错位。
    if (controller.deleteDialogVisible) {
        PuiDialog(
            scrim = !settings.noScrim,
            bottomAligned = atBottom,
            title = tr("删除安装包？", "Delete the APK?"),
            text = tr("应用已经安装完成。是否删除这次使用的 APK 文件？删除后无法恢复。", "The app is installed. Delete the APK used for this install? This can't be undone."),
            confirmText = tr("删除", "Delete"),
            dismissText = tr("保留", "Keep"),
            danger = true,
            onDismiss = { controller.resolveDeleteDialog(false) },
            onConfirm = { controller.resolveDeleteDialog(true) }
        )
    }
}

/** 浮窗内容区的高度上限（不含拖拽条）：卡片平时贴合内容，只有窗口够高时才用得上这个值。 */
private val SHEET_MAX_CONTENT_H = 470.dp

/**
 * 顶部拖拽条。现在不只是装饰：按住往上拖可以把浮窗拉成全屏样式，
 * 拖回一半以下就缩回去（DragHandle 的 modifier 里挂着手势）。
 */
@Composable
private fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** 平滑插值：两端导数为 0，淡入淡出不会在起止点「顿」一下。 */
private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}


@Composable
private fun ReadySheet(
    info: ApkInfo,
    atBottom: Boolean,
    onClose: () -> Unit,
    onInstall: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val title = when (info.relation) {
        VersionRelation.UPDATE -> tr("更新应用", "Update app")
        VersionRelation.DOWNGRADE -> tr("降级安装", "Downgrade")
        VersionRelation.REINSTALL -> tr("重新安装", "Reinstall")
        VersionRelation.NEW -> tr("安装应用", "Install app")
    }

    // 高度跟着内容走：不再是 fillMaxSize 撑满整个内容区，
    // 否则卡片比内容高的时候按钮下面会空出一条（用户圈出来的那块）。
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIconView(info.icon, 60.dp)
            Spacer(Modifier.height(6.dp))
            MarqueeText(
                text = info.label,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                alignment = Alignment.Center
            )
            Spacer(Modifier.height(3.dp))
            MarqueeText(
                text = info.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                alignment = Alignment.Center
            )
            if (info.splits.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(
                        "拆分包 · 共 ${info.splits.size + 1} 个分片",
                        "Split bundle · ${info.splits.size + 1} parts"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            InstallInfoChips(
                info = info,
                showOsdk = Settings.get(LocalContext.current).showOsdk,
                showApi = true,
                twoRows = true,
                showSource = Settings.get(LocalContext.current).sourcePresetEnabled
            )
        }

        Spacer(Modifier.height(16.dp))

        SheetVersionCard(info)

        val signer = info.primarySigner
        if (signer?.isAosp == true) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info_badge),
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("该安装包使用 AOSP 公开密钥（${signer.aospKey}）签名", "This APK is signed with the AOSP public key (${signer.aospKey})"),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PuiButton(
                text = tr("取消", "Cancel"),
                onClick = onClose,
                modifier = Modifier.weight(1f),
                style = PuiButtonStyle.Ghost
            )
            PuiButton(
                text = when (info.relation) {
                    VersionRelation.UPDATE -> tr("更新", "Update")
                    VersionRelation.DOWNGRADE -> tr("降级安装", "Downgrade")
                    VersionRelation.REINSTALL -> tr("重新安装", "Reinstall")
                    VersionRelation.NEW -> tr("安装", "Install")
                },
                onClick = onInstall,
                modifier = Modifier.weight(1f),
                style = if (info.relation == VersionRelation.DOWNGRADE) {
                    PuiButtonStyle.Danger
                } else {
                    PuiButtonStyle.Filled
                }
            )
        }

        if (atBottom) Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun VersionLine(label: String, value: String, highlight: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
    }
}

@Composable
private fun InstallingSheet(state: InstallUiState.Installing, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // 高度跟着内容走：不再是 fillMaxSize 撑满整个内容区，
    // 否则卡片比内容高的时候按钮下面会空出一条（用户圈出来的那块）。
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = PuiIcon.Download,
                tint = scheme.onPrimaryContainer,
                container = scheme.primaryContainer,
                size = 68.dp,
                painter = painterResource(R.drawable.ic_direct_inbox)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                state.stage,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (state.progress == null) {
                PuiIndeterminateBar()
            } else {
                PuiProgressBar(progress = state.progress, height = 6.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
            }
        }
    }
}

/**
 * 从分享链接下载安装包。和安装中同一个骨架：图标 → 文件名 → 进度 → 取消。
 *
 * 下载完成后会自动切到 [ReadySheet]，用户不用回浏览器再点一次。
 */
@Composable
private fun DownloadingSheet(
    state: InstallUiState.Downloading,
    controller: InstallController,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (state.total > 0) {
        (state.received.toFloat() / state.total).coerceIn(0f, 1f)
    } else {
        null
    }
    val speed = ApkDownloader.speedText(state.speedBps)

    // 下载过程要有「在动」的感觉：进度条按 450ms 匀速补间（不是每 250ms 硬跳一格），
    // 百分比数字跟着补间往上数，图标做一次很轻的呼吸缩放。
    val shown by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 450,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "download-fraction"
    )
    val shownPercent by androidx.compose.animation.core.animateIntAsState(
        targetValue = ((fraction ?: 0f) * 100).toInt(),
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 400,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "download-percent"
    )
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "download-pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    durationMillis = 1100,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "download-pulse-scale"
        )

    // 高度跟着内容走：不再是 fillMaxSize 撑满整个内容区，
    // 否则卡片比内容高的时候按钮下面会空出一条（用户圈出来的那块）。
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
            ) {
                StatusGlyph(
                    icon = PuiIcon.Download,
                    tint = scheme.onPrimaryContainer,
                    container = scheme.primaryContainer,
                    size = 68.dp,
                    painter = painterResource(R.drawable.ic_document_download)
                )
            }
            Spacer(Modifier.height(14.dp))
            MarqueeText(
                text = state.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                alignment = Alignment.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr("正在下载安装包", "Downloading the APK"),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            if (state.verifying) {
                PuiIndeterminateBar()
                Spacer(Modifier.height(10.dp))
                Text(
                    tr("正在完成网页验证…", "Verifying the page…"),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
            } else if (fraction == null) {
                PuiIndeterminateBar()
                Spacer(Modifier.height(10.dp))
                Text(
                    listOf(Formatters.size(state.received).takeIf { state.received > 0 } ?: "—", speed)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
            } else {
                PuiProgressBar(progress = shown, height = 6.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "$shownPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(
                        "${Formatters.size(state.received)} / ${Formatters.size(state.total)}",
                        speed
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        PuiButton(
            text = tr("取消下载", "Cancel download"),
            onClick = {
                controller.cancelDownload()
                onClose()
            },
            modifier = Modifier
                .fillMaxWidth(),
            style = PuiButtonStyle.Ghost
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun SuccessSheet(
    state: InstallUiState.Success,
    controller: InstallController,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 高度跟着内容走：不再是 fillMaxSize 撑满整个内容区，
    // 否则卡片比内容高的时候按钮下面会空出一条（用户圈出来的那块）。
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = PuiIcon.Check,
                tint = scheme.onPrimary,
                container = scheme.primary,
                size = 72.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (state.info.relation == VersionRelation.UPDATE) tr("更新完成", "Updated") else tr("安装完成", "Installed"),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconView(state.info.icon, 40.dp, corner = 12.dp)
                Spacer(Modifier.width(10.dp))
                // 宽度上限 + 不撑满：这颗「图标 + 名称」的小胶囊要整块居中，
                // 里面的 MarqueeText 一旦 fillMaxWidth 就会把整行拉满、整块贴到左边（用户圈的就是这个）
                Column(Modifier.widthIn(max = 240.dp)) {
                    MarqueeText(
                        text = state.info.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        fillWidth = false
                    )
                    Text(
                        "${state.info.versionName} (${state.info.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            if (state.deleted || state.deleteFailed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (state.deleted) tr("已删除安装包", "APK deleted") else tr("安装包未能删除（没有写入权限）", "Couldn't delete the APK (no write permission)"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.deleted) scheme.primary else scheme.error
                )
                if (state.deleteFailed) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr(
                            "需要给安装器「所有文件访问权限」，或用 ROOT 删",
                            "Grant \"All files access\" to the installer, or use ROOT"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (controller.hasSourceFile()) {
                Spacer(Modifier.height(6.dp))
                PuiButton(
                    text = tr("删除安装包", "Delete APK"),
                    onClick = { controller.deleteSourceManually() },
                    style = PuiButtonStyle.Text,
                    icon = PuiIcon.Trash
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PuiButton(
                text = tr("完成", "Done"),
                onClick = onClose,
                modifier = Modifier.weight(1f),
                style = PuiButtonStyle.Ghost
            )
            PuiButton(
                text = tr("打开", "Open"),
                onClick = { controller.openApp(); onClose() },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun FailedSheet(
    state: InstallUiState.Failed,
    controller: InstallController,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 高度跟着内容走：不再是 fillMaxSize 撑满整个内容区，
    // 否则卡片比内容高的时候按钮下面会空出一条（用户圈出来的那块）。
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = PuiIcon.Close,
                tint = scheme.onErrorContainer,
                container = scheme.errorContainer,
                size = 68.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PuiButton(
                text = tr("关闭", "Close"),
                onClick = onClose,
                modifier = Modifier.weight(1f),
                style = PuiButtonStyle.Ghost
            )
            if (state.info != null) {
                PuiButton(
                    text = tr("重试", "Retry"),
                    onClick = { controller.retry() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * 模块刷入确认。和装 APK 共用同一个浮窗壳子，所以位置、遮罩、背景模糊全部跟着设置走。
 */
@Composable
internal fun ModuleSheet(
    st: InstallUiState.ModuleConfirm,
    controller: InstallController,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val noRoot = st.rootMode == com.panl.installer.core.ModuleInstaller.RootMode.None

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ModuleIconView(60.dp)
            Spacer(Modifier.height(14.dp))
            MarqueeText(
                text = st.module.name,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                alignment = Alignment.Center
            )
            Spacer(Modifier.height(3.dp))
            Text(
                st.module.id,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(
                    6.dp, Alignment.CenterHorizontally
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                if (st.module.version.isNotBlank()) {
                    PuiChip(text = st.module.version)
                }
                if (st.module.author.isNotBlank()) {
                    PuiChip(text = st.module.author)
                }
                if (!noRoot) {
                    PuiChip(text = com.panl.installer.core.ModuleInstaller.label(st.rootMode))
                }
            }
            Spacer(Modifier.height(16.dp))

            // 和装 APK 那张「版本」卡一个位置，放模块描述 + 刷入结果
            PuiCard(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(
                        tr("模块信息", "Module"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    if (st.module.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            st.module.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    if (noRoot) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr(
                                "没有检测到 Magisk / KernelSU / APatch",
                                "No Magisk / KernelSU / APatch found"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr(
                                "如果已经装了 root，请在 root 管理器里给安装器授权后再试",
                                "If root is installed, grant root to this installer in your root manager"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    if (st.flashing) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = scheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr("正在刷入模块…", "Flashing module…"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.primary
                            )
                        }
                    }
                    st.output?.takeIf { it.isNotBlank() }?.let { out ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (st.failed) tr("刷入失败", "Flash failed")
                            else tr("刷入成功，重启后生效", "Flashed — takes effect after a reboot"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (st.failed) scheme.error else scheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            out.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when {
                st.flashing -> PuiButton(
                    text = tr("正在刷入…", "Flashing…"),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f)
                )

                st.output != null && !st.failed -> {
                    PuiButton(
                        text = tr("稍后", "Later"),
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        style = PuiButtonStyle.Ghost
                    )
                    PuiButton(
                        text = tr("立即重启", "Reboot now"),
                        onClick = { scope.launch { com.panl.installer.core.RootShell.reboot() } },
                        modifier = Modifier.weight(1.2f)
                    )
                }

                st.failed -> {
                    PuiButton(
                        text = tr("关闭", "Close"),
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        style = PuiButtonStyle.Ghost
                    )
                    PuiButton(
                        text = tr("重试", "Retry"),
                        onClick = { controller.flashModule() },
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    PuiButton(
                        text = tr("取消", "Cancel"),
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        style = PuiButtonStyle.Ghost
                    )
                    PuiButton(
                        text = tr("刷入", "Flash"),
                        onClick = { controller.flashModule() },
                        enabled = !noRoot,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

/** 模块图标：红色渐变圆角方块 + 白色 Box 图标，和装 APK 的应用图标同一个尺寸位置。 */
@Composable
private fun ModuleIconView(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFFE2557A),
                        androidx.compose.ui.graphics.Color(0xFFB01F45)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(com.panl.installer.R.drawable.ic_box),
            contentDescription = null,
            modifier = Modifier.size(size * 0.5f),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                androidx.compose.ui.graphics.Color.White
            )
        )
    }
}

@Composable
private fun SessionSheet(
    state: InstallUiState.SessionConfirm,
    controller: InstallController,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 高度跟着内容走：不再是 fillMaxSize 撑满整个内容区，
    // 否则卡片比内容高的时候按钮下面会空出一条（用户圈出来的那块）。
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = PuiIcon.Download,
                tint = scheme.onPrimaryContainer,
                container = scheme.primaryContainer,
                size = 68.dp,
                painter = painterResource(R.drawable.ic_direct_inbox)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                state.label,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                state.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PuiButton(
                text = tr("取消", "Cancel"),
                onClick = onClose,
                modifier = Modifier.weight(1f),
                style = PuiButtonStyle.Ghost
            )
            PuiButton(
                text = tr("安装", "Install"),
                onClick = { controller.confirmSession() },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun MessageSheet(
    title: String,
    message: String,
    icon: PuiIcon,
    danger: Boolean = false,
    onClose: () -> Unit,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    // 高度跟着内容走：不再是 fillMaxSize 撑满整个内容区，
    // 否则卡片比内容高的时候按钮下面会空出一条（用户圈出来的那块）。
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = icon,
                tint = if (danger) scheme.onErrorContainer else scheme.onPrimaryContainer,
                container = if (danger) scheme.errorContainer else scheme.primaryContainer,
                size = 68.dp,
                painter = painter
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionText != null && onAction != null) {
            PuiButton(
                text = actionText,
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth(),
                style = PuiButtonStyle.Filled
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}


/** 浮窗里的版本卡：结构和全屏版一致，字号小一号，白底 + 1dp 描边（浮窗本身也是白底）。 */
@Composable
private fun SheetVersionCard(info: ApkInfo) {
    val scheme = MaterialTheme.colorScheme
    val installed = info.installed
    val relation = info.relation
    val shape = RoundedCornerShape(20.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("版本", "Version"),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            RelationBadge(relation)
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("当前", "Current"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                MarqueeText(
                    text = installed?.versionName ?: tr("未安装", "Not installed"),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (installed == null) scheme.onSurfaceVariant else scheme.onSurface
                )
                Text(
                    if (installed != null) "(${installed.versionCode})" else "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            Box(
                Modifier
                    // 版本号改成轮播后会占满整列，文字会贴到圆钮上；这里留出呼吸位
                    .padding(horizontal = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                PuiIconView(PuiIcon.ChevronRight, Modifier.size(16.dp), tint = scheme.primary)
            }

            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(tr("新版本", "New"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                MarqueeText(
                    text = info.versionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (relation == VersionRelation.DOWNGRADE) scheme.error else scheme.primary,
                    alignment = Alignment.CenterEnd
                )
                Text(
                    "(${info.versionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(scheme.outlineVariant.copy(alpha = 0.7f))
        )
        Spacer(Modifier.height(8.dp))

        Text(
            text = when (relation) {
                VersionRelation.NEW -> tr("当前设备未安装该应用，将作为新应用安装", "Not installed on this device — it will be installed as a new app")
                VersionRelation.UPDATE ->
                    tr("从 ${installed?.versionName} 更新到 ${info.versionName}，", "Updating from ${installed?.versionName} to ${info.versionName}, ") +
                        tr("版本号 ${installed?.versionCode} → ${info.versionCode}", "version ${installed?.versionCode} → ${info.versionCode}")
                VersionRelation.DOWNGRADE ->
                    tr("新版本号低于当前版本（${installed?.versionCode} → ${info.versionCode}），", "The new version is lower than the installed one (${installed?.versionCode} → ${info.versionCode}), ") +
                        tr("需要系统允许降级安装", "the system must allow downgrades")
                VersionRelation.REINSTALL -> tr("版本号相同，将覆盖重新安装", "Same version — it will overwrite the current install")
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

