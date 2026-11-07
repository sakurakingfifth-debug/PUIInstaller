package com.panl.installer.ui.main

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.panl.installer.core.IconLoader
import com.panl.installer.core.InstalledApp
import com.panl.installer.core.PackageUtils
import com.panl.installer.core.Settings
import com.panl.installer.ui.UninstallActivity
import com.panl.installer.ui.components.AppIconView
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiBadge
import com.panl.pui.PuiDivider
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSearchBar
import com.panl.pui.PuiSegmentedControl
import com.panl.pui.PuiTopFade
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import com.panl.installer.i18n.tr

/** 已安装应用列表：搜索 + 用户/系统筛选，点一行进详情/卸载页。 */
@Composable
fun AppsScreen(topPad: Dp) {
    val context = LocalContext.current
    val settings = Settings.get(context)
    // 系统应用默认隐藏：关掉时只有「用户应用 / 全部」两格
    val filters = if (settings.showSystemApps) listOf(0, 1, 2) else listOf(0, 2)
    var query by remember { mutableStateOf("") }
    // 当前筛选（filter id）：0 用户应用 / 1 系统应用 / 2 全部
    var filter by remember { mutableIntStateOf(0) }
    var all by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        all = PackageUtils.loadInstalled(context, includeSystem = true)
        loading = false
    }

    fun listFor(page: Int): List<InstalledApp> {
        val byFilter = when (page) {
            0 -> all.filter { !it.isSystem }
            1 -> all.filter { it.isSystem }
            else -> all
        }
        if (query.isBlank()) return byFilter
        return byFilter.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    // 不能在这里再套一层横向 Pager：会和外壳的「首页/应用/设置」翻页抢手势，
    // 结果是两边都翻到一半卡住。筛选只走分段控件。
    val list = remember(query, filter, all) { listFor(filter) }
    // 三个筛选各自一个列表状态；分页只由分段控件驱动（userScrollEnabled = false），
    // 这样不会和外壳的「首页/应用/设置」翻页抢手势
    val pagerState = rememberPagerState(initialPage = 0) { filters.size }
    val scope = rememberCoroutineScope()
    val listStates = remember(filters.size) { List(filters.size) { LazyListState() } }
    // 页索引 ↔ filter id 的换算；关掉系统应用后当前页可能越界，拉回最后一页
    val pageIndex = filters.indexOf(filter).coerceAtLeast(0)
    LaunchedEffect(filters.size) {
        if (pagerState.currentPage > filters.lastIndex) {
            pagerState.scrollToPage(filters.lastIndex)
            filter = filters.last()
        }
    }
    var pageWidth by remember { mutableIntStateOf(1) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = topPad)) {
            PuiPageTitle(tr("应用", "Apps"))
            Text(
                if (loading) tr("正在读取应用列表…", "Reading the app list…") else tr("${all.size} 个应用 · 点击查看版本与签名", "${all.size} apps · tap to see version and signature"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )
            PuiSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = tr("搜索应用名或包名", "Search app name or package")
            )
            Spacer(Modifier.height(10.dp))
            // 分段控件跟手滑动：拖动时胶囊跟着手指走，松手吸附到最近一格
            var dragFraction by remember { mutableFloatStateOf(-1f) }
            var segWidth by remember { mutableIntStateOf(0) }
            val haptic = LocalHapticFeedback.current
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { segWidth = it.width }
                    .pointerInput(segWidth) {
                        if (segWidth == 0) return@pointerInput
                        val step = segWidth / filters.size.toFloat()
                        var lastTick = -1
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                lastTick = dragFraction.roundToInt()
                            },
                            onDragEnd = {
                                val target = if (dragFraction >= 0f) {
                                    dragFraction.roundToInt().coerceIn(0, filters.lastIndex)
                                } else {
                                    pageIndex
                                }
                                filter = filters[target]
                                dragFraction = -1f
                                scope.launch { pagerState.animateScrollToPage(target) }
                            },
                            onDragCancel = {
                                val target = if (dragFraction >= 0f) {
                                    dragFraction.roundToInt().coerceIn(0, filters.lastIndex)
                                } else {
                                    pageIndex
                                }
                                filter = filters[target]
                                dragFraction = -1f
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            val from = if (dragFraction < 0f) {
                                pagerState.currentPage + pagerState.currentPageOffsetFraction
                            } else {
                                dragFraction
                            }
                            // 手指往右 → 胶囊和页面都往右
                            val to = (from + dragAmount / step)
                                .coerceIn(0f, filters.lastIndex.toFloat())
                            dragFraction = to
                            // 同步把下面的列表也推走（按页宽换算成像素）
                            pagerState.dispatchRawDelta((to - from) * pageWidth)
                            val tick = to.roundToInt()
                            if (tick != lastTick) {
                                lastTick = tick
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
            ) {
                PuiSegmentedControl(
                    items = filters.map {
                        when (it) {
                            0 -> tr("用户应用", "User apps")
                            1 -> tr("系统应用", "System apps")
                            else -> tr("全部", "All")
                        }
                    },
                    selectedIndex = pageIndex,
                    onSelect = { index ->
                        // 列表现在由 pager 驱动，点选也要把 pager 推过去
                        filter = filters[index]
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    dragFraction = dragFraction.takeIf { it >= 0f }
                )
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    tr("读取中…", "Loading…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 静止时不留空隙（只 8dp），一旦开始滚动才浮出渐隐带
            val scrolled by remember {
                derivedStateOf {
                    val s = listStates[pagerState.currentPage]
                    s.firstVisibleItemIndex > 0 || s.firstVisibleItemScrollOffset > 0
                }
            }
            val fadeAlpha by animateFloatAsState(
                targetValue = if (scrolled) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "apps-top-fade"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { pageWidth = it.width }
            ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageList = listFor(page)
                LazyColumn(
                    state = listStates[page],
                    contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp),
                    modifier = Modifier.fillMaxSize().puiEdgeHaptic()
                ) {
                    items(pageList, key = { it.packageName }) { app ->
                        AppRow(app) {
                            context.startActivity(
                                Intent(context, UninstallActivity::class.java)
                                    .putExtra(UninstallActivity.EXTRA_PACKAGE, app.packageName)
                            )
                        }
                    }
                    if (pageList.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    tr("没有匹配的应用", "No matching apps"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            // 顶部渐隐：静止时完全不出现（不挡第一行），一滚动就淡入
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .graphicsLayer { alpha = fadeAlpha }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background.copy(alpha = 0f)
                            )
                        )
                    )
            )
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<Bitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) { IconLoader.icon(context, app.packageName) }
    }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconView(bitmap = icon, size = 44.dp, corner = 13.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isSystem) {
                        Spacer(Modifier.width(6.dp))
                        PuiBadge(
                            tr("系统", "System"),
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${app.versionName} (${app.versionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            PuiIconView(
                PuiIcon.ChevronRight,
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PuiDivider(startIndent = 72.dp)
    }
}
