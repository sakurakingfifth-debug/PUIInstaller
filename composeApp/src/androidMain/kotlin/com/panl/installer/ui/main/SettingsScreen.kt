package com.panl.installer.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.panl.installer.R
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.panl.installer.core.PackageUtils
import com.panl.installer.core.Notifications
import com.panl.installer.i18n.AppLanguage
import com.panl.installer.i18n.I18n
import com.panl.installer.core.Settings
import com.panl.installer.ui.AdvancedActivity
import com.panl.installer.ui.AppearanceActivity
import com.panl.installer.ui.LanguageActivity
import com.panl.installer.ui.LogActivity
import com.panl.installer.ui.AppStoreActivity
import com.panl.installer.ui.SignatureActivity
import com.panl.installer.ui.components.puiCardBackdropBlur
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.rememberTickHaptic
import com.panl.pui.PuiDialog
import com.panl.pui.PuiGroup
import com.panl.pui.PuiDivider
import com.panl.pui.PuiIcon
import com.panl.pui.PuiListRow
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSegmentedControl
import com.panl.pui.PuiSliderRow
import com.panl.pui.PuiSwitch
import com.panl.pui.PuiSwitchRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.Dialog
import com.panl.pui.PuiTextField
import com.panl.pui.PuiIconView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.panl.installer.i18n.tr
import com.panl.installer.ui.InstallSettingsActivity

