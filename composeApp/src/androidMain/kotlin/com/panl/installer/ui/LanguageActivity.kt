package com.panl.installer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.panl.installer.core.Notifications
import com.panl.installer.core.Settings
import com.panl.installer.i18n.AppLanguage
import com.panl.installer.i18n.I18n
import com.panl.installer.i18n.tr
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.puiEdgeHaptic
import com.panl.pui.PuiGroup
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import com.panl.pui.PuiListRow
import com.panl.pui.PuiPageTitle
import com.panl.pui.puiProgressiveTopBlur
import com.panl.pui.rememberPuiTopBlurHeight

/** 语言选择：独立页面，选完立即生效并返回。 */
class LanguageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            InstallerTheme {
                LanguageScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun LanguageScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = Settings.get(context)
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    // 标题跟着列表一起滚，模糊带只盖住状态栏这一条
    val blurH = rememberPuiTopBlurHeight()

    val current = AppLanguage.fromId(settings.language)

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
                PuiPageTitle(tr("语言", "Language"))
                // 标题和下面第一张卡片之间留一口气，和其他页面统一 14dp
                Spacer(Modifier.height(14.dp))
            }

            item {
                PuiGroup(title = null) {
                    PuiListRow(
                        icon = PuiIcon.Phone,
                        title = tr("跟随系统", "System"),
                        subtitle = tr("由系统的语言设置决定", "Follows the system language"),
                        trailing = {
                            if (current == AppLanguage.System) {
                                PuiIconView(PuiIcon.Check, Modifier.size(20.dp), tint = scheme.primary)
                            }
                        },
                        showDivider = true,
                        showChevron = false,
                        onClick = { select(context, settings, AppLanguage.System) { onClose() } }
                    )
                    // 只列已经翻译好的语言，没翻完的不占位
                    AppLanguage.entries
                        .filter { it != AppLanguage.System && it.supported }
                        .forEachIndexed { index, lang ->
                        PuiListRow(
                            icon = null,
                            title = lang.nativeName,
                            // 列出来的都是翻译完整的，不用再标一遍
                            subtitle = "",
                            trailing = {
                                if (current == lang) {
                                    PuiIconView(PuiIcon.Check, Modifier.size(20.dp), tint = scheme.primary)
                                }
                            },
                            showDivider = index != AppLanguage.entries.count {
                                it != AppLanguage.System && it.supported
                            } - 1,
                            showChevron = false,
                            onClick = { select(context, settings, lang) { onClose() } }
                        )
                    }
                }
            }

        }

        // 标题在列表里，滚走就跟着走；顶部只剩状态栏那一条模糊
    }
}

private fun select(
    context: android.content.Context,
    settings: Settings,
    lang: AppLanguage,
    onDone: () -> Unit
) {
    settings.language = lang.id
    I18n.select(lang)
    // 通知渠道名也跟着换（同 id 再建一次会覆盖名字）
    runCatching { Notifications.createChannel(context) }
    onDone()
}
