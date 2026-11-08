package com.panl.installer.ui

import android.content.Intent
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import com.panl.installer.ui.components.puiCardBackdropBlur
import com.panl.installer.core.RootCheck
import com.panl.installer.core.RootState
import com.panl.installer.R
import com.panl.installer.core.Settings
import com.panl.installer.core.LauncherIcon
import com.panl.installer.core.SystemTweaks
import com.panl.installer.i18n.tr
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiDialog
import com.panl.pui.PuiSwitchRow
import com.panl.pui.PuiListRow
import com.panl.pui.PuiPageTitle
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/**
 * 进阶功能：目前只有一个入口——核心破解。
 *
 * 做成二级页而不是直接堆在设置里，是因为这些开关属于「折腾」范畴，
 * 和日常的安装设置分开更清楚。
 */
class AdvancedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                AdvancedScreen()
            }
        }
    }
}

@Composable
private fun AdvancedScreen() {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val settings = Settings.get(context)
    val density = LocalDensity.current.density
    // 标题跟着列表一起滚，模糊带只盖住状态栏这一条
    val blurH = rememberPuiTopBlurHeight()
    var showSystemWarning by remember { mutableStateOf(false) }
    var showRootRequired by remember { mutableStateOf(false) }
    var rootState by remember { mutableStateOf(RootState.Checking) }
    var rootTick by remember { mutableIntStateOf(0) }
    // 这两个开关改的是系统状态（全局设置 / 电池白名单），回到页面要重新读一遍
    var tweakTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tweakTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val verifyDisabled = remember(tweakTick) { SystemTweaks.isVerificationDisabled(context) }
    val launcherHidden = remember(tweakTick) { LauncherIcon.isHidden(context) }
    val ignoringBattery = remember(tweakTick) { SystemTweaks.isIgnoringBatteryOptimizations(context) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(rootTick) {
        rootState = RootState.Checking
        rootState = RootCheck.detect()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .puiEdgeHaptic()
                .puiCardBackdropBlur(showSystemWarning, (settings.sheetBlurPx / density).dp)
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
                PuiPageTitle(tr("进阶功能", "Advanced"))
                // 标题和下面第一张卡片之间留一口气，和其他页面统一 14dp
                Spacer(Modifier.height(14.dp))
            }

            item {
                PuiGroup(title = null) {
                    PuiListRow(
                        icon = PuiIcon.User,
                        title = tr("ROOT 权限", "ROOT access"),
                        subtitle = tr("点击重新检测", "Tap to check again"),
                        trailingText = when (rootState) {
                            RootState.Checking -> tr("检测中…", "Checking…")
                            RootState.Granted -> tr("已获取", "Granted")
                            RootState.Denied -> tr("未授权", "Not granted")
                            RootState.NoSu -> tr("未 ROOT", "No su")
                        },
                        showDivider = false,
                        showChevron = false,
                        onClick = { rootTick++ }
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = null) {
                    PuiListRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_security_safe),
                        title = tr("核心破解", "Core patch"),
                        subtitle = tr("需要 LSPosed", "Requires LSPosed"),
                        showDivider = false,
                        showChevron = true,
                        onClick = {
                            context.startActivity(Intent(context, CorePatchActivity::class.java))
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = null) {
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_shield_search),
                        title = tr("停用Play 保护机制", "Disable Play Protect"),
                        subtitle = tr("禁止安装扫描", "Block install scanning"),
                        checked = verifyDisabled,
                        onChange = { on ->
                            scope.launch {
                                val ok = SystemTweaks.setVerificationDisabled(context, on)
                                if (ok) {
                                    tweakTick++
                                } else {
                                    showRootRequired = true
                                }
                            }
                        },
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = null) {
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_flash_circle),
                        title = tr("忽略电池优化", "Ignore battery optimization"),
                        subtitle = tr(
                            "如果安装器经常被后台管理关闭，请打开。",
                            "Turn this on if the installer keeps getting killed in the background."
                        ),
                        checked = ignoringBattery,
                        onChange = { on ->
                            if (on) {
                                SystemTweaks.requestIgnoreBatteryOptimizations(context)
                            } else {
                                SystemTweaks.openBatteryOptimizationSettings(context)
                            }
                        },
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = null) {
                    PuiSwitchRow(
                        icon = PuiIcon.Grid,
                        title = tr("显示系统应用", "Show system apps"),
                        subtitle = tr(
                            "在应用列表里显示系统应用，可以卸载它们的更新",
                            "List system apps so their updates can be removed"
                        ),
                        checked = settings.showSystemApps,
                        onChange = { on ->
                            // 打开才弹风险确认；关闭直接生效
                            if (on) showSystemWarning = true else settings.showSystemApps = false
                        },
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = null) {
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_document_text),
                        title = tr("支持 .apk.1 安装包", "Support .apk.1 packages"),
                        subtitle = tr(
                            "QQ / 微信 下载的 APK 会被改名成 .apk.1，打开后照常安装",
                            "QQ and WeChat rename downloaded APKs to .apk.1 — install them as usual"
                        ),
                        checked = settings.apk1Support,
                        onChange = { settings.apk1Support = it },
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = null) {
                    PuiSwitchRow(
                        icon = null,
                        iconPainter = painterResource(R.drawable.ic_security),
                        title = tr("安全模式", "Safe mode"),
                        subtitle = tr(
                            "禁止伪造安装者与测试包；系统拦截安装时自动开启",
                            "No forged installer or test packages; turns on when the system blocks an install"
                        ),
                        checked = settings.safeMode,
                        onChange = { settings.safeMode = it },
                        showDivider = false
                    )
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                PuiGroup(title = null) {
                    PuiSwitchRow(
                        icon = PuiIcon.Moon,
                        title = tr("隐藏桌面图标", "Hide launcher icon"),
                        subtitle = tr(
                            "拨号输入 *#*#078#*#* 可随时打开安装器",
                            "Dial *#*#078#*#* anytime to open the installer"
                        ),
                        checked = launcherHidden,
                        onChange = { on ->
                            LauncherIcon.setHidden(context, on)
                            tweakTick++
                        },
                        showDivider = false
                    )
                }
            }

            item { Spacer(Modifier.height(18.dp)) }
        }

        // 标题在列表里，滚走就跟着走；顶部只剩状态栏那一条模糊
    }

    if (showRootRequired) {
        PuiDialog(
            scrim = !settings.noScrim,
            title = tr("需要 ROOT 权限", "ROOT required"),
            text = tr(
                "修改系统的安装校验开关需要 ROOT 授权，当前没有拿到权限，设置没有改动。",
                "Changing the system verification switch needs ROOT access. Nothing was changed."
            ),
            confirmText = tr("知道了", "Got it"),
            showCancel = false,
            onDismiss = { showRootRequired = false }
        )
    }

    if (showSystemWarning) {
        PuiDialog(
            scrim = !settings.noScrim,
            title = tr("风险提示", "Warning"),
            text = tr(
                "卸载系统自带应用的操作极其危险，且极容易导致无法开机。打开这个开关后，如果卸载任一一个系统APP导致无法开机或者其他使用异常，本安装器概不负责。",
                "Uninstalling built-in system apps is extremely risky and can easily leave the device unable to boot. If you enable this and uninstalling any system app causes a boot failure or other problems, this installer takes no responsibility."
            ),
            confirmText = tr("仍要打开", "Enable anyway"),
            dismissText = tr("取消", "Cancel"),
            danger = true,
            onDismiss = { showSystemWarning = false },
            onConfirm = {
                settings.showSystemApps = true
                showSystemWarning = false
            }
        )
    }
}
