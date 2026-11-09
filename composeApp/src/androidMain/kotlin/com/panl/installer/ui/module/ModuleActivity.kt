package com.panl.installer.ui.module

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.panl.installer.R
import com.panl.installer.core.ModuleInfo
import com.panl.installer.core.ModuleInspector
import com.panl.installer.core.ModuleInstaller
import com.panl.installer.i18n.tr
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiCard
import com.panl.pui.PuiPageTitle
import kotlinx.coroutines.launch
import java.io.File

/**
 * 模块刷入页。
 *
 * 从安装页跳过来（`InstallController.load()` 认出 zip 里有 `module.prop` 时），
 * 只做一件事：把 zip 交给 Magisk / KernelSU / APatch 刷入，输出原样展示。
 */
class ModuleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 跟安装页一样跟随「安装界面 → 样式」：浮窗那两档用半透明窗口，其余整页
        val style = com.panl.installer.core.Settings.get(this).installStyle
        val sheet = style == com.panl.installer.ui.InstallActivity.STYLE_SHEET ||
            style == com.panl.installer.ui.InstallActivity.STYLE_SHEET_LIVE
        setTheme(if (sheet) R.style.Theme_PuiInstaller_Sheet else R.style.Theme_PuiInstaller)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val path = intent?.getStringExtra(EXTRA_PATH)
        setContent {
            InstallerTheme {
                ModuleScreen(
                    path = path,
                    sheet = sheet,
                    onClose = { finish() },
                    onReboot = { lifecycleScope.launch { com.panl.installer.core.RootShell.reboot() } }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PATH = "module_path"

        fun intent(activity: android.app.Activity, file: File): Intent =
            Intent(activity, ModuleActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath)
    }
}

private sealed interface FlashState {
    data object Idle : FlashState
    data object Running : FlashState
    data class Done(val output: String) : FlashState
    data class Failed(val output: String) : FlashState
}

@Composable
private fun ModuleScreen(path: String?, sheet: Boolean, onClose: () -> Unit, onReboot: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val info: ModuleInfo? = remember(path) {
        path?.let { ModuleInspector.inspect(File(it)) }
    }
    var state by remember { mutableStateOf<FlashState>(FlashState.Idle) }
    var rootMode by remember { mutableStateOf(ModuleInstaller.RootMode.None) }
    var rootChecked by remember { mutableStateOf(false) }

    // 进页面就把 root 方案探一遍，好让按钮文案显示「用 KernelSU 刷入」这类
    androidx.compose.runtime.LaunchedEffect(path) {
        rootMode = ModuleInstaller.detect()
        rootChecked = true
    }

    // 浮窗态：底部一张圆角卡片 + 淡遮罩，点遮罩关闭；整页态：普通全屏
    Box(Modifier.fillMaxSize().then(if (sheet) Modifier else Modifier.background(scheme.background))) {
        if (sheet) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } }
            )
        }
        Column(
            if (sheet) {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(scheme.surfaceContainerLow)
            } else {
                Modifier.fillMaxSize()
            }
        ) {
            Column(
                Modifier
                    .weight(1f, fill = !sheet)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = if (sheet) 22.dp else 40.dp)
            ) {
                PuiPageTitle(tr("刷入模块", "Flash module"))
                Spacer(Modifier.height(14.dp))

                if (info == null) {
                    PuiCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                tr("这不是一个模块包", "Not a module package"),
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr(
                                    "zip 里没有 module.prop，装不了。",
                                    "The zip has no module.prop, so it can't be flashed."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    ModuleCard(info)
                    Spacer(Modifier.height(12.dp))
                    RootCard(rootChecked, rootMode)
                    if (state is FlashState.Done || state is FlashState.Failed) {
                        Spacer(Modifier.height(12.dp))
                        OutputCard(
                            ok = state is FlashState.Done,
                            output = (state as? FlashState.Done)?.output
                                ?: (state as FlashState.Failed).output
                        )
                    }
                }
            }

            // 底部按钮
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    info == null -> PuiButton(
                        text = tr("关闭", "Close"),
                        onClick = onClose,
                        modifier = Modifier.weight(1f)
                    )

                    state is FlashState.Running -> PuiButton(
                        text = tr("正在刷入…", "Flashing…"),
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    )

                    state is FlashState.Done -> {
                        PuiButton(
                            text = tr("稍后", "Later"),
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            style = PuiButtonStyle.Ghost
                        )
                        PuiButton(
                            text = tr("立即重启", "Reboot now"),
                            onClick = onReboot,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    state is FlashState.Failed -> {
                        PuiButton(
                            text = tr("关闭", "Close"),
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            style = PuiButtonStyle.Ghost
                        )
                        PuiButton(
                            text = tr("重试", "Retry"),
                            onClick = { state = FlashState.Idle },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> {
                        PuiButton(
                            text = tr("取消", "Cancel"),
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            style = PuiButtonStyle.Ghost
                        )
                        PuiButton(
                            text = if (rootMode == ModuleInstaller.RootMode.None) {
                                tr("刷入", "Flash")
                            } else {
                                tr("用 ${ModuleInstaller.label(rootMode)} 刷入", "Flash with ${ModuleInstaller.label(rootMode)}")
                            },
                            onClick = {
                                val file = info?.file ?: return@PuiButton
                                state = FlashState.Running
                                scope.launch {
                                    val result = ModuleInstaller.install(file, rootMode)
                                    state = if (result.ok) {
                                        FlashState.Done(result.output)
                                    } else {
                                        FlashState.Failed(result.output)
                                    }
                                    com.panl.installer.core.InstallerLog.record(
                                        context, "MODULE",
                                        "刷入 ${info.id} -> ${if (result.ok) "成功" else "失败"}"
                                    )
                                }
                            },
                            enabled = rootMode != ModuleInstaller.RootMode.None,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(info: ModuleInfo) {
    val scheme = MaterialTheme.colorScheme
    PuiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "M",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    if (info.version.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            info.version,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (info.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    info.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            InfoLine(tr("模块 ID", "Module ID"), info.id)
            if (info.author.isNotBlank()) InfoLine(tr("作者", "Author"), info.author)
        }
    }
}

@Composable
private fun RootCard(checked: Boolean, mode: ModuleInstaller.RootMode) {
    val scheme = MaterialTheme.colorScheme
    PuiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                tr("刷写方式", "Flash method"),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !checked -> tr("检测中…", "Detecting…")
                    mode == ModuleInstaller.RootMode.None -> tr(
                        "没有检测到 Magisk / KernelSU / APatch",
                        "No Magisk / KernelSU / APatch found"
                    )
                    else -> ModuleInstaller.label(mode)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (mode == ModuleInstaller.RootMode.None && checked) scheme.error else scheme.onSurface
            )
        }
    }
}

@Composable
private fun OutputCard(ok: Boolean, output: String) {
    val scheme = MaterialTheme.colorScheme
    PuiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (ok) tr("刷入成功", "Flashed") else tr("刷入失败", "Flash failed"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (ok) scheme.primary else scheme.error
            )
            if (ok) {
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("重启后模块才会生效。", "The module takes effect after a reboot."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            if (output.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    output.take(2000),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface
        )
    }
}
