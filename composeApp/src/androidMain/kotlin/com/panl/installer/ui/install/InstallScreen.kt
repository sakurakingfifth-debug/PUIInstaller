package com.panl.installer.ui.install

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.panl.installer.R
import com.panl.installer.core.ApkDownloader
import com.panl.installer.core.ApkInfo
import com.panl.installer.core.Formatters
import com.panl.installer.core.Settings
import com.panl.installer.core.VersionRelation
import com.panl.installer.ui.components.AppIconView
import com.panl.installer.ui.components.BottomActionBar
import com.panl.installer.ui.components.PuiIndeterminateBar
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiTopFade
import com.panl.pui.rememberPuiTopBlurHeight
import com.panl.pui.puiProgressiveTopBlur
import com.panl.installer.ui.components.InstallContentMaxWidth
import com.panl.installer.ui.components.MarqueeText
import com.panl.installer.ui.components.adaptiveWidth
import com.panl.installer.ui.components.RelationBadge
import com.panl.installer.ui.components.SignerCard
import com.panl.installer.ui.components.InstallInfoChips
import com.panl.installer.ui.components.PuiBottomFade
import com.panl.installer.ui.components.StatusGlyph
import androidx.compose.ui.res.painterResource
import com.panl.installer.ui.components.VersionCompareCard
import com.panl.installer.ui.components.puiCardBackdropBlur
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiCard
import com.panl.pui.PuiChip
import com.panl.pui.PuiDialog
import com.panl.pui.PuiDivider
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.panl.installer.i18n.tr

/**
 * 安装页。
 *
 * 信息层级：应用是谁（头部）→ 版本怎么变（版本卡）→ 谁签的（签名卡）→ 装到哪（详情）→ 要什么权限。
 * 一屏里前两块必须在首屏可见，所以头部高度压得比较紧（图标 76dp、名称一行起）。
 */
