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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.panl.installer.core.InstallerLog
import com.panl.installer.core.Settings
import com.panl.installer.i18n.tr
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiSwitchRow
import com.panl.pui.PuiTextField
import com.panl.pui.PuiIconView

/**
 * 安装器全局设置（移植自 InstallerX-Revived 的 InstallerGlobalSettingsPage，只搬功能不搬 UI）。
 *
 * 三组：
 *  - 签名校验：关掉就跳过「签名不一致」预检；
 *  - Xposed 检测：装包前扫一下有没有 Xposed 模块；
 *  - 包名黑名单 / 受管安装器包：命中就拒绝安装。
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

@Composable
private fun InstallerGlobalScreen() {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    val blurH = rememberPuiTopBlurHeight()

    // 列表类设置改的是字符串，用 tick 触发重读
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    val blacklist = remember(tick) { settings.blacklistPackagesRaw }
    val managed = remember(tick) { settings.managedInstallerPackagesRaw }

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

            // ---------------- 签名校验 ----------------
            item {
                PuiGroup(title = tr("签名校验", "Signature check")) {
                    PuiSwitchRow(
                        icon = PuiIcon.Check,
                        title = tr("校验应用签名", "Check app signature"),
                        subtitle = tr(
                            "安装前比对签名，和已安装版本不一致就拦下来",
                            "Compare signatures before installing and block mismatches"
                        ),
                        checked = settings.checkAppSignature,
                        onChange = { settings.checkAppSignature = it },
                        showDivider = true
                    )
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = null,
                        title = tr("校验来源戳", "Check source stamp"),
                        subtitle = tr(
                            "同时校验 APK 签名方案 v2+ 的来源戳",
                            "Also verify the source stamp of APK Signature Scheme v2+"
                        ),
                        checked = settings.checkSourceStamp,
                        onChange = { settings.checkSourceStamp = it },
                        showDivider = true,
                        enabled = settings.checkAppSignature
                    )
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = null,
                        title = tr("签名一致时显示信息", "Show info when matched"),
                        subtitle = tr(
                            "签名对得上时也把签名信息显示出来",
                            "Show signature info even when the signatures match"
                        ),
                        checked = settings.showSignatureInfoOnMatch,
                        onChange = { settings.showSignatureInfoOnMatch = it },
                        showDivider = true,
                        enabled = settings.checkAppSignature
                    )
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = null,
                        title = tr("显示签名详情", "Show signature details"),
                        subtitle = tr(
                            "展示指纹、算法、证书链等完整信息",
                            "Show fingerprints, algorithms and the certificate chain"
                        ),
                        checked = settings.showSignatureDetails,
                        onChange = { settings.showSignatureDetails = it },
                        showDivider = false,
                        enabled = settings.checkAppSignature
                    )
                }
            }

            // ---------------- Xposed 检测 ----------------
            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = tr("Xposed 检测", "Xposed detection")) {
                    PuiSwitchRow(
                        icon = PuiIcon.Warning,
                        title = tr("检测 Xposed 模块", "Detect Xposed modules"),
                        subtitle = tr(
                            "安装前扫一遍 APK，看是否带了 Xposed 模块",
                            "Scan the APK before installing to see if it ships an Xposed module"
                        ),
                        checked = settings.detectXposedModule,
                        onChange = { settings.detectXposedModule = it },
                        showDivider = true
                    )
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = null,
                        title = tr("快捷打开 LSPosed", "Quick open LSPosed"),
                        subtitle = tr(
                            "检测到模块时提供跳转 LSPosed 的入口",
                            "Offer a shortcut to LSPosed when a module is found"
                        ),
                        checked = settings.quickOpenLSPosed,
                        onChange = { settings.quickOpenLSPosed = it },
                        showDivider = false,
                        enabled = settings.detectXposedModule
                    )
                }
            }

            // ---------------- 包名黑名单 ----------------
            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = tr("包名黑名单", "Package blacklist")) {
                    ListEditor(
                        raw = blacklist,
                        empty = tr("没有屏蔽任何应用", "No blocked packages"),
                        onAdd = { editing = EditTarget.Blacklist },
                        onRemove = { line ->
                            settings.blacklistPackagesRaw = removeLine(blacklist, line)
                            tick++
                        }
                    )
                }
            }

            // ---------------- 受管安装器包 ----------------
            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = tr("受管安装器包", "Managed installers")) {
                    ListEditor(
                        raw = managed,
                        empty = tr(
                            "为空时不限制，谁都能发起安装",
                            "Empty means any app can start an install"
                        ),
                        onAdd = { editing = EditTarget.Managed },
                        onRemove = { line ->
                            settings.managedInstallerPackagesRaw = removeLine(managed, line)
                            tick++
                        }
                    )
                }
            }
        }
    }

    editing?.let { target ->
        val isBlacklist = target == EditTarget.Blacklist
        AddPackageDialog(
            title = if (isBlacklist) {
                tr("添加黑名单", "Add to blacklist")
            } else {
                tr("添加受管安装器", "Add managed installer")
            },
            placeholder = tr("包名，例如 com.example.app", "Package name, e.g. com.example.app"),
            onDismiss = { editing = null },
            onConfirm = { pkg ->
                val raw = if (isBlacklist) blacklist else managed
                val next = if (raw.isBlank()) pkg else raw.trimEnd() + "\n" + pkg
                if (isBlacklist) {
                    settings.blacklistPackagesRaw = next
                } else {
                    settings.managedInstallerPackagesRaw = next
                }
                InstallerLog.record(
                    context,
                    "SETTINGS",
                    (if (isBlacklist) "加入黑名单 " else "加入受管安装器 ") + pkg
                )
                tick++
                editing = null
            }
        )
    }
}

private enum class EditTarget { Blacklist, Managed }

private fun removeLine(raw: String, line: String): String =
    raw.lineSequence().filter { it.trim() != line.trim() }.joinToString("\n")

/** 一行一个的列表：每行右边一个删除按钮，最后一行是「添加」。 */
@Composable
private fun ListEditor(
    raw: String,
    empty: String,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val items = raw.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

    if (items.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                empty,
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
                Text(
                    item,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onRemove(item) },
                    contentAlignment = Alignment.Center
                ) {
                    PuiIconView(PuiIcon.Close, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuiIconView(PuiIcon.Plus, Modifier.size(20.dp), tint = scheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(
            tr("添加", "Add"),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.primary
        )
    }
}

@Composable
private fun AddPackageDialog(
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
