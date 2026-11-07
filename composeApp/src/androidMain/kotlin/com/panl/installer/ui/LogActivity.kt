package com.panl.installer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panl.installer.R
import com.panl.installer.core.InstallerLog
import com.panl.installer.core.Settings
import com.panl.installer.i18n.tr
import com.panl.installer.ui.components.StatusGlyph
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiButton
import com.panl.pui.PuiButtonStyle
import com.panl.pui.PuiCard
import com.panl.pui.PuiIcon
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiTopFade
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/**
 * 日志页：只列**异常和失败**（安装会话创建失败、系统返回失败、ROOT 兜底失败、
 * 删安装包失败、崩溃），方便用户在出问题时一键分享出来。
 *
 * 顶栏不放图标 —— 标题跟着列表一起滚（和「安装与界面」那些页一致），返回交给系统手势。
 */
class LogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                LogScreen()
            }
        }
    }
}

/** 底部按钮行的高度（不含导航栏）：渐隐高度和列表下内边距都按它算。 */
private val BOTTOM_BAR_H = 74.dp

@Composable
private fun LogScreen() {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    val blurH = rememberPuiTopBlurHeight()

    // 页面打开时读一次；清空后直接置空，不用重新读盘
    var raw by remember { mutableStateOf(InstallerLog.read(context)) }
    // 新的排在上面，报错时第一眼就能看到最近一次
    val entries = remember(raw) {
        raw.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }.asReversed()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (entries.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                PuiPageTitle(tr("日志", "Logs"))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StatusGlyph(
                            icon = PuiIcon.Warning,
                            painter = painterResource(R.drawable.ic_warning_2),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            size = 88.dp
                        )
                        Spacer(Modifier.height(18.dp))
                        Text(
                            tr("暂无日志", "No logs yet"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            tr(
                                "安装器遇到异常时会自动记录在这里",
                                "Anything that goes wrong in the installer is recorded here"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = blurH + 4.dp,
                    bottom = BOTTOM_BAR_H + 76.dp
                )
            ) {
                item {
                    PuiPageTitle(tr("日志", "Logs"))
                    Spacer(Modifier.height(14.dp))
                }
                items(entries) { entry ->
                    PuiCard(Modifier.padding(bottom = 10.dp)) {
                        Text(
                            entry,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }
                }
            }
            if (settings.blurEnabled) PuiTopFade(height = blurH)
        }

        // 底部渐隐：日志往上滚时从按钮上方慢慢淡出，而不是被一刀切掉
        // 底部过渡：上半段渐隐、下半段实色（PuiBottomFade 是纯线性渐变，
        // 到按钮那一带还只有五六成不透明，字照样透出来，所以这里自己给多段）
        val bg = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(BOTTOM_BAR_H + 90.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to bg,
                        1f to bg
                    )
                )
        )

        // 底部操作：分享（没日志时也允许分享，正文会写明「没有记录到异常」）
        val hasClear = entries.isNotEmpty()
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = if (hasClear) Arrangement.spacedBy(12.dp) else Arrangement.Center
        ) {
            if (hasClear) {
                PuiButton(
                    text = tr("清空", "Clear"),
                    onClick = {
                        InstallerLog.clear(context)
                        raw = ""
                    },
                    modifier = Modifier.weight(1f),
                    style = PuiButtonStyle.Ghost
                )
            }
            PuiButton(
                text = tr("分享日志", "Share logs"),
                onClick = { share(context) },
                // 只有分享一个按钮时别拉满整屏，居中放一个正常宽度的就行
                modifier = if (hasClear) Modifier.weight(1f) else Modifier.width(190.dp)
            )
        }
    }
}

private fun share(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, tr("安装器日志", "Installer logs"))
        putExtra(Intent.EXTRA_TEXT, InstallerLog.shareText(context))
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, tr("分享日志", "Share logs")))
    }
}