@Composable
fun InstallScreen(controller: InstallController, onClose: () -> Unit) {
    val state = controller.state
    // 错误页的「重新选择文件」：换一个文件重新读
    val reselect = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { picked -> picked?.let { controller.openPicked(it) } }

    val settings = Settings.get(LocalContext.current)
    val density = LocalDensity.current.density
    // 和 PUI Demo 主页完全一致：状态栏 + 64dp
    val blurH = rememberPuiTopBlurHeight()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 删除安装包对话框弹出时，背后整页按「浮窗背景模糊」糊掉
            .puiCardBackdropBlur(controller.deleteDialogVisible, (settings.sheetBlurPx / density).dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (settings.blurEnabled) {
                        Modifier.puiProgressiveTopBlur(
                            height = blurH,
                            maxRadius = (settings.topBlurPx / density).dp
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            // contentKey 用状态类型：进度每涨 1% 都是新的 Installing 对象，
            // 不按类型去重的话整个转场会被进度刷新打断
            // 大屏（Tier 2）：正文收窄到 640dp 居中 —— 单列信息拉满整块屏只会多出大片留白
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            androidx.compose.animation.AnimatedContent(
                modifier = Modifier.adaptiveWidth(InstallContentMaxWidth).fillMaxHeight(),
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
                            androidx.compose.animation.core.tween(durationMillis = 260)
                        ) + androidx.compose.animation.slideInVertically(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 260),
                            initialOffsetY = { it / 16 }
                        )
                    ).togetherWith(
                        androidx.compose.animation.fadeOut(
                            androidx.compose.animation.core.tween(durationMillis = 160)
                        ) + androidx.compose.animation.slideOutVertically(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 160),
                            targetOffsetY = { -it / 24 }
                        )
                    )
                },
                label = "install-state"
            ) { st ->
                when (st) {
                    // 模块刷入：和浮窗共用同一份内容，外面套个居中容器
                    is InstallUiState.ModuleConfirm -> Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        ModuleSheet(st, controller, onClose)
                    }

                    is InstallUiState.Loading -> SimpleStateBody(
                        title = tr("准备中", "Preparing"),
                        message = tr("正在读取安装包…", "Reading the APK…"),
                        icon = PuiIcon.Download,
                        onClose = onClose,
                        iconPainter = painterResource(R.drawable.ic_direct_inbox)
                    )

                    is InstallUiState.Preparing -> SimpleStateBody(
                        title = tr("准备中", "Preparing"),
                        message = tr("正在把安装包复制到临时目录…", "Copying the APK to a temp folder…"),
                        icon = PuiIcon.Download,
                        onClose = onClose,
                        iconPainter = painterResource(R.drawable.ic_direct_inbox)
                    )

                    is InstallUiState.Ready -> ReadyBody(st.info, controller, onClose, topPad = blurH)

                    is InstallUiState.Installing -> InstallingBody(st, controller, onClose)

                    is InstallUiState.Downloading -> DownloadingBody(st, controller, onClose)

                    is InstallUiState.Success -> SuccessBody(st, controller, onClose)

                    is InstallUiState.Failed -> FailedBody(st, controller, onClose)

                    is InstallUiState.Error -> SimpleStateBody(
                        title = st.title,
                        message = st.message,
                        icon = PuiIcon.Warning,
                        onClose = onClose,
                        tone = Tone.Error,
                        iconPainter = painterResource(R.drawable.ic_danger),
                        actionText = st.openUrl?.let { tr("用浏览器下载", "Open in browser") }
                            ?: tr("重新选择文件", "Pick another file"),
                        onAction = st.openUrl?.let { url -> { controller.openInBrowser(url) } }
                            ?: { reselect.launch(arrayOf("*/*")) }
                    )

                    is InstallUiState.SessionConfirm -> SessionConfirmBody(st, controller, onClose)

                    is InstallUiState.Batch -> BatchBody(st, controller, onClose, compact = false)
                }
            }
            }
        }

        // 顶部渐隐：内容滚进模糊区时自然消失（Demo 里也是叠在内容层之上）
        if (settings.blurEnabled) PuiTopFade(height = blurH)
    }

    if (controller.deleteDialogVisible) {
        PuiDialog(
            scrim = !settings.noScrim,
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

// ---------------- 就绪 ----------------

@Composable
private fun ReadyBody(
    info: ApkInfo,
    controller: InstallController,
    onClose: () -> Unit,
    topPad: Dp
) {
    val settings = Settings.get(LocalContext.current)
    val relation = info.relation
    val title = when (relation) {
        VersionRelation.UPDATE -> tr("更新应用", "Update app")
        VersionRelation.DOWNGRADE -> tr("降级安装", "Downgrade")
        VersionRelation.REINSTALL -> tr("重新安装", "Reinstall")
        VersionRelation.NEW -> tr("安装应用", "Install app")
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
        LazyColumn(
            // 必须 weight：fillMaxSize 会吃满整个 Column，底部的安装按钮被挤没
            Modifier.fillMaxSize().puiEdgeHaptic(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topPad + 4.dp,
                bottom = 40.dp
            )
        ) {
                item { AppHeader(info) }
                item {
                    Spacer(Modifier.height(18.dp))
                    VersionCompareCard(info)
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    SignerCard(info.primarySigner, settings.warnAospSignature)
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    DetailsCard(info)
                }
                if (info.permissions.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        PermissionsCard(info)
                    }
                }
            }
            PuiBottomFade(32.dp)
        }

        BottomActionBar(
            primaryText = when (relation) {
                VersionRelation.UPDATE -> tr("更新", "Update")
                VersionRelation.DOWNGRADE -> tr("降级安装", "Downgrade")
                VersionRelation.REINSTALL -> tr("重新安装", "Reinstall")
                VersionRelation.NEW -> tr("安装", "Install")
            },
            onPrimary = { controller.startInstall() },
            secondaryText = tr("取消", "Cancel"),
            onSecondary = onClose,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
private fun AppHeader(info: ApkInfo) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconView(info.icon, 76.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            info.label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        MarqueeText(
            text = info.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            alignment = Alignment.Center
        )
        Spacer(Modifier.height(10.dp))
        // 版本关系徽标只留在下面的版本卡里，这里不再重复
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PuiChip(text = Formatters.size(info.sizeBytes))
            if (info.isDebuggable) PuiChip(text = tr("可调试", "Debuggable"))
        }
        Spacer(Modifier.height(8.dp))
        InstallInfoChips(
            info = info,
            showOsdk = Settings.get(LocalContext.current).showOsdk
        )
    }
}

@Composable
private fun DetailsCard(info: ApkInfo) {
    PuiCard {
        Column {
            DetailRow(tr("文件大小", "Size"), Formatters.size(info.sizeBytes), showDivider = true)
            DetailRow(tr("最低系统", "Min Android"), Formatters.sdkLevel(info.minSdk), showDivider = true)
            DetailRow(tr("目标系统", "Target Android"), Formatters.sdkLevel(info.targetSdk), showDivider = true)
            DetailRow(tr("安装位置", "Install location"), Formatters.installLocation(info.installLocation), showDivider = true)
            DetailRow(
                tr("签名数量", "Signers"),
                if (info.signers.isEmpty()) tr("未签名", "Unsigned") else tr("${info.signers.size} 个签名者", "${info.signers.size} signers"),
                showDivider = false
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, showDivider: Boolean) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showDivider) PuiDivider(startIndent = 16.dp)
    }
}

/** 权限卡：默认收起，避免长列表把「安装」按钮顶得太远。 */
@Composable
private fun PermissionsCard(info: ApkInfo) {
    // 必须用 rememberSaveable：LazyColumn 把滚出屏幕的 item 销毁后，
    // 普通 remember 会丢状态，滑回来就自动收起了
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val chevron by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "perm-chevron"
    )
    val context = LocalContext.current
    val labels by produceState(initialValue = emptyList<String>(), info.packageName) {
        value = withContext(Dispatchers.IO) {
            info.permissions.map { name ->
                runCatching {
                    context.packageManager.getPermissionInfo(name, 0).loadLabel(context.packageManager).toString()
                }.getOrDefault(name.substringAfterLast('.'))
            }
        }
    }

    PuiCard {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // 轻触反馈：和 PUI 滑块换挡同一档位
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        expanded = !expanded
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PuiIconView(
                    PuiIcon.Lock,
                    Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    tr("权限", "Permissions"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    tr("${info.permissions.size} 项", "${info.permissions.size} items"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                PuiIconView(
                    PuiIcon.ChevronRight,
                    Modifier
                        .size(18.dp)
                        .rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 240),
                    expandFrom = Alignment.Top
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
                ),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                    shrinkTowards = Alignment.Top
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 140)
                )
            ) {
                Column {
                    PuiDivider(startIndent = 16.dp)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        labels.forEach { label ->
                            Text(
                                "· $label",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 安装中 ----------------

@Composable
private fun InstallingBody(
    state: InstallUiState.Installing,
    controller: InstallController,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = PuiIcon.Download,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                container = MaterialTheme.colorScheme.primaryContainer,
                size = 96.dp,
                painter = painterResource(R.drawable.ic_direct_inbox)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                tr("正在安装", "Installing"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                state.stage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(26.dp))
            if (state.progress == null) {
                PuiIndeterminateBar()
            } else {
                PuiProgressBar(progress = state.progress, height = 6.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                tr("请勿退出或锁屏，安装完成会自动跳转", "Don't leave or lock the screen — it continues automatically when done"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}


// ---------------- 下载中 ----------------

@Composable
private fun DownloadingBody(
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

    // 和浮窗版同一套动画：进度匀速补间、百分比跟着数、图标轻微呼吸
    val shown by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(durationMillis = 450, easing = androidx.compose.animation.core.LinearEasing),
        label = "download-fraction"
    )
    val shownPercent by androidx.compose.animation.core.animateIntAsState(
        targetValue = ((fraction ?: 0f) * 100).toInt(),
        animationSpec = tween(durationMillis = 400, easing = androidx.compose.animation.core.LinearEasing),
        label = "download-percent"
    )
    val pulse by rememberInfiniteTransition(label = "download-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "download-pulse-scale"
    )

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
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
                    size = 96.dp,
                    painter = painterResource(R.drawable.ic_document_download)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                tr("正在下载", "Downloading"),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                state.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
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
            Spacer(Modifier.height(24.dp))
            Text(
                tr("下载完成后可以直接安装，不用再回浏览器", "It installs right after the download — no need to go back to the browser"),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        BottomActionBar(
            primaryText = tr("取消下载", "Cancel download"),
            onPrimary = {
                controller.cancelDownload()
                onClose()
            }
        )
    }
}


// ---------------- 结果 ----------------

@Composable
private fun SuccessBody(
    state: InstallUiState.Success,
    controller: InstallController,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // 结果页不保留顶栏：底部已有「完成 / 打开」，再放一个 X + 标题是重复信息
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = PuiIcon.Check,
                tint = MaterialTheme.colorScheme.onPrimary,
                container = MaterialTheme.colorScheme.primary,
                size = 96.dp
            )
            Spacer(Modifier.height(22.dp))
            Text(
                if (state.info.relation == VersionRelation.UPDATE) tr("更新完成", "Updated") else tr("安装完成", "Installed"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconView(state.info.icon, 44.dp, corner = 13.dp)
                Spacer(Modifier.width(12.dp))
                // 同浮窗：小胶囊要整块居中，里面的 MarqueeText 不能撑满
                Column(Modifier.widthIn(max = 260.dp)) {
                    MarqueeText(
                        text = state.info.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fillWidth = false
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${state.info.versionName} (${state.info.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.deleted) {
                Spacer(Modifier.height(14.dp))
                Text(
                    tr("已删除安装包", "APK deleted"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (state.deleteFailed) {
                Spacer(Modifier.height(14.dp))
                Text(
                    tr("安装包未能删除（没有写入权限）", "Couldn't delete the APK (no write permission)"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr(
                        "需要给安装器「所有文件访问权限」，或用 ROOT 删",
                        "Grant \"All files access\" to the installer, or use ROOT"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else if (controller.hasSourceFile()) {
                Spacer(Modifier.height(8.dp))
                PuiButton(
                    text = tr("删除安装包", "Delete APK"),
                    onClick = { controller.deleteSourceManually() },
                    style = PuiButtonStyle.Text,
                    icon = PuiIcon.Trash
                )
            }
        }
        BottomActionBar(
            primaryText = tr("打开", "Open"),
            onPrimary = { controller.openApp(); onClose() },
            secondaryText = tr("完成", "Done"),
            onSecondary = onClose,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
private fun FailedBody(
    state: InstallUiState.Failed,
    controller: InstallController,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = PuiIcon.Close,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                container = MaterialTheme.colorScheme.errorContainer,
                size = 96.dp
            )
            Spacer(Modifier.height(22.dp))
            Text(
                tr("未能安装", "Install failed"),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    onClick = {
                        // 回到就绪态再走一次
                        controller.retry()
                    },
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}

// ---------------- 会话确认 / 通用状态 ----------------

@Composable
private fun SessionConfirmBody(
    state: InstallUiState.SessionConfirm,
    controller: InstallController,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = PuiIcon.Download,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                container = MaterialTheme.colorScheme.primaryContainer,
                size = 96.dp,
                painter = painterResource(R.drawable.ic_direct_inbox)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                state.label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                state.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Text(
                tr("其他应用请求安装这个包，确认后会交给系统继续安装。", "Another app asked to install this package. Once you confirm, the system takes over."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        BottomActionBar(
            primaryText = tr("安装", "Install"),
            onPrimary = { controller.confirmSession() },
            secondaryText = tr("取消", "Cancel"),
            onSecondary = onClose,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

enum class Tone { Normal, Error }

@Composable
private fun SimpleStateBody(
    title: String,
    message: String,
    icon: PuiIcon,
    onClose: () -> Unit,
    tone: Tone = Tone.Normal,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            StatusGlyph(
                icon = icon,
                tint = if (tone == Tone.Error) scheme.onErrorContainer else scheme.onPrimaryContainer,
                container = if (tone == Tone.Error) scheme.errorContainer else scheme.primaryContainer,
                size = 96.dp,
                painter = iconPainter
            )
            Spacer(Modifier.height(22.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionText != null && onAction != null) {
            BottomActionBar(primaryText = actionText, onPrimary = onAction)
        }
    }
}
