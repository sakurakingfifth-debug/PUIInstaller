package com.panl.installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.panl.installer.core.InstallerLog
import com.panl.installer.core.Settings
import com.panl.installer.i18n.tr
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiBarMetrics
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiCard
import com.panl.pui.PuiFloatingNavBar
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiTextField
import com.panl.pui.PuiTopFade
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.navBackdropSource
import com.panl.pui.rememberNavBackdrop
import com.panl.pui.rememberNavigationBarHeight
import com.panl.pui.rememberPuiTopBlurHeight
import kotlinx.coroutines.launch

/**
 * 安装器全局设置（移植自 InstallerX-Revived 的 InstallerGlobalSettingsPage，只搬功能不搬 UI）。
 *
 * 三块内容各占一页，底部用和主页一样的悬浮导航栏切换：
 *  - 来源：只允许列表里的应用发起安装；
 *  - 黑名单：按包名禁止安装；
 *  - SharedUID：按 sharedUserId 禁止安装。
 */
class InstallerGlobalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                GlobalShell()
            }
        }
    }
}

private val TAB_LABELS: List<String>
    @Composable get() = listOf(tr("来源", "Sources"), tr("黑名单", "Blocklist"), tr("SharedUID", "SharedUID"))

private val TAB_ICONS = listOf(PuiIcon.Download, PuiIcon.Trash, PuiIcon.User)

@Composable
private fun GlobalShell() {
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val settings = Settings.get(LocalContext.current)
    val navBarH = rememberNavigationBarHeight()
    val blurH = rememberPuiTopBlurHeight()
    val density = LocalDensity.current.density
    val backdrop = rememberNavBackdrop()
    val blurOn = settings.blurEnabled

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val itemWidth = minOf(
            PuiBarMetrics.itemWidth,
            (maxWidth - 40.dp - PuiBarMetrics.padding * 2) / TAB_LABELS.size
        )

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = if (blurOn) {
                Modifier
                    .fillMaxSize()
                    // 先登记位置（导航栏毛玻璃要采样这一层），再加模糊 —— 顺序不能反
                    .navBackdropSource(backdrop)
                    .puiProgressiveTopBlur(
                        height = blurH,
                        maxRadius = (settings.topBlurPx / density).dp,
                        backdrop = backdrop
                    )
            } else {
                Modifier.fillMaxSize()
            }
        ) { page ->
            GlobalPage(page = page, topPad = blurH, navBarH = navBarH)
        }

        // 内容滚进模糊区时自然消失
        PuiTopFade(height = blurH)

        // 底部切面过渡：内容滚到导航栏那儿是淡出，不是一刀切
        val bg = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBarH + 120.dp)
                .background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.55f to bg, 1f to bg)
                )
        )

        PuiFloatingNavBar(
            items = TAB_LABELS,
            selectedIndex = pagerState.currentPage,
            onSelect = { page ->
                if (page != pagerState.currentPage) {
                    scope.launch { pagerState.animateScrollToPage(page) }
                }
            },
            icons = TAB_ICONS,
            itemWidth = itemWidth,
            backdrop = if (blurOn) backdrop else null,
            glassAlpha = settings.navGlassPercent / 100f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PuiBarMetrics.bottomMargin + navBarH)
        )
    }
}

/** 三种列表共用的目标。 */
private enum class ListKind { Sources, Packages, SharedUids }

