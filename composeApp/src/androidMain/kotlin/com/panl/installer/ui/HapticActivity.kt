package com.panl.installer.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.panl.installer.R
import com.panl.installer.core.Haptics
import com.panl.installer.core.Settings
import com.panl.installer.i18n.tr
import com.panl.installer.ui.anim.SeamlessActivity
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.HapticScene
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSliderRow
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/** 振动反馈：按场景调强度，拖到 0 就是关掉这一项。 */
class HapticActivity : SeamlessActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                HapticScreen()
            }
        }
    }
}

@Composable
private fun HapticScreen() {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    val blurH = rememberPuiTopBlurHeight()

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .puiEdgeHaptic()
                .then(
                    if (settings.blurEnabled) {
                        Modifier.puiProgressiveTopBlur(
                            height = blurH,
                            maxRadius = (settings.topBlurPx / density).dp
                        )
                    } else Modifier
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = blurH + 4.dp, bottom = 32.dp)
        ) {
            item {
                PuiPageTitle(tr("振动反馈", "Haptic feedback"))
                Spacer(Modifier.height(14.dp))
            }

            item {
                PuiGroup(title = tr("场景强度", "Per-scene intensity")) {
                    SceneRow(
                        icon = null,
                        iconRes = R.drawable.ic_phone_vibration,
                        title = tr("滚到顶 / 到底", "Scroll edge"),
                        subtitle = tr("列表拉到头时轻震一下", "A light tick when a list hits its end"),
                        value = settings.hapticEdge,
                        scene = HapticScene.EDGE,
                        showDivider = true,
                        onValue = { settings.hapticEdge = it }
                    )
                    SceneRow(
                        icon = null,
                        iconRes = R.drawable.ic_phone_vibration,
                        title = tr("开关切换", "Toggle switch"),
                        subtitle = tr("打开或关闭任意开关", "Turning any switch on or off"),
                        value = settings.hapticToggle,
                        scene = HapticScene.TOGGLE,
                        showDivider = true,
                        onValue = { settings.hapticToggle = it }
                    )
                    SceneRow(
                        icon = null,
                        iconRes = R.drawable.ic_phone_vibration,
                        title = tr("选择选项", "Select option"),
                        subtitle = tr("点击列表项、分段控件", "Tapping list rows and segmented controls"),
                        value = settings.hapticSelect,
                        scene = HapticScene.SELECT,
                        showDivider = true,
                        onValue = { settings.hapticSelect = it }
                    )
                    SceneRow(
                        icon = null,
                        iconRes = R.drawable.ic_phone_vibration,
                        title = tr("安装完成", "Install finished"),
                        subtitle = tr("装好后两下短震", "A double pulse when an install succeeds"),
                        value = settings.hapticInstall,
                        scene = HapticScene.INSTALL,
                        showDivider = false,
                        onValue = { settings.hapticInstall = it }
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    tr(
                        "拖动滑块时实时试听；调到 0 表示关闭该场景。没有线性马达的设备只能「震 / 不震」，强度调节不会生效。",
                        "Drag to feel the change live. 0 turns the scene off. Devices without a linear motor can only vibrate on or off — the intensity slider won't apply."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SceneRow(
    icon: PuiIcon?,
    iconRes: Int? = null,
    title: String,
    subtitle: String,
    value: Int,
    scene: HapticScene,
    showDivider: Boolean,
    onValue: (Int) -> Unit
) {
    val context = LocalContext.current
    // 拖动时每 140ms 最多试听一次，避免滑块连续回调把马达刷爆
    val lastPreview = remember { mutableLongStateOf(0L) }

    PuiSliderRow(
        icon = icon,
        iconPainter = iconRes?.let { androidx.compose.ui.res.painterResource(it) },
        label = title,
        subtitle = subtitle,
        value = value,
        min = 0,
        max = 100,
        step = 5,
        suffix = "%",
        enabled = true,
        showDivider = showDivider,
        valueText = { v -> if (v == 0) tr("关闭", "Off") else "$v%" },
        onChange = { v ->
            onValue(v)
            val now = System.currentTimeMillis()
            if (now - lastPreview.longValue > 140) {
                lastPreview.longValue = now
                Haptics.preview(context, scene, v)
            }
        }
    )
}
