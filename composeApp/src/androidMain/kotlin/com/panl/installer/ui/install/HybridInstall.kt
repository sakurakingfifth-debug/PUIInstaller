package com.panl.installer.ui.install

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
        // 和组件选择弹窗一样：自己开系统模糊，糊掉背后的页面
        val dialogView = LocalView.current
        SideEffect {
            runCatching {
                val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@runCatching
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val lp = window.attributes
                    lp.blurBehindRadius = (36 * dialogView.resources.displayMetrics.density).toInt()
                    window.attributes = lp
                }
            }
        }

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
                    .padding(20.dp)
            ) {
                Text(
                    tr("选择安装类型", "Choose install type"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tr("请选择安装类型", "Pick how this package should be installed"),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                HybridOption(
                    title = info.label,
                    subtitle = tr("包名：${info.packageName}", "Package: ${info.packageName}"),
                    hint = tr("作为普通应用安装（LSPosed 插件照常生效）", "Install as an app (LSPosed module)"),
                    icon = PuiIcon.Grid,
                    onClick = {
                        controller.closeHybridPicker()
                    }
                )
                Spacer(Modifier.height(10.dp))
                HybridOption(
                    title = module.name,
                    subtitle = tr("模块 ID：${module.id}", "Module ID: ${module.id}"),
                    hint = tr("不装应用本体，转成模块交给 root 管理器刷入", "Flash as a module instead of installing the app"),
                    icon = PuiIcon.Download,
                    onClick = { controller.installEmbeddedAsModule() }
                )

                Spacer(Modifier.height(16.dp))
                PuiButton(
                    text = tr("取消", "Cancel"),
                    onClick = { controller.closeHybridPicker() },
                    style = PuiButtonStyle.Ghost
                )
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PuiIconView(icon, Modifier.size(18.dp), tint = scheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.size(6.dp))
            Spacer(Modifier.weight(1f))
            PuiIconView(PuiIcon.ChevronRight, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = scheme.primary)
    }
}
