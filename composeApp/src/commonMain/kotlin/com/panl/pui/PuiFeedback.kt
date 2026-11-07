package com.panl.pui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider

/*
 * 反馈层：对话框 + 轻提示。
 *
 * 都是自己画的，不用 M3 的 AlertDialog / Snackbar —— 那两套的圆角（28dp）、
 * 按钮排布（右对齐文字按钮）和 PUI 的「20dp 卡片 + 46dp 实心按钮」不一致。
 */

/**
 * PUI 对话框：28dp 圆角白卡、标题 + 说明 + 底部按钮。
 *
 * 按钮排布是「次要动作在左、主要动作在右」，两个都撑满半宽，
 * 和设置页里的按钮保持同一套尺寸语言。
 */
@Composable
fun PuiDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    confirmText: String = PuiLocale.confirm,
    dismissText: String = PuiLocale.cancel,
    danger: Boolean = false,
    /** 背后的灰色遮罩；关掉就只剩模糊。 */
    scrim: Boolean = true,
    /** 信息类弹窗只要一个「知道了」，不需要取消。 */
    showCancel: Boolean = true,
    /** 贴屏幕底部弹（浮窗安装页那种「卡片在下、确认也在下」的场景）。 */
    bottomAligned: Boolean = false,
    onConfirm: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        // 背景已经做了模糊，遮罩就别再压那么黑，不然看不出是糊的还是黑的
        val dialogView = LocalView.current
        SideEffect {
            runCatching {
                val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@runCatching
                // 只 setDimAmount 是没用的：主题里 backgroundDimEnabled=false 时窗口根本没带
                // FLAG_DIM_BEHIND，dimAmount 会被直接忽略 —— 表现就是弹窗和身后的浮窗糊成一张卡。
                if (scrim) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setDimAmount(0.32f)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setDimAmount(0f)
                }
                window.setGravity(
                    if (bottomAligned) {
                        android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    } else {
                        android.view.Gravity.CENTER
                    }
                )
            }
        }
        Column(
            Modifier
                .widthIn(max = 320.dp)
                // 贴底时留出和导航栏的距离；居中时这点 padding 只让卡片往上挪一点点，看不出差别
                .padding(bottom = if (bottomAligned) 18.dp else 0.dp)
                // 白底卡片叠在白底浮窗上时，没有投影就分不出层次
                .shadow(22.dp, MaterialTheme.shapes.extraLarge)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(scheme.surfaceContainerLowest)
                .padding(22.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showCancel) {
                    PuiButton(
                        dismissText,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        style = PuiButtonStyle.Ghost
                    )
                }
                PuiButton(
                    confirmText,
                    onClick = { onConfirm(); onDismiss() },
                    modifier = Modifier.weight(1f),
                    style = if (danger) PuiButtonStyle.Danger else PuiButtonStyle.Filled
                )
            }
        }
    }
}

/**
 * 轻提示：深色胶囊 + 白字，贴在导航栏上方。
 *
 * 没有 Snackbar 的「动作按钮」，PUI 的反馈是**说完就消失**，
 * 需要动作的场景一律用对话框。
 */
@Composable
fun PuiToast(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.onSurface.luminanceSafe() > 0.5f
    val contentColor = if (dark) Color.White else scheme.surface
    Row(
        modifier
            .clip(CircleShape)
            .background(if (dark) Color(0xFF2A2F2C) else scheme.onSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PuiIconView(PuiIcon.Check, Modifier.size(16.dp), tint = contentColor)
        // 连着弹两条提示时，文字换掉要有过渡 —— 直接换会「啪」地跳一下，
        // 胶囊宽度也是瞬变。这里用 AnimatedContent：旧文案淡出 + 略微缩小，
        // 新文案淡入 + 从 92% 放大回来，容器尺寸跟着一起过渡。
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(tween(190)) + scaleIn(initialScale = 0.92f)) togetherWith
                    (fadeOut(tween(130)) + scaleOut(targetScale = 0.92f))
            },
            label = "toast-text"
        ) { current ->
            Text(
                current,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun Color.luminanceSafe(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
