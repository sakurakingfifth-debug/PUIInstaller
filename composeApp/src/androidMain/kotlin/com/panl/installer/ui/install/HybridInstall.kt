package com.panl.installer.ui.install

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.panl.installer.i18n.tr
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView

/**
 * 「APK 本身也是 Zygisk 模块」时的安装方式选择。
 *
 * 这类包（TCQT、WeKit 之类）根目录同时带着 `module.prop` + `zygisk/<abi>.so`，
 * 既可以当普通应用装（走 LSPosed 插件那条路），也可以不装本体、直接把同一个文件
 * 交给 root 管理器当模块刷。用户装之前必须能选，不然只会默认装成应用。
 */
@Composable
fun HybridInstallHost(controller: InstallController) {
    if (!controller.hybridPickerOpen) return
    val st = controller.state as? InstallUiState.Ready ?: return
    val module = st.embeddedModule ?: return
    val info = st.info
    val scheme = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = { controller.closeHybridPicker() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 弹窗自己开系统模糊，半径跟随设置里的「浮窗模糊」
        DialogBlurBehind()

        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    tr("发现目标为双模块", "Dual-mode package detected"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("请选择安装类型", "Pick how this package should be installed"),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                HybridOption(
                    title = info.label,
                    subtitle = tr("包名：${info.packageName}", "Package: ${info.packageName}"),
                    hint = tr("以LSPosed插件形式安装", "Install as an LSPosed plugin"),
                    icon = PuiIcon.Grid,
                    onClick = {
                        controller.closeHybridPicker()
                    }
                )
                Spacer(Modifier.height(10.dp))
                HybridOption(
                    title = module.name,
                    subtitle = tr("模块 ID：${module.id}", "Module ID: ${module.id}"),
                    hint = tr("以Zygisk模块形式刷入", "Flash as a Zygisk module"),
                    icon = PuiIcon.Download,
                    onClick = { controller.installEmbeddedAsModule() }
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    PuiButton(
                        text = tr("取消", "Cancel"),
                        onClick = { controller.closeHybridPicker() },
                        modifier = Modifier.widthIn(min = 132.dp),
                        style = PuiButtonStyle.Ghost
                    )
                }
            }
        }
    }
}

@Composable
private fun HybridOption(
    title: String,
    subtitle: String,
    hint: String,
    icon: PuiIcon,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 居中版：图标在上、文字横排居中，不再放右侧箭头（箭头是右对齐布局的语言）
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerLowest)
            // 浅色主题下卡片和白底几乎同色，加一道细边让「可点」这件事看得出来
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PuiIconView(icon, Modifier.size(22.dp), tint = scheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(3.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(3.dp))
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
