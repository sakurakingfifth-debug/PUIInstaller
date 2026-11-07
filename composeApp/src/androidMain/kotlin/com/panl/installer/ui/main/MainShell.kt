package com.panl.installer.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.panl.installer.core.Settings
import com.panl.pui.PuiBarMetrics
import com.panl.pui.PuiIconView
import com.panl.installer.R
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.panl.pui.PuiFloatingNavBar
import com.panl.pui.PuiIcon
import com.panl.pui.PuiTopFade
import com.panl.pui.navBackdropSource
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberNavBackdrop
import com.panl.pui.rememberNavigationBarHeight
import com.panl.pui.rememberPuiTopBlurHeight
import kotlinx.coroutines.launch
import com.panl.installer.i18n.tr

/**
 * 应用外壳：四个标签页 + PUI 悬浮导航栏。
 *
 * 导航栏是 PUI 的招牌：底部胶囊 + 毛玻璃。毛玻璃不是叠一层半透明色，
 * 而是把**页面内容**当采样源，在同一个 AGSL 里算出来（见 PuiPlatform.android.kt）。
 * 所以 pager 必须挂 `navBackdropSource`，否则胶囊底下会直接透出列表文字。
 */
@Composable
fun MainShell() {
    val pagerState = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    val navBarH = rememberNavigationBarHeight()
    val blurH = rememberPuiTopBlurHeight(extra = 32.dp)
    val density = LocalDensity.current.density
    val settings = Settings.get(LocalContext.current)

    // 稳定实例：靠 onGloballyPositioned 记录位置，重建会收不到回调
    val backdrop = rememberNavBackdrop()
    // 在组合期读一次，值变了会触发重组 → SideEffect 重跑 → 毛玻璃半径跟着滑块走
    val navBlurPx = settings.navBlurPx.toFloat()
    val blurOn = settings.blurEnabled
    val topBlurPx = settings.topBlurPx
    SideEffect { backdrop.blurPx = navBlurPx }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val itemWidth = minOf(
            PuiBarMetrics.itemWidth,
            (maxWidth - 40.dp - PuiBarMetrics.padding * 2) / NAV_LABELS.size
        )

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = if (blurOn) {
                Modifier
                    .fillMaxSize()
                    // 先登记位置（供蒙版换算），再加模糊 —— 顺序不能反
                    .navBackdropSource(backdrop)
                    .puiProgressiveTopBlur(
                        height = blurH,
                        maxRadius = (topBlurPx / density).dp,
                        backdrop = backdrop
                    )
            } else {
                Modifier.fillMaxSize()
            }
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    topPad = blurH,
                    onOpenSettings = { scope.launch { pagerState.animateScrollToPage(3) } }
                )

                1 -> AppsScreen(topPad = blurH)
                2 -> HistoryScreen(topPad = blurH)
                else -> SettingsScreen(topPad = blurH)
            }
        }

        // 内容滚进模糊区时自然消失
        PuiTopFade(height = blurH)

        PuiFloatingNavBar(
            items = NAV_LABELS,
            selectedIndex = pagerState.currentPage,
            onSelect = { page ->
                if (page != pagerState.currentPage) {
                    scope.launch { pagerState.animateScrollToPage(page) }
                }
            },
            icons = NAV_ICONS,
            // 首页 / 历史 / 设置用官方矢量图，中间「应用」继续用 PUI 的网格图标
            iconContent = { index, tint ->
                when (index) {
                    0 -> NavVectorIcon(R.drawable.ic_nav_home, tint)
                    2 -> NavVectorIcon(R.drawable.ic_nav_history, tint)
                    3 -> NavVectorIcon(R.drawable.ic_nav_settings, tint)
                    else -> PuiIconView(PuiIcon.Grid, Modifier.size(24.dp), tint = tint)
                }
            },
            itemWidth = itemWidth,
            backdrop = if (blurOn) backdrop else null,
            glassAlpha = settings.navGlassPercent / 100f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PuiBarMetrics.bottomMargin + navBarH)
        )
    }
}

/** 导航栏图标：矢量资源 + 运行时着色（保留原图 40% 透明层）。 */
@Composable
private fun NavVectorIcon(res: Int, tint: androidx.compose.ui.graphics.Color) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
    )
}

// 顶层 val 只在类加载时求值一次，切语言不会变；用 @Composable getter 每次重组都重新取
private val NAV_LABELS: List<String>
    @Composable get() = listOf(tr("首页", "Home"), tr("应用", "Apps"), tr("历史", "History"), tr("设置", "Settings"))
private val NAV_ICONS = listOf(PuiIcon.Home, PuiIcon.Grid, PuiIcon.List, PuiIcon.Element)
