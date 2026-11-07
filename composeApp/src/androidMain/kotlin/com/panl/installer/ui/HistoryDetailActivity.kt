package com.panl.installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panl.installer.core.Formatters
import com.panl.installer.core.InstallHistory
import com.panl.installer.core.InstallRecord
import com.panl.installer.core.Settings
import com.panl.installer.i18n.tr
import com.panl.installer.ui.components.AppIconView
import com.panl.installer.ui.components.RelationBadge
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiCard
import com.panl.pui.PuiIcon
import com.panl.pui.PuiNoticeCard
import com.panl.pui.PuiNoticeTone
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiTopFade
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/**
 * 安装详情：展示**当时那个 APK** 的信息快照（不是现在设备上装着的那个）。
 *
 * 和日志页一样不放顶栏图标，返回交给系统手势；顶部有模糊，底部内容渐隐。
 */
class HistoryDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val time = intent?.getLongExtra(EXTRA_TIME, -1L) ?: -1L
        setContent {
            InstallerTheme {
                HistoryDetailScreen(time)
            }
        }
    }

    companion object {
        const val EXTRA_TIME = "com.panl.installer.extra.HISTORY_TIME"
    }
}

@Composable
private fun HistoryDetailScreen(time: Long) {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    // 这页内容少，标题按默认的「状态栏 + 64dp」会显得太靠下；
    // 模糊带跟着一起收窄，标题才不会落进被糊掉的区域
    val blurH = rememberPuiTopBlurHeight(extra = 20.dp)
    val record = remember(time) { InstallHistory.find(context, time) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (record == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Text(
                    tr("这条记录已经不存在了", "This record no longer exists"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
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
                    bottom = 56.dp
                )
            ) {
                item {
                    PuiPageTitle(tr("安装详情", "Install details"))
                    Spacer(Modifier.height(14.dp))
                }

                item { HeroCard(record) }

                item {
                    Spacer(Modifier.height(12.dp))
                    DetailCard(record)
                }

                if (record.isAosp) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        PuiNoticeCard(
                            title = tr("AOSP 公开密钥签名", "Signed with an AOSP public key"),
                            text = tr(
                                "这个包用的是随 AOSP 源码公开的测试密钥，任何人都能签出同签名的包。",
                                "This package uses a test key that ships with the AOSP source, so anyone can sign a package with the same identity."
                            ),
                            iconPainter = androidx.compose.ui.res.painterResource(com.panl.installer.R.drawable.ic_info_circle),
                            tone = PuiNoticeTone.Danger
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
            if (settings.blurEnabled) PuiTopFade(height = blurH)
        }

        // 底部渐变：内容滚到底部时淡出，而不是被硬切
        val bg = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(48.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, bg)))
        )
    }
}

@Composable
private fun HeroCard(record: InstallRecord) {
    val scheme = MaterialTheme.colorScheme
    PuiCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIconView(record.icon(), 64.dp, corner = 18.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                record.label,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            RelationBadge(record.relationEnum)
            Spacer(Modifier.height(8.dp))
            Text(
                "${record.versionName} (${record.versionCode})",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailCard(record: InstallRecord) {
    PuiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            InfoLine(tr("安装时间", "Installed at"), Formatters.date(record.time))
            InfoLine(tr("包名", "Package"), record.packageName, mono = true)
            InfoLine(tr("版本", "Version"), "${record.versionName} (${record.versionCode})")
            InfoLine(tr("大小", "Size"), Formatters.size(record.sizeBytes))
            record.sourceLabel?.let { InfoLine(tr("来源应用", "Source app"), it) }
            // sdkLevel 自己就带「（API xx）」，别再加一遍
            InfoLine(tr("目标系统", "Target SDK"), Formatters.sdkLevel(record.targetSdk))
            InfoLine(tr("最低系统", "Min SDK"), Formatters.sdkLevel(record.minSdk))
            if (record.abis.isNotEmpty()) {
                InfoLine(tr("架构", "ABI"), record.abis.joinToString(" / "))
            }
            record.signerSubject?.let {
                InfoLine(tr("签名者", "Signer"), it, mono = true)
            }
            record.signerSha256?.let {
                InfoLine("SHA-256", it, mono = true)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = scheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
