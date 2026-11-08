package com.panl.installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.panl.installer.R
import com.panl.installer.core.Settings
import com.panl.installer.i18n.tr
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.LocalWallpaperAccent
import com.panl.pui.PuiDialog
import com.panl.pui.PuiDivider
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiListRow
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSegmentedControl
import com.panl.pui.PuiSliderRow
import com.panl.pui.PuiSwitch
import com.panl.pui.PuiSwitchRow
import com.panl.pui.PuiTopFade
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/**
 * 安装与界面。
 *
 * 原来设置页里「安装」和「安装界面」两张卡合并成这一页：
 * 上半部分是安装策略（删包、校验、OSDK），下半部分是界面样式和通知。
 */
class InstallSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                InstallSettingsScreen()
            }
        }
    }
}

@Composable
private fun InstallSettingsScreen() {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    // 和外观页同一套：标题跟着列表滚，模糊只盖住状态栏那一条
    val blurH = rememberPuiTopBlurHeight()
    // 打开「安装后删除安装包」时先说明一次限制（别的应用私有目录里的文件删不掉）
    var showDeleteNotice by remember { mutableStateOf(false) }

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
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = blurH + 4.dp, bottom = 32.dp)
        ) {
            item {
                PuiPageTitle(tr("安装与界面", "Install and UI"))
                Spacer(Modifier.height(14.dp))
            }

            item {
                PuiGroup(title = tr("安装", "Install")) {
                    PuiSwitchRow(
                        icon = PuiIcon.Trash,
                        title = tr("安装后删除安装包", "Delete APK after install"),
                        subtitle = tr("安装成功后删除源 APK 文件", "Remove the source APK once installed"),
                        checked = settings.deleteApkAfterInstall,
                        onChange = {
                            settings.deleteApkAfterInstall = it
                            if (it) showDeleteNotice = true
                        },
                        showDivider = true
                    )
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_tick_circle),
                        title = tr("删除前确认", "Confirm before deleting"),
                        subtitle = tr("删除前再确认一次，避免误删", "Ask once more to avoid accidents"),
                        checked = settings.confirmBeforeDelete,
                        onChange = { settings.confirmBeforeDelete = it },
                        showDivider = true,
                        enabled = settings.deleteApkAfterInstall
                    )
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_import),
                        title = tr("显示 OSDK 目标", "Show OSDK target"),
                        subtitle = "",
                        checked = settings.showOsdk,
                        onChange = { settings.showOsdk = it },
                        showDivider = true
                    )
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_check),
                        title = tr("安装前校验", "Verify before install"),
                        subtitle = tr("检查 zip 结构与 AndroidManifest", "Check the zip structure and AndroidManifest"),
                        checked = settings.verifyBeforeInstall,
                        onChange = { settings.verifyBeforeInstall = it },
                        showDivider = false
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            item {
                PuiGroup(title = tr("安装界面", "Install UI")) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            tr("样式", "Style"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        PuiSegmentedControl(
                            items = listOf(
                                tr("浮窗", "Sheet"),
                                tr("全屏", "Full screen"),
                                tr("实时通知", "Live"),
                                tr("浮窗+通知", "Sheet+live")
                            ),
                            selectedIndex = when (settings.installStyle) {
                                "Sheet" -> 0
                                "Live" -> 2
                                "SheetLive" -> 3
                                else -> 1
                            },
                            onSelect = {
                                settings.installStyle = when (it) {
                                    0 -> "Sheet"
                                    2 -> "Live"
                                    3 -> "SheetLive"
                                    else -> "Full"
                                }
                            }
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            tr("浮窗位置", "Sheet position"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        PuiSegmentedControl(
                            items = listOf(tr("居中", "Center"), tr("底部", "Bottom")),
                            selectedIndex = if (settings.sheetPosition == "Center") 0 else 1,
                            onSelect = { settings.sheetPosition = if (it == 0) "Center" else "Bottom" },
                            enabled = settings.installStyle == "Sheet" ||
                                settings.installStyle == "SheetLive"
                        )
                        Spacer(Modifier.height(14.dp))
                        // 「自动安装」只对纯实时通知有意义（浮窗+通知本来就要点一下安装）；
                        // 「完成后弹出通知」两种走服务的样式都有效
                        val liveDim = if (settings.installStyle == "Live") 1f else 0.45f
                        val popupActive = settings.installStyle == "Live" ||
                            settings.installStyle == "SheetLive"
                        val popupDim = if (popupActive) 1f else 0.45f
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RowIcon(R.drawable.ic_auto_install, settings.installStyle == "Live")
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tr("自动安装", "Auto install"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = liveDim)
                                )
                                Text(
                                    tr(
                                        "实时通知模式下不再询问，直接安装",
                                        "Install right away in live notification mode without asking"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = liveDim)
                                )
                            }
                            PuiSwitch(
                                checked = settings.autoInstallLive,
                                onChange = { settings.autoInstallLive = it },
                                enabled = settings.installStyle == "Live"
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RowIcon(R.drawable.ic_popup_notify, popupActive)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tr("完成后弹出通知", "Popup when done"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = popupDim)
                                )
                                Text(
                                    tr(
                                        "安装结束后弹出横幅提醒",
                                        "Show a banner when an install finishes"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = popupDim)
                                )
                            }
                            PuiSwitch(
                                checked = settings.popupOnDone,
                                onChange = { settings.popupOnDone = it },
                                enabled = popupActive
                            )
                        }
                    }
                    // 通知相关的两项和界面放一起：怎么弹、弹完怎么提醒，是一件事
                    // 上面两行现在也带图标，分隔线按图标缩进，不然会顶到最左边
                    PuiDivider()
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_notification_status),
                        title = tr("安装结果通知", "Install result notification"),
                        subtitle = tr("结束后在通知栏提示", "Notify in the status bar when done"),
                        checked = settings.showNotification,
                        onChange = { settings.showNotification = it },
                        showDivider = true
                    )
                    // 秒数用滑块：0 在左端就是「从不」，越往右越久
                    PuiSliderRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_trash),
                        label = tr("自动清除通知", "Auto-clear notification"),
                        subtitle = tr("安装成功后自动清理通知", "Clear the notification after a successful install"),
                        value = settings.autoClearSeconds.coerceIn(0, 30),
                        enabled = true,
                        min = 0,
                        max = 30,
                        suffix = tr("秒", "s"),
                        showDivider = false,
                        step = 1,
                        valueText = { seconds ->
                            if (seconds == 0) tr("从不", "Never") else "$seconds " + tr("秒", "s")
                        },
                        onChange = { settings.autoClearSeconds = it }
                    )
                }
            }
        }

        if (settings.blurEnabled) PuiTopFade(height = blurH)
    }

    // 首次打开这个开关时提示一次：MANAGE_EXTERNAL_STORAGE 管不到别的应用的私有目录
    if (showDeleteNotice) {
        PuiDialog(
            title = tr("注意", "Note"),
            text = tr(
                "当 APP 声明了应用私有目录时，根据规范 MANAGE_EXTERNAL_STORAGE 要求，不支持该功能。",
                "When an app declares its own private directory, this feature isn't supported under the MANAGE_EXTERNAL_STORAGE rules."
            ) + "\n\n" + tr("举例 APP：Telegram", "Example app: Telegram"),
            confirmText = tr("知道了", "Got it"),
            showCancel = false,
            onDismiss = { showDeleteNotice = false }
        )
    }
}

/**
 * 行首图标：和 [PuiSwitchRow] 里那套保持一致 —— 24dp、取壁纸强调色、禁用时整体压淡。
 *
 * 自定义行（自动安装 / 完成后弹出通知）不是 PuiSwitchRow，图标得自己摆。
 */
@Composable
private fun RowIcon(resId: Int, enabled: Boolean) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = Modifier
            .size(24.dp)
            .alpha(if (enabled) 1f else 0.45f),
        colorFilter = ColorFilter.tint(
            LocalWallpaperAccent.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.primary
        )
    )
    Spacer(Modifier.width(14.dp))
}
