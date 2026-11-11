package com.panl.installer.ui.install

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiDivider
import com.panl.installer.i18n.tr

/**
 * 批量安装的界面，浮窗版和全屏版共用（`compact` 只影响间距和字号）。
 *
 * 三个阶段共用一张列表：准备中（正在逐个读取）→ 待确认 → 逐个安装。
 * 每一项自己带状态，装完一项就把这一行标绿/标红，不打断整体流程。
 */
@Composable
fun BatchBody(
    st: InstallUiState.Batch,
    controller: InstallController,
    onClose: () -> Unit,
    compact: Boolean
) {
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = if (compact) 18.dp else 30.dp)
    ) {
        Text(
            tr("批量安装", "Batch install"),
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            batchSubtitle(st),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )

        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            st.items.forEachIndexed { index, item ->
                BatchRow(item, index, compact)
                if (index != st.items.lastIndex) PuiDivider(startIndent = 32.dp)
            }
        }

        Spacer(Modifier.height(10.dp))

        when (st.phase) {
            BatchPhase.Preparing -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PuiButton(
                        text = tr("取消", "Cancel"),
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        style = PuiButtonStyle.Ghost
                    )
                    PuiButton(
                        text = tr("正在读取…", "Reading…"),
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        enabled = false
                    )
                }
            }

            BatchPhase.Ready -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PuiButton(
                        text = tr("取消", "Cancel"),
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        style = PuiButtonStyle.Ghost
                    )
                    PuiButton(
                        text = tr("全部安装", "Install all"),
                        onClick = { controller.runBatch() },
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }

            BatchPhase.Running -> {
                val done = st.items.count { it.status != BatchStatus.Pending && it.status != BatchStatus.Running }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PuiButton(
                        text = tr("停止", "Stop"),
                        onClick = { controller.stopBatch() },
                        modifier = Modifier.weight(1f),
                        style = PuiButtonStyle.Ghost
                    )
                    PuiButton(
                        text = tr("安装中 $done/${st.items.size}", "Installing $done/${st.items.size}"),
                        onClick = {},
                        modifier = Modifier.weight(1.4f),
                        enabled = false
                    )
                }
            }

            BatchPhase.Done -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PuiButton(
                        text = tr("关闭", "Close"),
                        onClick = onClose,
                        modifier = Modifier.weight(1f),
                        style = PuiButtonStyle.Ghost
                    )
                    PuiButton(
                        text = if (st.failed == 0) {
                            tr("全部完成", "All done")
                        } else {
                            tr("完成（${st.failed} 项失败）", "Done (${st.failed} failed)")
                        },
                        onClick = onClose,
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun batchSubtitle(st: InstallUiState.Batch): String = when (st.phase) {
    BatchPhase.Preparing -> tr(
        "正在读取第 ${st.preparing}/${st.items.size} 个文件…",
        "Reading file ${st.preparing}/${st.items.size}…"
    )
    BatchPhase.Ready -> tr(
        "共 ${st.items.size} 项，将按顺序逐个安装",
        "${st.items.size} items — installed one by one"
    )
    BatchPhase.Running -> tr(
        "共 ${st.items.size} 项 · 成功 ${st.ok} · 失败 ${st.failed}",
        "${st.items.size} items · ${st.ok} done · ${st.failed} failed"
    )
    BatchPhase.Done -> tr(
        "共 ${st.items.size} 项 · 成功 ${st.ok} · 失败 ${st.failed}",
        "${st.items.size} items · ${st.ok} done · ${st.failed} failed"
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BatchRow(item: BatchItem, index: Int, compact: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (item.status) {
        BatchStatus.Pending -> scheme.onSurfaceVariant
        BatchStatus.Running -> scheme.primary
        BatchStatus.Done -> scheme.primary
        BatchStatus.Failed -> scheme.error
        BatchStatus.Skipped -> scheme.outline
    }
    val statusText = when (item.status) {
        BatchStatus.Pending -> tr("等待", "Waiting")
        BatchStatus.Running -> tr("安装中…", "Installing…")
        BatchStatus.Done -> tr("完成", "Done")
        BatchStatus.Failed -> tr("失败", "Failed")
        BatchStatus.Skipped -> tr("已跳过", "Skipped")
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (item.status == BatchStatus.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = scheme.primary
                )
            } else {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
        }
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            MarqueeLine(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface
            )
            itemDetail(item)?.let { detail ->
                MarqueeLine(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.status == BatchStatus.Failed) scheme.error else scheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        Text(
            statusText,
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
    }
}

/**
 * 一行文本：放不下就轮播，并且在左右两端做渐隐，不让文字在边缘被硬生生切断。
 *
 * 只有真的超出宽度时才加渐隐层 —— 短名字（`x.apk`）不该被压暗两头。
 * 渐隐用 DstIn 合成实现：先画文字，再用一条「两端透明、中间不透明」的黑条
 * 乘掉 alpha。必须套一层 Offscreen 的 graphicsLayer，否则 DstIn 会把背后的
 * 卡片背景一起啃掉；也必须排在 basicMarquee 外层，否则渐变会跟着文字一起滚。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MarqueeLine(
    text: String,
    style: TextStyle,
    color: Color
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth
        val overflows = remember(text, style, widthPx) {
            widthPx > 0 && measurer.measure(
                text = AnnotatedString(text),
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            ).size.width > widthPx
        }
        val fadePx = with(LocalDensity.current) { 18.dp.toPx() }

        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .then(
                    if (!overflows) {
                        Modifier
                    } else {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                val f = (fadePx / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.4f)
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        f to Color.Black,
                                        1f - f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    }
                )
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    initialDelayMillis = 1200,
                    repeatDelayMillis = 1600,
                    spacing = MarqueeSpacing(48.dp)
                )
        )
    }
}

/** 副标题：安装包给版本，模块给模块版本 + 作者，读不出来的给原因。 */
private fun itemDetail(item: BatchItem): String? {
    item.module?.let { module ->
        return listOf(tr("模块", "Module"), module.version, module.author)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    item.apk?.let { info ->
        val kind = when {
            info.splits.isNotEmpty() -> tr("拆分包", "Split bundle")
            item.name.endsWith(".apk.1", ignoreCase = true) -> tr("APK", "APK")
            else -> tr("APK", "APK")
        }
        return "$kind · ${info.versionName} (${info.versionCode})"
    }
    return item.note
}
