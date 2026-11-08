package com.panl.installer.ui.main

import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.panl.installer.core.PackageUtils
import com.panl.installer.core.Settings
import com.panl.installer.ui.components.puiCardBackdropBlur
import com.panl.installer.ui.components.ActionTile
import com.panl.installer.ui.AdvancedActivity
import com.panl.installer.ui.AppStoreActivity
import com.panl.installer.ui.AppearanceActivity
import com.panl.installer.ui.InstallActivity
import com.panl.installer.ui.InstallSettingsActivity
import com.panl.installer.ui.SignatureActivity
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.rememberTickHaptic
import com.panl.pui.PuiDialog
import com.panl.pui.PuiGroup
import com.panl.pui.PuiHeroCard
import com.panl.pui.PuiIcon
import com.panl.pui.PuiListRow
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSectionHeader
import com.panl.installer.i18n.tr

/**
 * 首页：状态 + 快捷入口 + 功能清单。
 *
 * 加新功能时只需要在 [FEATURES] 里加一条 —— 行的样式、分隔线、点击行为都是统一的。
 */
@Composable
fun HomeScreen(
    topPad: Dp,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember { appVersion(context) }
    val privileged = remember { PackageUtils.hasInstallPermission(context) }
    val isDefault = remember { PackageUtils.isDefaultInstaller(context) }
    var showAbout by remember { mutableStateOf(false) }
    val settings = Settings.get(context)
    val density = LocalDensity.current.density

    val installPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.startActivity(
                Intent(context, InstallActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }
    val signPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.startActivity(
                Intent(context, SignatureActivity::class.java)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
    }

    val tick = rememberTickHaptic()

    // 首页只留「跳转入口」：三个设置页 + 关于。具体功能都在对应的页面里。
    val features = listOf(
        Feature(
            iconRes = R.drawable.ic_code_1,
            title = tr("进阶功能", "Advanced"),
            subtitle = tr("ROOT及LSPosed功能专区", "ROOT & LSPosed features"),
            badge = { BetaBadge(tr("Beta版本", "Beta")) },
            onClick = { context.startActivity(Intent(context, AdvancedActivity::class.java)) }
        ),
        Feature(
            icon = PuiIcon.Palette,
            title = tr("外观与导航栏", "Appearance and navigation bar"),
            subtitle = tr("主题、模糊、导航栏", "Theme, blur, navigation bar"),
            onClick = { context.startActivity(Intent(context, AppearanceActivity::class.java)) }
        ),
        Feature(
            iconRes = R.drawable.ic_edit_2,
            title = tr("安装与界面", "Install and UI"),
            subtitle = tr("安装策略、界面样式、通知", "Install policy, UI style, notifications"),
            onClick = { context.startActivity(Intent(context, InstallSettingsActivity::class.java)) }
        ),
        Feature(
            iconRes = R.drawable.ic_info_sparkle,
            title = tr("关于", "About"),
            subtitle = tr("版本信息与开源说明", "Version info and open-source notes"),
            onClick = {
                tick()
                showAbout = true
            }
        )
    )

    LazyColumn(
        modifier = Modifier
            .puiEdgeHaptic()
            .puiCardBackdropBlur(showAbout, (settings.sheetBlurPx / density).dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad, bottom = 130.dp)
    ) {
        item {
            PuiPageTitle(tr("安装器", "Installer"))
            // 标题下不再挂副标题，只留一点呼吸空间
            Spacer(Modifier.height(14.dp))
        }

        item {
            PuiHeroCard(
                title = if (privileged) tr("系统安装器", "System installer") else tr("安装器", "Installer"),
                subtitle = if (privileged) {
                    if (isDefault) tr("已设为默认 · 安装无需二次确认", "Default installer · no confirmation needed")
                    else tr("具备静默安装权限 · 尚未设为默认", "Has silent-install permission · not the default yet")
                } else {
                    tr("缺少系统权限，安装时会走系统确认流程", "Missing system permissions — installs go through system confirmation")
                },
                badge = if (isDefault) tr("默认", "Default") else null,
                icon = PuiIcon.Download,
                iconPainter = painterResource(R.drawable.ic_direct_inbox),
                onClick = onOpenSettings
            )
        }

        item {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(
                    iconRes = R.drawable.ic_folder_open,
                    title = tr("选择安装包", "Choose APK"),
                    subtitle = tr("从文件里挑一个 APK", "Pick an APK from your files"),
                    modifier = Modifier.weight(1f)
                ) {
                    // 开了 .apk.1 支持就放开所有文件，否则 QQ/微信 下载的包在选择器里根本看不到
                    installPicker.launch(
                        if (settings.apk1Support) arrayOf(APK_MIME, "*/*") else arrayOf(APK_MIME)
                    )
                }

                ActionTile(
                    iconRes = R.drawable.ic_check,
                    title = tr("签名检测", "Signature check"),
                    subtitle = tr("看看谁签的名", "See who signed it"),
                    modifier = Modifier.weight(1f)
                ) { signPicker.launch(arrayOf(APK_MIME)) }
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            PuiSectionHeader(
                title = tr("功能", "Features"),
                subtitle = tr("快捷跳转", "Quick shortcuts")
            )
        }

        item {
            PuiGroup(title = null) {
                features.forEachIndexed { index, feature ->
                    PuiListRow(
                        icon = feature.icon,
                        iconPainter = feature.iconRes?.let { painterResource(it) },
                        title = feature.title,
                        titleBadge = feature.badge,
                        subtitle = feature.subtitle,
                        showDivider = index != features.lastIndex,
                        showChevron = true,
                        onClick = feature.onClick
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                tr("PUI Installer · 系统安装器重构", "PUI Installer · a rebuilt system installer"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }

    if (showAbout) {
        PuiDialog(
            scrim = !settings.noScrim,
            title = tr("关于", "About"),
            text = tr("PUI 安装器 $versionName\n\n", "PUI Installer $versionName\n\n") +
                tr("以 AOSP 的 PackageInstaller 为基线重写的安装 / 卸载界面，", "An install / uninstall UI rebuilt on top of AOSP's PackageInstaller, ") +
                tr("界面使用 PUI 设计系统（Compose）实现。\n\n", "built with the PUI design system (Compose).\n\n") +
                tr("安装权限：", "Install permission: ") + if (privileged) tr("已具备（系统安装器）", "granted (system installer)") else tr("未具备（普通应用）", "not granted (regular app)"),
            confirmText = tr("知道了", "Got it"),
            dismissText = tr("关闭", "Close"),
            onDismiss = { showAbout = false },
            onConfirm = { showAbout = false }
        )
    }
}

private data class Feature(
    val icon: PuiIcon? = null,
    /** 非空时用它代替 [icon]（官方矢量图）。 */
    val iconRes: Int? = null,
    /** 标题右边的徽标（例如「Beta版本」）。 */
    val badge: (@Composable () -> Unit)? = null,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

private const val APK_MIME = "application/vnd.android.package-archive"

fun appVersion(context: android.content.Context): String = runCatching {
    val pi = if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    pi.versionName ?: "—"
}.getOrDefault("—")
