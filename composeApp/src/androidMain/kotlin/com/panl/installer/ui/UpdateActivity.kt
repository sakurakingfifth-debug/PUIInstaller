package com.panl.installer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.panl.installer.R
import com.panl.installer.core.UpdateChecker
import com.panl.installer.ui.main.appVersion
import com.panl.installer.i18n.tr
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 系统更新页（OTA）。
 *
 * 顶部是圆角大图（这里用渐变代替实拍图，随主题走），中间是版本信息，
 * 下面是更新日志：有新版显示新版的日志，已是最新就显示 GitHub 上当前版本的日志，
 * 完全没网就整块隐藏 —— 空着一个「更新日志」标题比不显示更难看。
 */
class UpdateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                UpdateScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, UpdateActivity::class.java))
        }
    }
}

private sealed interface OtaState {
    data object Loading : OtaState
    data class Ready(val release: UpdateChecker.Release, val hasUpdate: Boolean) : OtaState
    /** 检查失败（没网 / 被拦 / 限流）：不显示日志 */
    data class Failed(val reason: String) : OtaState
}

@Composable
private fun UpdateScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val current = remember { appVersion(context) }

    var state by remember { mutableStateOf<OtaState>(OtaState.Loading) }
    var checking by remember { mutableStateOf(false) }

    suspend fun runCheck() {
        if (checking) return
        checking = true
        val r = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
        state = r.fold(
            onSuccess = { rel -> OtaState.Ready(rel, UpdateChecker.isNewer(rel.version, current)) },
            onFailure = { e -> OtaState.Failed(e.message ?: tr("网络不可用", "Network unavailable")) }
        )
        checking = false
    }

    LaunchedEffect(Unit) { runCheck() }

    val ready = state as? OtaState.Ready

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
    ) {
        // ── 顶栏 ────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                PuiIconView(
                    PuiIcon.ChevronRight,
                    Modifier
                        .size(20.dp)
                        .rotate(180f),
                    tint = scheme.onSurface
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                tr("软件更新", "Software update"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface
            )
        }

        // 滚动区 + 底部渐隐：内容不会在按钮上方被「一刀切」，
        // 而是逐渐淡出，暗示还能继续往下滑
        Box(Modifier.weight(1f)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
        ) {
            // ── 顶部大图：直接用壁纸，标题压在左下角 ────────────────────
            // 2:1 长矩形：宽高比固定，图用 Crop 裁（源图是正方形，上下会被切掉，
            // 这图本身就是纯渐变，切哪一块都看不出）
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ota_banner),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    tr("PUI 安装器", "PUI Installer"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 22.dp, bottom = 20.dp, end = 22.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── 版本信息卡 ────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surfaceContainerLow)
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            ) {
                OtaRow(tr("当前版本", "Current"), current)
                val latest = ready?.release?.version
                if (latest != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(scheme.outlineVariant.copy(alpha = 0.35f))
                    )
                    OtaRow(tr("最新版本", "Latest"), latest)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(scheme.outlineVariant.copy(alpha = 0.35f))
                )
                OtaRow(
                    tr("状态", "Status"),
                    when (val s = state) {
                        is OtaState.Loading -> tr("检查中…", "Checking…")
                        is OtaState.Ready -> if (s.hasUpdate) tr("有可用更新", "Update available") else tr("已是最新", "Up to date")
                        is OtaState.Failed -> tr("未联网", "Offline")
                    }
                )
            }

            // ── 更新日志（没网就整块不显示） ───────────────────────────
            val notes = ready?.release?.notes?.trim().orEmpty()
            if (state !is OtaState.Failed) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(scheme.surfaceContainerLow)
                        .padding(18.dp)
                ) {
                    Text(
                        if (ready?.hasUpdate == true) tr("更新日志", "What's new")
                        else tr("当前版本日志", "Release notes"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            state is OtaState.Loading -> tr("正在获取…", "Loading…")
                            notes.isNotBlank() -> notes
                            else -> tr("该版本没有填写更新说明", "No release notes for this version")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            } else if (state is OtaState.Failed) {
                Spacer(Modifier.height(14.dp))
                Text(
                    (state as OtaState.Failed).reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
        }

            // 渐隐层：只画不挡点击（没有 clickable），盖住滚动区最后 84dp
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, scheme.background)
                        )
                    )
            )
        }

        // ── 底部两个圆形按钮 ────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp, top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleAction(
                label = if (checking) tr("检查中…", "Checking…") else tr("检查更新", "Check"),
                painter = R.drawable.ic_document_download,
                filled = true,
                onClick = { if (!checking) scope.launch { runCheck() } }
            )
            Spacer(Modifier.width(48.dp))
            CircleAction(
                label = "GitHub",
                painter = R.drawable.ic_code_1,
                filled = false,
                onClick = {
                    runCatching { uriHandler.openUri("https://github.com/sakurakingfifth-debug/PUIInstaller") }
                }
            )
        }
    }
}

@Composable
private fun OtaRow(key: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CircleAction(
    label: String,
    painter: Int,
    filled: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (filled) scheme.primary else scheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(painter),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    if (filled) scheme.onPrimary else scheme.onSurfaceVariant
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant
        )
    }
}
