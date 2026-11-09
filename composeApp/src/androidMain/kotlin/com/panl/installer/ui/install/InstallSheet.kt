package com.panl.installer.ui.install

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.panl.installer.ui.components.InstallInfoChips
import com.panl.installer.core.Formatters
import com.panl.installer.core.Settings
import com.panl.installer.core.VersionRelation
import com.panl.installer.ui.InstallActivity
import com.panl.installer.ui.components.AppIconView
import com.panl.installer.ui.components.BottomActionBar
import com.panl.installer.ui.components.PuiIndeterminateBar
import com.panl.installer.ui.components.RelationBadge
import com.panl.installer.ui.components.StatusGlyph
import com.panl.installer.ui.components.puiCardBackdropBlur
import com.panl.pui.PuiButton
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
    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    val state = controller.state
    val atBottom = settings.sheetPosition != "Center"

    Box(Modifier.fillMaxSize()) {
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
                .background(Color.Black.copy(alpha = dim))
                .pointerInput(Unit) { detectTapGestures { onClose() } }
        )

        val shape = if (atBottom) {
            RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        } else {
            RoundedCornerShape(30.dp)
        }

        Column(
            Modifier
                .align(if (atBottom) Alignment.BottomCenter else Alignment.Center)
                .padding(horizontal = if (atBottom) 0.dp else 14.dp)
                .fillMaxWidth()
                // 删除确认弹出来时，整张卡片先糊掉：不然白底弹窗叠在白底卡片上分不出层次，
                // 看起来像「弹窗被塞进了卡片里」（用户报的就是这个）
                .puiCardBackdropBlur(controller.deleteDialogVisible, (settings.sheetBlurPx / density).dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                // 吃掉卡片上的点击，避免穿透到遮罩
                .pointerInput(Unit) { detectTapGestures { } }
                // 卡片内统一留底：居中时曾经是 0，按钮直接压在卡片下边缘上
                .padding(bottom = 22.dp)
        ) {
            DragHandle()

            // 卡片内容区固定高度：安装过程中卡片不随状态缩放。
            // 各状态内部是「头部固定 / 中段 weight 居中 / 按钮贴底」，所以切换时位置也不会跳。
            Box(Modifier.fillMaxWidth().height(SHEET_CONTENT_H)) {

            // 和全屏版同一套思路：按状态类型去重（进度刷新不触发转场），
            // 额外用 SizeTransform 把卡片高度也一起补间，否则换页时卡片会瞬间弹一下
            androidx.compose.animation.AnimatedContent(
                targetState = state,
                contentKey = { it::class },
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

                    is InstallUiState.Success -> SuccessSheet(st, controller, onClose)

                    is InstallUiState.Failed -> FailedSheet(st, controller, onClose)

                    is InstallUiState.ModuleConfirm -> ModuleSheet(st, controller, onClose)

                    is InstallUiState.SessionConfirm -> SessionSheet(st, controller, onClose)

                    is InstallUiState.Error -> MessageSheet(
                        title = st.title,
                        message = st.message,
                        icon = PuiIcon.Warning,
                        danger = true,
                        onClose = onClose
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

/** 浮窗内容区高度（不含拖拽条）。够放下「图标 + 版本卡 + 按钮」的完整就绪态。 */
private val SHEET_CONTENT_H = 470.dp

/** 顶部拖拽条。纯装饰：这里不支持手势拖拽，只用来暗示「这是一张可下拉的卡片」。 */
@Composable
private fun DragHandle() {
    Box(
        Modifier
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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIconView(info.icon, 60.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                info.label,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                info.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                .fillMaxWidth()
                .navigationBarsPadding(),
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
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
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

@Composable
private fun SuccessSheet(
    state: InstallUiState.Success,
    controller: InstallController,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
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
                Column {
                    Text(
                        state.info.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                .fillMaxWidth()
                .navigationBarsPadding(),
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
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
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
                .fillMaxWidth()
                .navigationBarsPadding(),
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

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            tr("刷入模块", "Flash module"),
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                st.module.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (st.module.version.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    st.module.version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            if (st.module.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    st.module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                tr("模块 ID · ${st.module.id}", "Module ID · ${st.module.id}"),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            if (st.module.author.isNotBlank()) {
                Text(
                    tr("作者 · ${st.module.author}", "Author · ${st.module.author}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    noRoot -> tr(
                        "没有检测到 Magisk / KernelSU / APatch",
                        "No Magisk / KernelSU / APatch found"
                    )
                    else -> tr(
                        "刷写方式 · ${com.panl.installer.core.ModuleInstaller.label(st.rootMode)}",
                        "Flash with · ${com.panl.installer.core.ModuleInstaller.label(st.rootMode)}"
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (noRoot) scheme.error else scheme.onSurface,
                textAlign = TextAlign.Center
            )
            st.output?.takeIf { it.isNotBlank() }?.let { out ->
                Spacer(Modifier.height(10.dp))
                Text(
                    if (st.failed) tr("刷入失败", "Flash failed") else tr("刷入成功，重启后生效", "Flashed — takes effect after a reboot"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (st.failed) scheme.error else scheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    out.take(600),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
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

@Composable
private fun SessionSheet(
    state: InstallUiState.SessionConfirm,
    controller: InstallController,
    onClose: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
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
                .fillMaxWidth()
                .navigationBarsPadding(),
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
    painter: androidx.compose.ui.graphics.painter.Painter? = null
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
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
                Text(
                    installed?.versionName ?: tr("未安装", "Not installed"),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (installed == null) scheme.onSurfaceVariant else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (installed != null) "(${installed.versionCode})" else "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            Box(
                Modifier
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
                Text(
                    info.versionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (relation == VersionRelation.DOWNGRADE) scheme.error else scheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

