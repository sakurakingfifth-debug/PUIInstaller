package com.panl.installer.ui.uninstall

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panl.installer.R
import com.panl.installer.core.Formatters
import com.panl.installer.core.InstalledApp
import com.panl.installer.core.Settings
import com.panl.installer.ui.components.AppIconView
import com.panl.installer.ui.components.BottomActionBar
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiTopFade
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.panl.installer.ui.components.PuiBottomFade
import com.panl.installer.ui.components.PuiIndeterminateBar
import com.panl.installer.ui.components.PuiTopBar
import com.panl.installer.ui.components.SignerCard
import com.panl.installer.ui.components.StatusGlyph
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
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.panl.installer.core.ApkAbi
import com.panl.installer.core.PageSizeCheck
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.panl.installer.i18n.tr

@Composable
fun UninstallScreen(controller: UninstallController, onClose: () -> Unit) {
    val state = controller.state
    val settings = Settings.get(LocalContext.current)
    val density = LocalDensity.current.density
    // 和安装页同一套：状态栏 + 64dp
    val blurH = rememberPuiTopBlurHeight()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .puiCardBackdropBlur(controller.confirmVisible, (settings.sheetBlurPx / density).dp)
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
            when (state) {
                is UninstallUiState.Loading -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            tr("正在读取应用信息…", "Reading app info…"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UninstallUiState.Ready ->
                    ReadyBody(state.app, controller, onClose, settings.warnAospSignature, topPad = blurH)

                is UninstallUiState.Working -> Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        StatusGlyph(
                            icon = PuiIcon.Trash,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            container = MaterialTheme.colorScheme.errorContainer,
                            size = 96.dp
                        )
                        Spacer(Modifier.height(22.dp))
                        Text(
                            if (state.app.isSystem) {
                                tr(
                                    "正在卸载 ${state.app.label} 的更新",
                                    "Removing updates for ${state.app.label}"
                                )
                            } else {
                                tr("正在卸载 ${state.app.label}", "Uninstalling ${state.app.label}")
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        PuiIndeterminateBar()
                    }
                }

                is UninstallUiState.Done -> Column(Modifier.fillMaxSize()) {
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
                            if (state.app.isSystem && !settings.allowUninstallSystemApps) {
                                tr("已卸载更新", "Updates removed")
                            } else {
                                tr("已卸载", "Uninstalled")
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.app.isSystem && !settings.allowUninstallSystemApps) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr("已恢复到出厂版本", "Reverted to the factory version"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    BottomActionBar(
                        primaryText = tr("完成", "Done"),
                        onPrimary = onClose,
                        modifier = Modifier.navigationBarsPadding()
                    )
                }

                is UninstallUiState.NeedsReboot -> Column(Modifier.fillMaxSize()) {
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
                            tr("更新文件已删除", "Update files removed"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr(
                                "重启后安装器会回退到出厂版本。",
                                "After a reboot the installer falls back to the factory version."
                            ),
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
                            text = tr("稍后", "Later"),
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            style = PuiButtonStyle.Ghost
                        )
                        PuiButton(
                            text = tr("立即重启", "Reboot now"),
                            onClick = { controller.rebootNow() },
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }

                is UninstallUiState.DeepRemoved -> Column(Modifier.fillMaxSize()) {
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
                            tr("已彻底卸载", "Completely removed"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr(
                                "已对所有用户卸载。重启后系统分区里的 APK 也会被遮罩掉。",
                                "Uninstalled for all users. After a reboot the APK in the system partition is masked too."
                            ),
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
                            text = tr("稍后", "Later"),
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            style = PuiButtonStyle.Ghost
                        )
                        PuiButton(
                            text = tr("立即重启", "Reboot now"),
                            onClick = { controller.rebootNow() },
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }

                is UninstallUiState.Failed -> Column(Modifier.fillMaxSize()) {
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
                            if (state.app?.isSystem == true) tr("未能卸载更新", "Couldn't remove updates")
                            else tr("未能卸载", "Uninstall failed"),
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
                        PuiButton(
                            text = tr("重试", "Retry"),
                            onClick = { controller.uninstall() },
                            modifier = Modifier.weight(1.2f),
                            style = PuiButtonStyle.Danger
                        )
                    }
                }

                is UninstallUiState.Error -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (settings.blurEnabled) PuiTopFade(height = blurH)
    }

    if (controller.confirmVisible) {
        val app = (state as? UninstallUiState.Ready)?.app
        // 开了「卸载系统应用」之后，系统应用也是整包卸，文案按普通应用走
        val systemApp = app?.isSystem == true && !settings.allowUninstallSystemApps
        PuiDialog(
            scrim = !settings.noScrim,
            title = if (systemApp) tr("卸载更新？", "Remove updates?")
            else tr("卸载应用？", "Uninstall this app?"),
            text = if (systemApp) {
                // 系统应用卸不掉本体：只摘更新，回到出厂版本，数据保留
                tr(
                    "系统应用只能移除更新并恢复到出厂版本，应用数据不会被删除。",
                    "A system app can only lose its updates — it reverts to the factory version and keeps its data."
                )
            } else {
                tr(
                    "${app?.label ?: ""} 将被卸载，应用数据一并删除。",
                    "${app?.label ?: ""} will be uninstalled, along with its data."
                )
            },
            confirmText = if (systemApp) tr("卸载更新", "Remove updates") else tr("卸载", "Uninstall"),
            dismissText = tr("取消", "Cancel"),
            danger = true,
            onDismiss = { controller.dismissConfirm() },
            onConfirm = { controller.uninstall() }
        )
    }

    if (controller.selfBlocked) {
        PuiDialog(
            scrim = !settings.noScrim,
            title = tr("注意事项", "Notes"),
            text = tr(
                "卸载更新后会回退到 ColorOS 的系统安装器，且卸载更新安装器后需要重启手机才可以恢复正常安装使用。",
                "Removing the updates falls back to the ColorOS system installer, and you need to reboot the phone before installing works normally again."
            ),
            confirmText = tr("强制删除", "Force delete"),
            dismissText = tr("取消", "Cancel"),
            danger = true,
            onDismiss = { controller.dismissSelfBlocked() },
            onConfirm = { controller.forceRemoveSelfUpdates() }
        )
    }

    if (controller.noUpdatesVisible) {
        val app = (state as? UninstallUiState.Ready)?.app
        PuiDialog(
            scrim = !settings.noScrim,
            title = tr("没有可卸载的更新", "No updates to remove"),
            text = tr(
                "${app?.label ?: ""} 是系统应用，且没有安装过更新，只能到系统设置里停用或查看详情。",
                "${app?.label ?: ""} is a system app with no updates installed. Use system settings to disable it or see its details."
            ),
            confirmText = tr("系统设置", "Settings"),
            dismissText = tr("取消", "Cancel"),
            onDismiss = { controller.dismissNoUpdates() },
            onConfirm = {
                controller.dismissNoUpdates()
                controller.openSystemAppInfo()
            }
        )
    }
}

@Composable
private fun ReadyBody(
    app: InstalledApp,
    controller: UninstallController,
    onClose: () -> Unit,
    warnAosp: Boolean,
    topPad: Dp
) {
    val settings = Settings.get(LocalContext.current)
    // 16 KB 页兼容性：装完的包要读 APK 里的 .so，异步跑一次
    var supports16K by remember(app.packageName) { mutableStateOf<Boolean?>(null) }
    var abiLabel by remember(app.packageName) { mutableStateOf("") }
    LaunchedEffect(app.packageName) {
        val path = app.apkPath
        if (path == null) {
            supports16K = null
            abiLabel = ""
        } else {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val file = java.io.File(path)
                supports16K = PageSizeCheck.detect(file)
                abiLabel = ApkAbi.describe(ApkAbi.detect(file))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {

        Box(Modifier.weight(1f)) {
        LazyColumn(
            Modifier.fillMaxSize().puiEdgeHaptic(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad + 4.dp, bottom = 40.dp)
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIconView(app.icon, 76.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        app.label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PuiChip(text = app.versionName)
                        if (app.isSystem) PuiChip(text = tr("系统应用", "System apps"))
                    }
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiCard {
                    Column {
                        InfoRow(tr("版本号", "Version code"), "${app.versionCode}", true)
                        InfoRow(tr("安装大小", "Size"), Formatters.size(app.apkSize), true)
                        InfoRow(tr("首次安装", "First installed"), Formatters.date(app.firstInstallTime), true)
                        InfoRow(tr("最近更新", "Last updated"), Formatters.date(app.lastUpdateTime), true)
                        InfoRow(tr("最低系统", "Min Android"), Formatters.sdkLevel(app.minSdk), true)
                        InfoRow(tr("目标系统", "Target Android"), Formatters.sdkLevel(app.targetSdk), true)
                        if (supports16K == true) {
                            InfoRow(tr("16 KB 页面", "16 KB pages"), tr("支持", "Supported"), true)
                        }
                        if (abiLabel.isNotEmpty()) {
                            InfoRow(tr("架构", "Architecture"), abiLabel, true)
                        }
                        InfoRow(
                            tr("APK 路径", "APK path"),
                            app.apkPath ?: "—",
                            false,
                            stacked = true
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SignerCard(
                    signer = app.signers.firstOrNull(),
                    warnAosp = warnAosp,
                    title = tr("签名", "Signature")
                )
            }

            item {
                Spacer(Modifier.height(12.dp))
                val context = LocalContext.current
                PuiCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // 跳系统「应用信息」页
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", app.packageName, null)
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_info_sparkle),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            tr("在系统设置中查看", "View in system settings"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        PuiIconView(
                            PuiIcon.ChevronRight,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            }
            PuiBottomFade(32.dp)
        }

        BottomActionBar(
            primaryText = if (app.isSystem && !settings.allowUninstallSystemApps) {
                tr("卸载更新", "Remove updates")
            } else {
                tr("卸载", "Uninstall")
            },
            onPrimary = { controller.requestUninstall() },
            secondaryText = tr("打开", "Open"),
            onSecondary = { controller.openApp() },
            danger = true,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    showDivider: Boolean,
    stacked: Boolean = false
) {
    Column {
        if (stacked) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
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
                    modifier = Modifier.width(88.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (showDivider) PuiDivider(startIndent = 16.dp)
    }
}

