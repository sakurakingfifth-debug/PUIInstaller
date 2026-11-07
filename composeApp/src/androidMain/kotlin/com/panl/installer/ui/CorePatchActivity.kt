package com.panl.installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.panl.installer.core.Settings
import com.panl.installer.corepatch.Config
import com.panl.installer.corepatch.CorePatchPrefs
import com.panl.installer.i18n.tr
import com.panl.installer.ui.components.PuiTopBar
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiListRow
import com.panl.pui.PuiPageTitle
import com.panl.pui.PuiNoticeCard
import com.panl.pui.PuiNoticeTone
import com.panl.pui.PuiSwitchRow
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/**
 * 核心破解：内置 Core Patch N 模块的开关页。
 *
 * 开关存在 LSPosed 的远程配置里，system_server 侧的模块直接读同一份，
 * 所以改完不用重启，下一次安装就按新配置走。
 */
class CorePatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                CorePatchScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun CorePatchScreen(onClose: () -> Unit) {
    // 打开页面时重新拉一次模块发布的真实开关状态
    androidx.compose.runtime.LaunchedEffect(Unit) { CorePatchPrefs.refreshState() }
    val scheme = MaterialTheme.colorScheme
    val settings = Settings.get(LocalContext.current)
    val density = LocalDensity.current.density
    val blurH = rememberPuiTopBlurHeight()

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = blurH + 4.dp, bottom = 32.dp)
        ) {
            item {
                PuiPageTitle(tr("核心破解", "Core patch"))
                // 标题和下面第一张卡片之间留一口气，和其他页面统一 14dp
                Spacer(Modifier.height(14.dp))
            }

            // 状态：模块有没有真正接上系统框架
            item {
                PuiGroup(title = null) {
                    PuiListRow(
                        icon = if (CorePatchPrefs.activeInSystem) null else PuiIcon.Warning,
                        iconPainter = if (CorePatchPrefs.activeInSystem) {
                            androidx.compose.ui.res.painterResource(com.panl.installer.R.drawable.ic_security_safe)
                        } else {
                            null
                        },
                        title = if (CorePatchPrefs.activeInSystem) {
                            tr("已接入系统框架", "Active in system framework")
                        } else {
                            tr("未激活", "Not active")
                        },
                        subtitle = if (CorePatchPrefs.activeInSystem) {
                            tr("开关改动立即生效，下次安装就按新配置走", "Changes apply to the next install, no reboot needed")
                        } else {
                            tr(
                                "在 LSPosed 里启用「安装器」，作用域勾选「系统框架」，重启后生效",
                                "Enable Installer in LSPosed, tick the System Framework scope, then reboot"
                            )
                        },
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = tr("开关", "Switches")) {
                    CorePatchSwitch(
                        key = Config.BYPASS_DOWNGRADE,
                        title = tr("允许降级安装", "Allow downgrades"),
                        subtitle = tr("系统层面放行低版本覆盖安装", "Install lower versions system-wide")
                    )
                    CorePatchSwitch(
                        key = Config.BYPASS_VERIFICATION,
                        title = tr("跳过签名校验", "Skip signature check"),
                        subtitle = tr("签名不一致也能覆盖安装", "Install over an app signed differently")
                    )
                    CorePatchSwitch(
                        key = Config.BYPASS_EXACT_SIGNATURE_MATCH,
                        title = tr("忽略签名精确匹配", "Ignore exact signature match"),
                        subtitle = tr("签名集合不同也当成同一个应用", "Treat a different signature set as the same app")
                    )
                    CorePatchSwitch(
                        key = Config.USE_PREVIOUS_SIGNATURES,
                        title = tr("沿用旧签名", "Keep previous signatures"),
                        subtitle = tr("覆盖安装时保留原有签名", "Keep the existing signature when replacing")
                    )
                    CorePatchSwitch(
                        key = Config.BYPASS_SHARED_USER,
                        title = tr("忽略 sharedUserId", "Ignore sharedUserId"),
                        subtitle = tr("共享用户不一致也允许安装", "Allow mismatched shared users")
                    )
                    CorePatchSwitch(
                        key = Config.BYPASS_DIGEST,
                        title = tr("跳过 digest 校验", "Skip digest check"),
                        subtitle = tr("不校验 APK 摘要", "Don't verify APK digests")
                    )
                    CorePatchSwitch(
                        key = Config.BYPASS_RESOURCE_ARSC_RESTRICTIONS,
                        title = tr("放行 resources.arsc", "Allow resources.arsc"),
                        subtitle = tr("允许加载不匹配的资源表", "Load mismatched resource tables")
                    )
                    CorePatchSwitch(
                        key = Config.ALLOW_HIDDEN_APIS_FOR_SYSTEM_APPS,
                        title = tr("系统应用隐藏 API", "Hidden APIs for system apps"),
                        subtitle = tr("解除系统应用的隐藏 API 限制", "Lift hidden-API limits for system apps")
                    )
                    CorePatchSwitch(
                        key = Config.DISABLE_VERIFICATION_AGENT,
                        title = tr("关闭校验代理", "Disable verification agent"),
                        subtitle = tr("安装时不跑校验代理", "Skip the install verification agent")
                    )
                    CorePatchSwitch(
                        key = Config.BYPASS_BLOCK,
                        title = tr("忽略安装拦截", "Bypass install blocks"),
                        subtitle = tr("绕过阻止安装的判定", "Ignore install-block decisions"),
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiNoticeCard(
                    title = tr("源自", "Credits"),
                    text = tr(
                        "核心破解功能来自开源项目 Core Patch N，感谢 Core Patch 的支持",
                        "Core patch comes from the open-source project Core Patch N. Thanks to the Core Patch team."
                    ),
                    icon = PuiIcon.Heart,
                    tone = PuiNoticeTone.Info
                )
            }
        }

        // 标题在列表里，滚走就跟着走；顶部只剩状态栏那一条模糊
    }
}

/** 核心破解开关：值存在 LSPosed 远程配置里，未连接时置灰。 */
@Composable
private fun CorePatchSwitch(
    key: String,
    title: String,
    subtitle: String,
    showDivider: Boolean = true
) {
    // 快照刷新后重新取一次值（模块那边的真实状态）
    val snapshot = CorePatchPrefs.state
    var checked by remember(snapshot) { mutableStateOf(CorePatchPrefs.get(key)) }
    PuiSwitchRow(
        icon = null,
        iconPainter = androidx.compose.ui.res.painterResource(com.panl.installer.R.drawable.ic_security_safe),
        title = title,
        subtitle = subtitle,
        checked = checked,
        enabled = CorePatchPrefs.connected,
        onChange = {
            checked = it
            CorePatchPrefs.set(key, it)
        },
        showDivider = showDivider
    )
}