@Composable
private fun GlobalPage(page: Int, topPad: Dp, navBarH: Dp) {
    val context = LocalContext.current
    val settings = Settings.get(context)
    var tick by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }

    val kind = when (page) {
        0 -> ListKind.Sources
        1 -> ListKind.Packages
        else -> ListKind.SharedUids
    }
    val raw = remember(tick, page) {
        when (kind) {
            ListKind.Sources -> settings.managedInstallerPackagesRaw
            ListKind.Packages -> settings.blacklistPackagesRaw
            ListKind.SharedUids -> settings.blacklistSharedUidRaw
        }
    }
    val entries = raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

    val title = when (kind) {
        ListKind.Sources -> tr("来源预设", "Install sources")
        ListKind.Packages -> tr("禁止安装的包名", "Blocked packages")
        ListKind.SharedUids -> tr("禁止安装的 SharedUID", "Blocked SharedUIDs")
    }
    val emptyTitle = when (kind) {
        ListKind.Sources -> tr("没有预设安装来源", "No preset install sources")
        ListKind.Packages -> tr("没有需要禁止安装的应用包名", "No package names blocked")
        ListKind.SharedUids -> tr("没有需要禁止安装的应用 SharedUID", "No SharedUIDs blocked")
    }
    val subtitleOf = when (kind) {
        ListKind.Sources -> tr("允许发起安装", "Allowed to start installs")
        ListKind.Packages -> tr("禁止安装该包", "Installs blocked")
        ListKind.SharedUids -> tr("禁止安装该 SharedUID", "Installs blocked")
    }
    val rowIcon = when (kind) {
        ListKind.Sources -> PuiIcon.Download
        ListKind.Packages -> PuiIcon.Trash
        ListKind.SharedUids -> PuiIcon.User
    }

    fun write(value: String) {
        when (kind) {
            ListKind.Sources -> settings.managedInstallerPackagesRaw = value
            ListKind.Packages -> settings.blacklistPackagesRaw = value
            ListKind.SharedUids -> settings.blacklistSharedUidRaw = value
        }
        tick++
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .puiEdgeHaptic(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPad + 4.dp,
            bottom = navBarH + 130.dp
        )
    ) {
        item {
            PuiPageTitle(title)
            Spacer(Modifier.height(14.dp))
        }

        if (entries.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    run {
                        Text(
                            emptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tr("点击下方的「新增」按钮开始", "Tap “Add” below to start"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(entries) { entry ->
            EntryCard(
                icon = rowIcon,
                title = entry.substringAfter(' ', "").ifBlank { entry },
                subtitle = if (entry.contains(' ')) entry.substringBefore(' ') else subtitleOf,
                onRemove = {
                    write(entries.filter { it != entry }.joinToString("\n"))
                }
            )
        }

        item {
            AddCard(onClick = { adding = true })
        }
    }

    if (adding) {
        val (dialogTitle, hint) = when (kind) {
            ListKind.Sources -> tr("新增安装来源", "Add install source") to
                tr("包名，例如 com.android.vending", "Package name, e.g. com.android.vending")
            ListKind.Packages -> tr("新增禁止安装的包名", "Add blocked package") to
                tr("包名，例如 com.example.app", "Package name, e.g. com.example.app")
            ListKind.SharedUids -> tr("新增禁止安装的 SharedUID", "Add blocked SharedUID") to
                tr("UID 值，例如 1000 或 android.uid.phone", "UID value, e.g. 1000 or android.uid.phone")
        }
        AddEntryDialog(
            title = dialogTitle,
            placeholder = hint,
            onDismiss = { adding = false },
            onConfirm = { value ->
                write(if (raw.isBlank()) value else raw.trimEnd() + "\n" + value)
                InstallerLog.record(context, "SETTINGS", "新增 $value")
                adding = false
            }
        )
    }
}

/** 一条记录：图标 + 名称 + 说明 + 删除。版式跟历史页的行一致，支持左滑删除。 */
@Composable
private fun EntryCard(
    icon: PuiIcon,
    title: String,
    subtitle: String,
    onRemove: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    SwipeToDelete(onDelete = onRemove) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceContainerLowest)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                PuiIconView(icon, Modifier.size(24.dp), tint = scheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            PillButton(text = tr("删除", "Remove"), onClick = onRemove)
        }
    }
}

/**
 * 左滑删除：卡片往左跟手滑，松手超过阈值就删。
 *
 * 只认「往左」——往右的位移不 consume，交给外层 pager 翻页（和历史页同一套判断）。
 */
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxSwipePx = with(density) { 104.dp.toPx() }
    val triggerPx = with(density) { 64.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        // 底层：滑开后露出的红色删除底
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.errorContainer),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                tr("删除", "Delete"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onErrorContainer,
                modifier = Modifier.padding(end = 24.dp)
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDx = 0f
                            var claimed = false
                            var dragging = true
                            while (dragging) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                val delta = change.positionChange()
                                if (!claimed) {
                                    totalDx += delta.x
                                    when {
                                        totalDx < -slop -> claimed = true
                                        totalDx > slop -> dragging = false   // 往右：交给 pager
                                    }
                                }
                                if (claimed) {
                                    change.consume()
                                    offsetX = (offsetX + delta.x).coerceIn(-maxSwipePx, 0f)
                                }
                            }
                            if (claimed) {
                                if (offsetX <= -triggerPx) onDelete()
                                scope.launch {
                                    androidx.compose.animation.core.animate(
                                        offsetX,
                                        0f
                                    ) { value, _ -> offsetX = value }
                                }
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}

/** 列表末尾的「+ 新增」。 */
@Composable
private fun AddCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLowest)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuiIconView(PuiIcon.Plus, Modifier.size(22.dp), tint = scheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            tr("新增", "Add"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary
        )
    }
}

/** 浅底圆角小胶囊按钮。 */
@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.primary.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary
        )
    }
}

@Composable
private fun AddEntryDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(20.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
            Spacer(Modifier.height(14.dp))
            PuiTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = placeholder,
                leadingIcon = PuiIcon.Grid
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PuiButton(
                    text = tr("取消", "Cancel"),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    style = PuiButtonStyle.Ghost
                )
                PuiButton(
                    text = tr("确定", "OK"),
                    onClick = { onConfirm(text.trim()) },
                    modifier = Modifier.weight(1f),
                    enabled = text.isNotBlank()
                )
            }
        }
    }
}
