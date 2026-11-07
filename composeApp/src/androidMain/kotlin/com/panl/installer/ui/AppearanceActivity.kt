package com.panl.installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.view.drawToBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import com.panl.installer.core.Settings
import kotlinx.coroutines.launch
import kotlin.math.hypot
import com.panl.installer.i18n.tr
import com.panl.installer.ui.components.puiCardBackdropBlur
import com.panl.installer.ui.components.ArgbPickerDialog
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiListRow
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSegmentedControl
import com.panl.pui.PuiSliderRow
import com.panl.pui.PuiSwitchRow
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight
import com.panl.pui.rememberTickHaptic

/** 外观与导航栏：主题、配色、模糊、导航栏样式，合并成一个独立页面。 */
class AppearanceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                AppearanceScreen()
            }
        }
    }
}

@Composable
private fun AppearanceScreen() {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    // 标题跟着列表一起滚，模糊带只盖住状态栏这一条
    val blurH = rememberPuiTopBlurHeight()
    var showColorDialog by remember { mutableStateOf(false) }
    // 明暗切换：先把当前这一帧拍成位图铺在上面，再让新主题从点的选项那儿圆形展开
    var oldFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }
    var segBounds by remember { mutableStateOf(Rect.Zero) }
    val reveal = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val activity = context.findActivity()
    val tick = rememberTickHaptic()

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .puiEdgeHaptic()
                .puiCardBackdropBlur(showColorDialog, (settings.sheetBlurPx / density).dp)
                .then(
                    if (settings.blurEnabled) {
                        Modifier.puiProgressiveTopBlur(
                            height = blurH,
                            maxRadius = (settings.topBlurPx / density).dp
                        )
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = blurH + 4.dp, bottom = 32.dp)
        ) {
            item {
                PuiPageTitle(tr("外观与导航栏", "Appearance and navigation bar"))
                // 标题和下面第一张卡片之间留一口气，和其他页面统一 14dp
                Spacer(Modifier.height(14.dp))
            }

            item {
                PuiGroup(title = null) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            tr("主题", "Theme"),
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.onGloballyPositioned { segBounds = it.boundsInRoot() }) {
                            PuiSegmentedControl(
                                items = listOf(tr("跟随系统", "System"), tr("浅色", "Light"), tr("深色", "Dark")),
                                selectedIndex = when (settings.themeMode) {
                                    "Light" -> 1
                                    "Dark" -> 2
                                    else -> 0
                                },
                                onSelect = { index ->
                                    // 先把旧主题这一帧拍下来，切换后垫在底下当「还没被揭开」的部分
                                    oldFrame = runCatching {
                                        activity?.window?.decorView?.drawToBitmap()?.asImageBitmap()
                                    }.getOrNull()
                                    revealOrigin = Offset(
                                        x = segBounds.left + segBounds.width * (index + 0.5f) / 3f,
                                        y = segBounds.center.y
                                    )
                                    settings.themeMode = when (index) {
                                        1 -> "Light"
                                        2 -> "Dark"
                                        else -> "System"
                                    }
                                    scope.launch {
                                        reveal.snapTo(0f)
                                        reveal.animateTo(
                                            1f,
                                            tween(durationMillis = 520, easing = FastOutSlowInEasing)
                                        )
                                        oldFrame = null
                                    }
                                }
                            )
                        }
                    }
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                        Text(
                            tr("主题色", "Theme color"),
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface
                        )
                    }
                    PuiListRow(
                        icon = PuiIcon.Edit,
                        title = tr("自定义 ARGB", "Custom ARGB"),
                        subtitle = if (settings.themeAccentMode == "Custom") {
                            tr(
                                "当前 #%08X，点击重新输入".format(settings.themeAccentArgb),
                                "Current #%08X — tap to change".format(settings.themeAccentArgb)
                            )
                        } else {
                            tr("输入 #AARRGGBB 应用主题色", "Enter #AARRGGBB to apply a color")
                        },
                        showDivider = true,
                        showChevron = true,
                        onClick = {
                            tick()
                            showColorDialog = true
                        }
                    )
                    PuiListRow(
                        icon = PuiIcon.Sun,
                        title = tr("跟随系统壁纸", "Follow wallpaper"),
                        subtitle = tr("用系统从壁纸取的色作为主题色", "Use the color the system picked from your wallpaper"),
                        trailing = {
                            SelectionCheck(visible = settings.themeAccentMode == "Wallpaper")
                        },
                        showDivider = true,
                        onClick = {
                            tick()
                            settings.themeAccentMode = "Wallpaper"
                        }
                    )
                    PuiListRow(
                        icon = PuiIcon.Palette,
                        title = tr("恢复 PUI 主题色", "Reset to PUI color"),
                        subtitle = tr("回到设计系统自带的洋红", "Back to the design system's magenta"),
                        trailing = {
                            SelectionCheck(visible = settings.themeAccentMode == "Default")
                        },
                        showDivider = true,
                        onClick = {
                            tick()
                            settings.themeAccentMode = "Default"
                        }
                    )
                    PuiSwitchRow(
                        icon = PuiIcon.Glow,
                        title = tr("顶部模糊与毛玻璃", "Top blur and frosted glass"),
                        subtitle = "",
                        checked = settings.blurEnabled,
                        onChange = { settings.blurEnabled = it },
                        showDivider = true
                    )
                    PuiSliderRow(
                        icon = PuiIcon.SliderVertical,
                        label = tr("顶部模糊深度", "Top blur depth"),
                        value = settings.topBlurPx,
                        enabled = settings.blurEnabled,
                        min = 2,
                        max = 100,
                        suffix = "px",
                        showDivider = true,
                        step = 1,
                        onChange = { settings.topBlurPx = it }
                    )
                    PuiSliderRow(
                        icon = PuiIcon.Glow,
                        label = tr("浮窗背景模糊", "Sheet background blur"),
                        value = settings.sheetBlurPx,
                        enabled = settings.blurEnabled,
                        min = 2,
                        max = 100,
                        suffix = "px",
                        showDivider = true,
                        step = 1,
                        onChange = { settings.sheetBlurPx = it }
                    )
                    PuiSliderRow(
                        icon = PuiIcon.Squares3,
                        label = tr("卡片展开模糊", "Card blur"),
                        value = settings.cardBlurPx,
                        enabled = settings.blurEnabled,
                        min = 0,
                        max = 120,
                        suffix = "px",
                        showDivider = true,
                        step = 1,
                        onChange = { settings.cardBlurPx = it }
                    )
                    PuiSwitchRow(
                        icon = PuiIcon.Glow,
                        title = tr("关闭遮罩", "Disable scrim"),
                        subtitle = tr(
                            "浮窗和卡片展开不显示灰色遮罩",
                            "No grey scrim behind sheets and dialogs"
                        ),
                        checked = settings.noScrim,
                        onChange = { settings.noScrim = it },
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = tr("导航栏", "Navigation bar")) {
                    PuiSliderRow(
                        icon = PuiIcon.Glow,
                        label = tr("模糊半径", "Blur radius"),
                        value = settings.navBlurPx,
                        enabled = settings.blurEnabled,
                        min = 2,
                        max = 64,
                        suffix = "px",
                        showDivider = true,
                        step = 1,
                        onChange = { settings.navBlurPx = it }
                    )
                    PuiSliderRow(
                        icon = PuiIcon.Sun,
                        label = tr("背景不透明度", "Background opacity"),
                        value = settings.navGlassPercent,
                        enabled = true,
                        min = 0,
                        max = 100,
                        suffix = "%",
                        showDivider = false,
                        step = 10,
                        onChange = { settings.navGlassPercent = it }
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }

        // 旧主题那一帧盖在上面，只保留圆形之外的部分；圆内直接露出新界面
        val frame = oldFrame
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        val p = reveal.value
                        if (p >= 1f) return@drawWithContent
                        // 半径要取到离圆心最远的那个角，偏心时才不会提前盖满
                        val maxR = maxOf(
                            hypot(revealOrigin.x, revealOrigin.y),
                            hypot(size.width - revealOrigin.x, revealOrigin.y),
                            hypot(revealOrigin.x, size.height - revealOrigin.y),
                            hypot(size.width - revealOrigin.x, size.height - revealOrigin.y)
                        )
                        val hole = Path().apply {
                            addOval(Rect(revealOrigin, maxR * p))
                        }
                        clipPath(hole, ClipOp.Difference) { this@drawWithContent.drawContent() }
                    }
            )
        }

        // 标题在列表里，滚走就跟着走；顶部只剩状态条那一条模糊
    }

    if (showColorDialog) {
        ArgbPickerDialog(
            initial = settings.themeAccentArgb,
            onDismiss = { showColorDialog = false },
            onApply = {
                settings.themeAccentArgb = it
                settings.themeAccentMode = "Custom"
                showColorDialog = false
            },
            scrim = !settings.noScrim
        )
    }
}

/** 选中打勾：出现时缩放淡入，取消时缩小淡出，避免勾「啪」地跳出来。 */
@Composable
private fun SelectionCheck(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.5f),
        exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.5f)
    ) {
        PuiIconView(
            PuiIcon.Check,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** 从 Compose 的 Context 里把宿主 Activity 捞出来（拍窗口快照要用）。 */
private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