/** 设置页：安装策略、安全提示、通知、外观、系统入口。 */
@Composable
fun SettingsScreen(topPad: Dp) {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    val privileged = remember { PackageUtils.hasInstallPermission(context) }
    val isDefault = remember { PackageUtils.isDefaultInstaller(context) }
    var showAbout by remember { mutableStateOf(false) }

    // 从系统「所有文件访问」页返回后要刷新状态，靠生命周期事件触发一次重组
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasAllFiles = remember(resumeTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    val tick = rememberTickHaptic()

    val signPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.startActivity(
                Intent(context, SignatureActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .puiCardBackdropBlur(showAbout, (settings.sheetBlurPx / density).dp)
            .puiEdgeHaptic(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad, bottom = 130.dp)
    ) {
        item {
            PuiPageTitle(tr("设置", "Settings"))
        }

        item {
            // 和页面标题之间留 14dp，标题不贴卡片（与其他页面统一）
            Spacer(Modifier.height(14.dp))
            // 无标题卡片：单独一条入口，压在「安装」分组上面
            PuiGroup(title = null) {
                PuiListRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_code_1),
                    title = tr("进阶功能", "Advanced"),
                    titleBadge = { BetaBadge(tr("Beta版本", "Beta")) },
                    subtitle = tr("ROOT及LSPosed功能专区", "ROOT & LSPosed features"),
                    showDivider = false,
                    showChevron = true,
                    onClick = { context.startActivity(Intent(context, AdvancedActivity::class.java)) }
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        item {
            // 外观与导航栏入口：单独一张卡，位置在「安装」分组上面
            PuiGroup(title = tr("主题", "Theme")) {
                PuiListRow(
                    icon = PuiIcon.Palette,
                    title = tr("外观与导航栏", "Appearance and navigation bar"),
                    subtitle = tr("主题、模糊、导航栏", "Theme, blur, navigation bar"),
                    showDivider = false,
                    showChevron = true,
                    onClick = { context.startActivity(Intent(context, AppearanceActivity::class.java)) }
                )
            }
            // 和下面的「安装」分组拉开距离，否则分组标题会贴着卡片
            Spacer(Modifier.height(18.dp))
        }

        item {
            PuiGroup(title = tr("安装", "Install")) {
                PuiListRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_edit_2),
                    title = tr("安装与界面", "Install and UI"),
                    subtitle = tr("安装策略、界面样式、通知", "Install policy, UI style, notifications"),
                    showDivider = false,
                    showChevron = true,
                    onClick = {
                        context.startActivity(Intent(context, InstallSettingsActivity::class.java))
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            PuiGroup(title = tr("安全", "Security")) {
                PuiSwitchRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_info_circle),
                    title = tr("AOSP 签名警告", "AOSP signature warning"),
                    subtitle = tr("检测到公开测试密钥时在安装页提示", "Warn on the install page when a public test key is found"),
                    checked = settings.warnAospSignature,
                    onChange = { settings.warnAospSignature = it },
                    showDivider = true
                )
                PuiListRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_check),
                    title = tr("签名检测", "Signature check"),
                    subtitle = tr("查看签名者与密钥指纹", "View signers and key fingerprints"),
                    showDivider = false,
                    showChevron = true,
                    onClick = { signPicker.launch(arrayOf("application/vnd.android.package-archive")) }
                )
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            PuiGroup(title = null) {
                PuiListRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_language_circle),
                    title = tr("语言", "Language"),
                    subtitle = AppLanguage.fromId(settings.language).let {
                        if (it == AppLanguage.System) tr("跟随系统", "System") else it.nativeName
                    },
                    showDivider = false,
                    showChevron = true,
                    onClick = { context.startActivity(Intent(context, LanguageActivity::class.java)) }
                )
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            PuiGroup(title = tr("系统", "System")) {
                PuiListRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_folder_minus),
                    title = tr("文件访问权限", "File access permission"),
                    subtitle = if (hasAllFiles) {
                        tr("已授予，可直接读取并删除公共目录里的 APK", "Granted — can read and delete APKs in public folders")
                    } else {
                        tr("未授予，file:// 形式的安装包可能读不到、删不掉", "Not granted — file:// APKs may be unreadable or undeletable")
                    },
                    trailingText = if (hasAllFiles) tr("已授予", "Granted") else tr("去授予", "Revoke"),
                    showDivider = true,
                    showChevron = !hasAllFiles,
                    onClick = {
                        if (!hasAllFiles) {
                            val intent = Intent(
                                AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            runCatching { context.startActivity(intent) }.onFailure {
                                runCatching {
                                    context.startActivity(
                                        Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    )
                                }
                            }
                        }
                    }
                )
                PuiListRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_play),
                    title = tr("应用商店能力", "App store capabilities"),
                    subtitle = tr("RAS 要求与自检", "RAS requirements and checks"),
                    showDivider = true,
                    showChevron = true,
                    onClick = { context.startActivity(Intent(context, AppStoreActivity::class.java)) }
                )
                PuiListRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_document_text),
                    title = tr("日志", "Logs"),
                    subtitle = tr("安装器异常与错误记录", "Installer exceptions and errors"),
                    showDivider = true,
                    showChevron = true,
                    onClick = { context.startActivity(Intent(context, LogActivity::class.java)) }
                )
                PuiListRow(
                    icon = null,
                    iconPainter = painterResource(R.drawable.ic_info_sparkle),
                    title = tr("关于", "About"),
                    subtitle = tr("版本 ${appVersion(context)}", "Version ${appVersion(context)}"),
                    showDivider = false,
                    showChevron = true,
                    onClick = {
                        tick()
                        showAbout = true
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "PUI Installer · " + if (privileged) tr("系统安装器模式", "System installer mode") else tr("普通应用模式", "Regular app mode"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
            )
        }
    }

    if (showAbout) {
        PuiDialog(
            scrim = !settings.noScrim,
            title = tr("关于", "About"),
            text = tr("PUI 安装器 ${appVersion(context)}\n\n", "PUI Installer ${appVersion(context)}\n\n") +
                tr("界面基于 PUI 设计系统（Compose）重构，安装能力来自 PackageInstaller 会话。", "The UI is rebuilt with the PUI design system (Compose); installs use PackageInstaller sessions.") +
                tr("系统安装器模式下具备静默安装 / 卸载权限。", "System installer mode has silent install / uninstall permissions."),
            confirmText = tr("知道了", "Got it"),
            dismissText = tr("关闭", "Close"),
            onDismiss = { showAbout = false },
            onConfirm = { showAbout = false }
        )
    }
}

/** 高危操作徽标：和 [BetaBadge] 一个版式，但整体走 error 色，一眼能看出是危险项。 */
@Composable
internal fun DangerBadge(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, scheme.error.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_danger),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            colorFilter = ColorFilter.tint(scheme.error)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.error
        )
    }
}

/** 标题后面的小徽标：细边框 + 烧瓶图标 + 一行小字，用来标 Beta 这类状态。 */
@Composable
internal fun BetaBadge(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_flag),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(scheme.onSurfaceVariant)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant
        )
    }
}
