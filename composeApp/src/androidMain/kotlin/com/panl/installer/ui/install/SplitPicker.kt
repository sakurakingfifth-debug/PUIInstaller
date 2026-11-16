package com.panl.installer.ui.install

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.panl.installer.core.ApkInfo
import com.panl.installer.i18n.tr
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView

/**
 * 拆分包（.apks / .apkm / .xapk）的组件选择。
 *
 * 为什么需要：这类包里除了 base 还有一堆 split——按 ABI 的、按屏幕密度的、按语言的，
 * 还有各种功能模块。全装进去既占空间又没必要（比如只想要简体中文，日语法语包纯属浪费），
 * 有时候还会因为不匹配的分片导致安装失败。所以装之前让用户自己勾。
 *
 * base.apk 是必须的，不给取消（没有它系统根本不认这个包）。
 */
@Composable
fun SplitPickerHost(controller: InstallController) {
    if (!controller.splitPickerOpen) return
    val info = controller.splitPickerTarget ?: return
    SplitPickerDialog(
        info = info,
        onDismiss = { controller.closeSplitPicker() },
        onConfirm = { selected -> controller.applySplitSelection(selected) }
    )
}

/** 「已选 M/N」这一行：放在安装确认页和批量列表项里，点开就是组件选择。 */
@Composable
fun SplitSummaryRow(
    info: ApkInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (info.splits.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val total = info.splits.size + 1
    val picked = info.splits.count { it.absolutePath in info.selectedSplits } + 1
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuiIconView(PuiIcon.Grid, Modifier.size(16.dp), tint = scheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            tr("安装组件", "Components"),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(8.dp))
        Text(
            tr("$picked / $total", "$picked / $total"),
            style = MaterialTheme.typography.bodySmall,
            color = if (picked == total) scheme.onSurfaceVariant else scheme.primary
        )
        Spacer(Modifier.weight(1f))
        PuiIconView(PuiIcon.ChevronRight, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
    }
}

@Composable
private fun SplitPickerDialog(
    info: ApkInfo,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val all = remember(info) { info.splits.map { it.absolutePath } }
    var selected by remember(info) {
        mutableStateOf(info.selectedSplits.ifEmpty { all.toSet() })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f))
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(20.dp)
        ) {
            Text(
                tr("选择要安装的组件", "Choose components"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr(
                    "base.apk 必装，其余分片可以按需勾选",
                    "base.apk is required; other splits are optional"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // base.apk 固定勾上、点不动
                SplitRow(
                    name = "base.apk",
                    checked = true,
                    enabled = false,
                    onClick = {}
                )
                info.splits.forEach { file ->
                    val key = file.absolutePath
                    SplitRow(
                        name = file.name,
                        checked = key in selected,
                        enabled = true,
                        onClick = {
                            selected = if (key in selected) selected - key else selected + key
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PuiButton(
                    text = tr("全选", "All"),
                    onClick = { selected = all.toSet() },
                    style = PuiButtonStyle.Text
                )
                PuiButton(
                    text = tr("反选", "Invert"),
                    onClick = {
                        selected = all.filterNot { it in selected }.toSet()
                    },
                    style = PuiButtonStyle.Text
                )
                Spacer(Modifier.weight(1f))
                PuiButton(
                    text = tr("取消", "Cancel"),
                    onClick = onDismiss,
                    style = PuiButtonStyle.Ghost
                )
                Spacer(Modifier.width(8.dp))
                // 一个都不选也允许：等于只装 base（有些包的分片本来就可选）
                PuiButton(text = tr("确定", "OK"), onClick = { onConfirm(selected) })
            }
        }
    }
}

@Composable
private fun SplitRow(
    name: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) scheme.primary else scheme.surfaceContainerLowest)
                .border(
                    width = 1.dp,
                    color = if (checked) scheme.primary else scheme.outlineVariant,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                PuiIconView(PuiIcon.Check, Modifier.size(14.dp), tint = scheme.onPrimary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
