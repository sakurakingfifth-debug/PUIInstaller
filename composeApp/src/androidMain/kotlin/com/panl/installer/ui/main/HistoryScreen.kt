package com.panl.installer.ui.main

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.panl.installer.core.Formatters
import com.panl.installer.core.InstallHistory
import com.panl.installer.core.InstallRecord
import com.panl.installer.i18n.tr
import com.panl.installer.ui.HistoryDetailActivity
import com.panl.installer.ui.components.AppIconView
import com.panl.installer.ui.components.RelationBadge
import com.panl.installer.ui.components.StatusGlyph
import com.panl.pui.PuiCard
import com.panl.pui.PuiDialog
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiPageTitle
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.rememberNavigationBarHeight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 「历史」页：装成功一次记一条，点进去看当时那个 APK 的完整信息。
 *
 * 交互：
 *  - 左滑卡片露出「删除」，松手弹确认框；
 *  - 长按进入多选，顶部换成「已选 N 项 / 全选 / 删除 / 取消」。
 *
 * 顶部模糊由 [MainShell] 统一挂在 pager 上，这里只管内容的下内边距和底部渐变。
 */
@Composable
fun HistoryScreen(topPad: Dp) {
    val context = LocalContext.current
    val navBarH = rememberNavigationBarHeight()

    var records by remember { mutableStateOf(InstallHistory.read(context)) }
    // pager 会把相邻页一起组合出来，只靠 LaunchedEffect(Unit) 会读到旧数据；
    // 改成每次页面回到前台（装完回来）都重读一次
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) records = InstallHistory.read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    // 待确认删除的记录（左滑删一条、多选删多条都走这里）
    var pendingDelete by remember { mutableStateOf(emptySet<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }

    fun askDelete(times: Set<Long>) {
        if (times.isEmpty()) return
        pendingDelete = times
        showDeleteDialog = true
    }

    // 多选态下系统返回（含侧滑预测性手势）先退出多选，而不是直接退到桌面
    BackHandler(enabled = selecting) { exitSelection() }

    val open = { record: InstallRecord ->
        context.startActivity(
            Intent(context, HistoryDetailActivity::class.java)
                .putExtra(HistoryDetailActivity.EXTRA_TIME, record.time)
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (records.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .puiEdgeHaptic()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(topPad + 4.dp))
                PuiPageTitle(tr("历史", "History"))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusGlyph(
                            icon = PuiIcon.Download,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            size = 88.dp
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            tr("暂无安装记录", "No installs yet"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr(
                                "安装成功的应用会记录在这里",
                                "Apps you install successfully are recorded here"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
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
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = {
                            (
                                fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 8 }
                            ).togetherWith(
                                fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 8 }
                            ).using(
                                SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(200) })
                            )
                        },
                        label = "history-header"
                    ) { inSelection ->
                        if (inSelection) {
                            SelectionHeader(
                                count = selected.size,
                                allSelected = selected.size == records.size,
                                onToggleAll = {
                                    selected = if (selected.size == records.size) {
                                        emptySet()
                                    } else {
                                        records.map { it.time }.toSet()
                                    }
                                },
                                onDelete = { askDelete(selected) },
                                onCancel = { exitSelection() }
                            )
                        } else {
                            PuiPageTitle(tr("历史", "History"))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
                items(records, key = { it.time }) { record ->
                    SwipeableHistoryRow(
                        record = record,
                        selecting = selecting,
                        selected = record.time in selected,
                        onClick = {
                            if (selecting) {
                                selected = if (record.time in selected) {
                                    selected - record.time
                                } else {
                                    selected + record.time
                                }
                            } else {
                                open(record)
                            }
                        },
                        onLongClick = {
                            selecting = true
                            selected = selected + record.time
                        },
                        onSwipeDelete = { askDelete(setOf(record.time)) }
                    )
                }
            }
        }

        // 底部渐变：内容滚到悬浮导航栏那儿是慢慢淡出，不是一刀切
        val bg = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBarH + 120.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to bg,
                        1f to bg
                    )
                )
        )
    }

    if (showDeleteDialog) {
        val many = pendingDelete.size > 1
        PuiDialog(
            title = tr("删除安装记录？", "Delete install record?"),
            text = if (many) {
                tr(
                    "将删除选中的 %d 条记录，只影响历史列表，不会卸载应用。",
                    "This removes %d selected records from the history list only — the apps stay installed."
                ).replace("%d", pendingDelete.size.toString())
            } else {
                tr(
                    "这条记录会从历史列表里移除，不会卸载应用。",
                    "This record will be removed from the history list — the app stays installed."
                )
            },
            confirmText = tr("删除", "Delete"),
            dismissText = tr("取消", "Cancel"),
            danger = true,
            onDismiss = {
                showDeleteDialog = false
                pendingDelete = emptySet()
            },
            onConfirm = {
                InstallHistory.delete(context, pendingDelete)
                records = InstallHistory.read(context)
                selected = selected - pendingDelete
                if (records.isEmpty() || selected.isEmpty()) exitSelection()
            }
        )
    }
}

