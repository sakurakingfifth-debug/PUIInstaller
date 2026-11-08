package com.panl.installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.panl.installer.core.InstallerLog
import com.panl.installer.core.Settings
import com.panl.installer.i18n.tr
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiTextField
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/**
 * 安装器全局设置（移植自 InstallerX-Revived 的 InstallerGlobalSettingsPage，只搬功能不搬 UI）。
 *
 * 三组都是「白名单 / 黑名单」：
 *  - 安装来源预设：只允许列表里的应用发起安装（空 = 不限制）；
 *  - 根据包名禁止安装应用程序；
 *  - 根据 SharedUID 禁止安装应用程序。
 */
class InstallerGlobalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                InstallerGlobalScreen()
            }
        }
    }
}

/** 三种列表共用的目标。 */
private enum class ListKind { Sources, Packages, SharedUids }

@Composable
private fun InstallerGlobalScreen() {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    val blurH = rememberPuiTopBlurHeight()

    var tick by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf<ListKind?>(null) }

    val sources = remember(tick) { settings.managedInstallerPackagesRaw }
    val packages = remember(tick) { settings.blacklistPackagesRaw }
    val uids = remember(tick) { settings.blacklistSharedUidRaw }

    fun write(kind: ListKind, value: String) {
        when (kind) {
            ListKind.Sources -> settings.managedInstallerPackagesRaw = value
            ListKind.Packages -> settings.blacklistPackagesRaw = value
            ListKind.SharedUids -> settings.blacklistSharedUidRaw = value
        }
        tick++
    }

    Box(Modifier.fillMaxSize().background(scheme.background)) {
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
                PuiPageTitle(tr("安装器全局设置", "Installer settings"))
                Spacer(Modifier.height(14.dp))
            }

            item {
                PuiGroup(title = tr("来源预设", "Install sources")) {
                    EntryList(
                        raw = sources,
                        emptyTitle = tr("没有预设安装来源", "No preset install sources"),
                        emptyHint = tr("点击下方的「新增」按钮开始", "Tap “Add” below to start"),
                        onRemove = { line -> write(ListKind.Sources, removeLine(sources, line)) },
                        onAdd = { adding = ListKind.Sources }
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = tr("根据包名禁止安装应用程序", "Block installs by package name")) {
                    EntryList(
                        raw = packages,
                        emptyTitle = tr("没有需要禁止安装的应用包名", "No package names blocked"),
                        emptyHint = tr("点击下方的「新增」按钮开始", "Tap “Add” below to start"),
                        onRemove = { line -> write(ListKind.Packages, removeLine(packages, line)) },
                        onAdd = { adding = ListKind.Packages }
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = tr("根据 SharedUID 禁止安装应用程序", "Block installs by SharedUID")) {
                    EntryList(
                        raw = uids,
                        emptyTitle = tr("没有需要禁止安装的应用 SharedUID", "No SharedUIDs blocked"),
                        emptyHint = tr("点击下方的「新增」按钮开始", "Tap “Add” below to start"),
                        onRemove = { line -> write(ListKind.SharedUids, removeLine(uids, line)) },
                        onAdd = { adding = ListKind.SharedUids }
                    )
                }
            }
        }
    }

    adding?.let { kind ->
        val title: String
        val hint: String
        when (kind) {
            ListKind.Sources -> {
                title = tr("新增安装来源", "Add install source")
                hint = tr("包名，例如 com.android.vending", "Package name, e.g. com.android.vending")
            }
            ListKind.Packages -> {
                title = tr("新增禁止安装的包名", "Add blocked package")
                hint = tr("包名，例如 com.example.app", "Package name, e.g. com.example.app")
            }
            ListKind.SharedUids -> {
                title = tr("新增禁止安装的 SharedUID", "Add blocked SharedUID")
                hint = tr("UID 值，例如 1000 或 android.uid.phone", "UID value, e.g. 1000 or android.uid.phone")
            }
        }
        AddEntryDialog(
            title = title,
            placeholder = hint,
            onDismiss = { adding = null },
            onConfirm = { value ->
                val raw = when (kind) {
                    ListKind.Sources -> sources
                    ListKind.Packages -> packages
                    ListKind.SharedUids -> uids
                }
                write(kind, if (raw.isBlank()) value else raw.trimEnd() + "\n" + value)
                InstallerLog.record(context, "SETTINGS", "新增 $value")
                adding = null
            }
        )
    }
}

private fun removeLine(raw: String, line: String): String =
    raw.lineSequence().filter { it.trim() != line.trim() }.joinToString("\n")

/** 列表主体：每行「名称 + 副标题 + 删除」，底部一个「+ 新增」。 */
@Composable
private fun EntryList(
    raw: String,
    emptyTitle: String,
    emptyHint: String,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val items = raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

    if (items.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                emptyTitle,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
    } else {
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    // 「包名 显示名」：有显示名就把显示名放上面
                    Text(
                        item.substringAfter(' ', "").ifBlank { item },
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.contains(' ')) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            item.substringBefore(' '),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                PillButton(text = tr("删除", "Remove"), onClick = { onRemove(item) })
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Spacer(Modifier.weight(1f))
        PillButton(text = tr("新增", "Add"), onClick = onAdd, leadingPlus = true)
    }
}

/** 截图里那种浅底圆角小胶囊按钮。 */
@Composable
private fun PillButton(text: String, onClick: () -> Unit, leadingPlus: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.primary.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingPlus) {
            PuiIconView(PuiIcon.Plus, Modifier.size(16.dp), tint = scheme.primary)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
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
