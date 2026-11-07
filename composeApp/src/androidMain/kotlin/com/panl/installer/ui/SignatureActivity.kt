package com.panl.installer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.panl.installer.R
import com.panl.installer.core.ApkInfo
import com.panl.installer.core.ApkInspector
import com.panl.installer.core.ApkSignature
import com.panl.installer.core.ApkStaging
import com.panl.installer.core.Formatters
import com.panl.installer.core.Settings
import com.panl.installer.core.SignerInfo
import com.panl.installer.ui.components.AppIconView
import com.panl.installer.ui.components.PuiTopBar
import com.panl.installer.ui.components.StatusGlyph
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiButton
import com.panl.pui.PuiCard
import com.panl.pui.PuiIcon
import com.panl.pui.PuiNoticeCard
import com.panl.pui.PuiNoticeTone
import com.panl.pui.PuiTopFade
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.panl.installer.i18n.tr

/**
 * 签名检测页：选一个 APK，列出全部签名者、指纹与是否为 AOSP 公开密钥。
 *
 * 这是「支持检测 AOSP 签名 key」的独立入口，安装页里的签名卡只是它的摘要版。
 */
class SignatureActivity : ComponentActivity() {

    private var state by mutableStateOf<SigState>(SigState.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                SignatureScreen(
                    state = state,
                    onClose = { finish() },
                    onPick = { uri -> analyze(uri) }
                )
            }
        }
        intent?.data?.let { analyze(it) }
    }

    private fun analyze(uri: Uri) {
        lifecycleScope.launch {
            state = SigState.Loading
            val result = withContext(Dispatchers.IO) {
                val file = ApkStaging.stage(this@SignatureActivity, uri, "signature")
                    ?: return@withContext null
                ApkInspector.inspect(this@SignatureActivity, file)
            }
            state = if (result == null) {
                SigState.Error(tr("无法解析这个文件，可能不是有效的 APK", "Couldn't parse this file — it may not be a valid APK"))
            } else {
                SigState.Ready(result)
            }
        }
    }
}

sealed interface SigState {
    data object Idle : SigState
    data object Loading : SigState
    data class Ready(val info: ApkInfo) : SigState
    data class Error(val message: String) : SigState
}

@Composable
private fun SignatureScreen(
    state: SigState,
    onClose: () -> Unit,
    onPick: (Uri) -> Unit
) {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    // 顶部模糊高度 = 状态栏 + 64dp，顶栏正好落在这一条里
    val blurH = rememberPuiTopBlurHeight()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPick(uri)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (state) {
            is SigState.Idle -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StatusGlyph(
                    icon = PuiIcon.Lock,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    size = 96.dp
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    tr("选择一个 APK", "Choose an APK"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("会列出签名者、证书指纹，并识别 AOSP 公开密钥", "Lists signers and certificate fingerprints, and flags AOSP public keys"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(26.dp))
                PuiButton(
                    text = tr("选择安装包", "Choose APK"),
                    onClick = { picker.launch(arrayOf("application/vnd.android.package-archive")) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is SigState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tr("正在解析签名…", "Reading signatures…"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is SigState.Error -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                PuiButton(
                    text = tr("重新选择", "Choose another"),
                    onClick = { picker.launch(arrayOf("application/vnd.android.package-archive")) }
                )
            }

            is SigState.Ready -> LazyColumn(
                modifier = Modifier
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
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppIconView(state.info.icon, 72.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.info.label,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.info.packageName} · ${state.info.versionName} (${state.info.versionCode})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.info.file.name} · ${Formatters.size(state.info.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val aosp = state.info.signers.filter { it.isAosp }
                if (aosp.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        val aospNames = aosp.joinToString { it.aospKey ?: "" }
                        PuiNoticeCard(
                            title = tr("检测到 AOSP 公开密钥", "AOSP public key detected"),
                            text = tr(
                                "签名者为 $aospNames，这四把密钥随 AOSP 源码公开，任何人都能签出同签名的包。",
                                "Signed by $aospNames. These four keys ship with the AOSP source, " +
                                    "so anyone can sign a package with the same identity."
                            ),
                            iconPainter = androidx.compose.ui.res.painterResource(R.drawable.ic_info_circle),
                            tone = PuiNoticeTone.Danger
                        )
                    }
                }

                if (state.info.signers.isEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        PuiNoticeCard(
                            title = tr("没有签名", "No signature"),
                            text = tr("这个 APK 没有可解析的签名块，无法正常安装。", "This APK has no readable signature block and can't be installed normally."),
                            iconPainter = androidx.compose.ui.res.painterResource(R.drawable.ic_info_circle),
                            tone = PuiNoticeTone.Danger
                        )
                    }
                }

                items(state.info.signers.size) { index ->
                    Spacer(Modifier.height(12.dp))
                    SignerDetailCard(state.info.signers[index], index, state.info.signers.size)
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    PuiButton(
                        text = tr("检测另一个安装包", "Check another APK"),
                        onClick = { picker.launch(arrayOf("application/vnd.android.package-archive")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (settings.blurEnabled) PuiTopFade(height = blurH)

        // 顶栏叠在最上层：列表从它下面滚过去，正好被顶部模糊糊掉
        Column(Modifier.statusBarsPadding().align(Alignment.TopCenter)) {
            PuiTopBar(title = tr("签名检测", "Signature check"), onClose = onClose)
        }
    }
}

@Composable
private fun SignerDetailCard(signer: SignerInfo, index: Int, total: Int) {
    val scheme = MaterialTheme.colorScheme
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA) }

    PuiCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (total > 1) tr("签名者 ${index + 1}", "Signer ${index + 1}") else tr("签名者", "Signer"),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                signer.verdict,
                style = MaterialTheme.typography.titleMedium,
                color = if (signer.isAosp) scheme.error else scheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            MonoLine(tr("主题", "Theme"), signer.subject)
            MonoLine(tr("颁发者", "Issuer"), signer.issuer)
            MonoLine("SHA-256", signer.sha256.chunked(2).joinToString(":").uppercase())
            MonoLine("SHA-1", signer.sha1.chunked(2).joinToString(":").uppercase())
            MonoLine(tr("算法", "Algorithm"), "${signer.keyAlgorithm} ${signer.keySize} bit / ${signer.signatureAlgorithm}")
            MonoLine(
                tr("有效期", "Validity"),
                "${dateFormat.format(Date(signer.validFrom))} → ${dateFormat.format(Date(signer.validTo))}"
            )
            if (signer.isAosp) {
                Spacer(Modifier.height(10.dp))
                Text(
                    ApkSignature.AOSP_KEY_NOTES[signer.aospKey] ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error
                )
            }
        }
    }
}

@Composable
private fun MonoLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