/** 多选时的顶部工具条：已选数量 + 全选 / 删除 / 取消。 */
@Composable
private fun SelectionHeader(
    count: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            tr("已选 %d 项", "%d selected").replace("%d", count.toString()),
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        HeaderAction(
            text = if (allSelected) tr("取消全选", "Deselect all") else tr("全选", "Select all"),
            color = scheme.primary,
            onClick = onToggleAll
        )
        Spacer(Modifier.width(4.dp))
        HeaderAction(tr("删除", "Delete"), scheme.error, onDelete)
        Spacer(Modifier.width(4.dp))
        HeaderAction(tr("取消", "Cancel"), scheme.onSurfaceVariant, onCancel)
    }
}

@Composable
private fun HeaderAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

/**
 * 可左滑删除的历史卡片。
 *
 * 为什么自己写而不用 SwipeToDismissBox：外面还套着一层 HorizontalPager，
 * 手写 offset 才能把「左滑」和「翻页」的界限控制住（只吃左滑、松手回弹）。
 */
@Composable
private fun SwipeableHistoryRow(
    record: InstallRecord,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxSwipePx = with(density) { 104.dp.toPx() }
    val triggerPx = with(density) { 64.dp.toPx() }

    var offsetX by remember(record.time) { mutableFloatStateOf(0f) }
    // 选中时描边淡入、退出时淡出（直接切 Modifier 会「啪」一下）
    val cardShape = RoundedCornerShape(20.dp)
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "row-border-color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 1.5.dp else 0.dp,
        animationSpec = tween(180),
        label = "row-border-width"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        // 底层的删除底色：卡片左滑后露出来
        if (!selecting) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    tr("删除", "Delete"),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onErrorContainer,
                    modifier = Modifier.padding(end = 22.dp)
                )
            }
        }

        PuiCard(
            Modifier
                .fillMaxWidth()
                .border(borderWidth, borderColor, cardShape)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .then(
                    if (selecting) {
                        Modifier
                    } else {
                        Modifier.pointerInput(record.time) {
                            // 自己判方向：detectHorizontalDragGestures 一过 slop 就会 consume，
                            // 那样往右滑也被吃掉，pager 就翻不了页了。这里只认「往左」。
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
                                        if (offsetX <= -triggerPx) onSwipeDelete()
                                        scope.launch {
                                            animate(offsetX, 0f) { value, _ -> offsetX = value }
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconView(record.icon(), 44.dp, corner = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            record.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        RelationBadge(record.relationEnum)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${record.versionName} (${record.versionCode}) · ${Formatters.size(record.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(Formatters.date(record.time))
                            record.sourceLabel?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = selecting,
                    transitionSpec = {
                        fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                    },
                    label = "row-trailing"
                ) { inSelection ->
                    if (inSelection) {
                        SelectionMark(selected)
                    } else {
                        PuiIconView(PuiIcon.ChevronRight, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 多选态右侧的圆点：选中是实心打勾，未选中是空心圈。 */
@Composable
private fun SelectionMark(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // 填充色、外圈、对勾三个量一起补间：选中是「圈被填满 + 勾弹出来」
    val fill by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "mark-fill"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (selected) 0f else 1f,
        animationSpec = tween(180),
        label = "mark-ring"
    )
    val checkProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(220),
        label = "mark-check"
    )
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, scheme.outlineVariant.copy(alpha = ringAlpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        PuiIconView(
            PuiIcon.Check,
            Modifier
                .size(14.dp)
                .scale(0.6f + 0.4f * checkProgress)
                .alpha(checkProgress),
            tint = scheme.onPrimary
        )
    }
}
