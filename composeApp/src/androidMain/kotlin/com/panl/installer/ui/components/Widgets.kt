package com.panl.installer.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.panl.installer.R
import com.panl.installer.core.ApkAbi
import com.panl.installer.core.ApkInfo
import com.panl.installer.core.InstalledApp
import com.panl.installer.core.SignerInfo
import com.panl.installer.core.VersionRelation
import com.panl.pui.PuiBadge
import com.panl.pui.PuiCard
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconButton
import com.panl.pui.PuiIconView
import com.panl.pui.PuiNoticeCard
import com.panl.pui.PuiNoticeTone
import com.panl.pui.PuiRowIcon
import com.panl.pui.puiRealtimeBlur
import com.panl.installer.i18n.tr

/** 应用图标：优先用 APK / 已安装应用的真实图标，取不到时退回 PUI 图标。 */
@Composable
fun AppIconView(
    bitmap: Bitmap?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = size * 0.26f,
    fallback: PuiIcon = PuiIcon.Download
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            PuiIconView(
                fallback,
                Modifier.size(size * 0.46f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 版本关系徽标。 */
@Composable
fun RelationBadge(relation: VersionRelation) {
    val scheme = MaterialTheme.colorScheme
    val (text, bg, fg) = when (relation) {
        VersionRelation.NEW -> Triple(tr("新安装", "New install"), scheme.primary, scheme.onPrimary)
        VersionRelation.UPDATE -> Triple(tr("更新", "Update"), scheme.primary, scheme.onPrimary)
        VersionRelation.DOWNGRADE -> Triple(tr("降级", "Downgrade"), scheme.error, scheme.onError)
        VersionRelation.REINSTALL -> Triple(tr("重新安装", "Reinstall"), scheme.secondary, scheme.onSecondary)
    }
    PuiBadge(text, container = bg, contentColor = fg)
}

/**
 * 版本对比卡：安装页的核心信息。
 *
 * 左边是**当前已安装**的版本，右边是**这个 APK** 的版本。
 * 未安装时左边显示「未安装」，避免用 0 或空串误导。
 */
@Composable
fun VersionCompareCard(info: ApkInfo, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val installed = info.installed
    val relation = info.relation

    PuiCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr("版本", "Version"),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                RelationBadge(relation)
            }
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("当前", "Current"),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        installed?.versionName ?: tr("未安装", "Not installed"),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (installed == null) scheme.onSurfaceVariant else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (installed != null) "(${installed.versionCode})" else "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    PuiIconView(
                        PuiIcon.ChevronRight,
                        Modifier.size(18.dp),
                        tint = scheme.primary
                    )
                }

                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        tr("新版本", "New"),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        info.versionName,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (relation == VersionRelation.DOWNGRADE) scheme.error else scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "(${info.versionCode})",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(scheme.outlineVariant.copy(alpha = 0.7f))
            )
            Spacer(Modifier.height(10.dp))

            Text(
                text = when (relation) {
                    VersionRelation.NEW -> tr("当前设备未安装该应用，将作为新应用安装", "Not installed on this device — it will be installed as a new app")
                    VersionRelation.UPDATE ->
                        tr("从 ${installed?.versionName} 更新到 ${info.versionName}，", "Updating from ${installed?.versionName} to ${info.versionName}, ") +
                            tr("版本号 ${installed?.versionCode} → ${info.versionCode}", "version ${installed?.versionCode} → ${info.versionCode}")
                    VersionRelation.DOWNGRADE ->
                        tr("新版本号低于当前版本（${installed?.versionCode} → ${info.versionCode}），", "The new version is lower than the installed one (${installed?.versionCode} → ${info.versionCode}), ") +
                            tr("需要系统允许降级安装", "the system must allow downgrades")
                    VersionRelation.REINSTALL -> tr("版本号相同，将覆盖重新安装", "Same version — it will overwrite the current install")
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 签名摘要卡：谁签的、指纹、是否 AOSP 公开密钥。 */
@Composable
fun SignerCard(
    signer: SignerInfo?,
    warnAosp: Boolean,
    modifier: Modifier = Modifier,
    title: String = tr("签名", "Signature")
) {
    val scheme = MaterialTheme.colorScheme
    PuiCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PuiRowIcon(PuiIcon.Lock)
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                when {
                    signer == null -> PuiBadge(
                        tr("未签名", "Unsigned"),
                        container = scheme.error,
                        contentColor = scheme.onError
                    )
                    signer.isAosp -> PuiBadge(
                        "AOSP ${signer.aospKey}",
                        container = scheme.error,
                        contentColor = scheme.onError
                    )
                    signer.isDebugKey -> PuiBadge(
                        tr("调试签名", "Debug key"),
                        container = scheme.tertiaryContainer,
                        contentColor = scheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            if (signer == null) {
                Text(
                    tr("无法读取签名信息，安装包可能已损坏", "Couldn't read the signature — the APK may be corrupted"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            } else {
                Text(
                    signer.subject,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                val fp = signer.sha256.chunked(2)
                Text(
                    "SHA-256  " + fp.take(6).joinToString(":").uppercase() +
                        " … " + fp.takeLast(3).joinToString(":").uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    ),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${signer.keyAlgorithm} ${signer.keySize} bit · ${signer.signatureAlgorithm}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
    }

    if (warnAosp && signer?.isAosp == true) {
        Spacer(Modifier.height(10.dp))
        PuiNoticeCard(
            title = tr("这是 AOSP 公开密钥", "This is an AOSP public key"),
            text = tr("该 APK 使用 AOSP 源码里公开的 ${signer.aospKey} 密钥签名，", "This APK is signed with the ${signer.aospKey} key published in the AOSP source, ") +
                tr("任何拿到这份密钥的人都能伪造同签名的更新。只在刷机、替换系统应用等场景下继续。", "so anyone holding that key can forge an update with the same signature. Continue only for flashing or replacing system apps."),
            iconPainter = androidx.compose.ui.res.painterResource(R.drawable.ic_info_circle),
            tone = PuiNoticeTone.Danger
        )
    }
    if (signer?.isDebugKey == true) {
        Spacer(Modifier.height(10.dp))
        PuiNoticeCard(
            title = tr("调试签名", "Debug key"),
            text = tr("这是 Android Studio 的调试密钥，不能用于正式分发。", "This is Android Studio's debug key and must not be used for distribution."),
            iconPainter = androidx.compose.ui.res.painterResource(R.drawable.ic_info_sparkle),
            tone = PuiNoticeTone.Warning
        )
    }
}

/** 页面顶栏：左侧关闭 / 返回，中间标题，右侧可放动作。 */
@Composable
fun PuiTopBar(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PuiIcon = PuiIcon.Close,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuiIconButton(icon = icon, onClick = onClose, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}

/** 底部按钮条：取消 + 主操作，自动让开导航栏。 */
@Composable
fun BottomActionBar(
    primaryText: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    danger: Boolean = false,
    navPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // 底部留白：这台机器手势条 inset 接近 0，只靠 navigationBarsPadding 会贴到屏幕边缘
            .padding(top = 14.dp, bottom = 28.dp + navPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (secondaryText != null && onSecondary != null) {
            com.panl.pui.PuiButton(
                text = secondaryText,
                onClick = onSecondary,
                modifier = Modifier.weight(1f),
                style = com.panl.pui.PuiButtonStyle.Ghost
            )
        }
        if (leading != null) leading()
        com.panl.pui.PuiButton(
            text = primaryText,
            onClick = onPrimary,
            // 和次要按钮等宽：1 : 1.4 会让右侧明显更宽，两个按钮看起来不在一条水平线上
            modifier = Modifier.weight(1f),
            enabled = primaryEnabled,
            style = if (danger) com.panl.pui.PuiButtonStyle.Danger else com.panl.pui.PuiButtonStyle.Filled
        )
    }
}


/**
 * 对话框（卡片）弹出时把背后的页面糊掉。
 *
 * 半径走设置里的「卡片展开模糊」；用 [puiRealtimeBlur] 而不是静态 RenderEffect，
 * 是为了让开关和滑块调整时能实时看到变化（进度从 0 动画到 1）。
 */
@Composable
fun Modifier.puiCardBackdropBlur(active: Boolean, maxRadius: Dp): Modifier {
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
        label = "card-backdrop-blur"
    )
    return this.puiRealtimeBlur(progress = { progress }, maxRadius = maxRadius)
}


/**
 * 不确定进度条：一段固定宽度的圆角胶囊在轨道里匀速滑过。
 *
 * 关键点：**用 offset 位移，不要用 padding 推**。
 * padding 会把子元素的可用宽度压小，表现是「越往右越细、最后缩成一个点再跳回左边」，
 * 之前安装页那个奇怪的动画就是这么来的。
 */
@Composable
fun PuiIndeterminateBar(
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    segmentFraction: Float = 0.4f,
    durationMillis: Int = 1600
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = durationMillis,
                // 对称三次贝塞尔：两端慢、中间快。配合 Reverse，滑块到两端会「顿一下」再折返，
                // 比匀速扫过去更像在呼吸（FastOutSlowIn 是偏 Material 的加减速，端点不够明显）
                easing = androidx.compose.animation.core.CubicBezierEasing(0.66f, 0f, 0.34f, 1f)
            ),
            // Reverse = 走到头再走回来，比 Restart（瞬间跳回起点）顺眼
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "indeterminate-progress"
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
    ) {
        val segWidth = maxWidth * segmentFraction
        val travel = maxWidth - segWidth
        Box(
            Modifier
                .offset(x = travel * progress)
                .width(segWidth)
                .fillMaxHeight()
                .background(scheme.primary, CircleShape)
        )
    }
}

/** 大号状态图标（成功 / 失败 / 进行中）。 */@Composable
fun StatusGlyph(
    icon: PuiIcon,
    tint: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
    size: Dp = 84.dp,
    /** 自定义图标（矢量 / 图片），传了就不画 [icon]。 */
    painter: androidx.compose.ui.graphics.painter.Painter? = null
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        if (painter != null) {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(size * 0.44f),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
            )
        } else {
            PuiIconView(icon, Modifier.size(size * 0.44f), tint = tint)
        }
    }
}

/** 可点击的一行小卡片（首页快捷入口用）。 */
@Composable
fun ActionTile(
    icon: PuiIcon? = null,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    /** 官方矢量图；传了就不看 [icon]。 */
    iconRes: Int? = null,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLowest)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(14.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(scheme.onPrimaryContainer)
                )
            } else if (icon != null) {
                PuiIconView(icon, Modifier.size(20.dp), tint = scheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}


// ---------------- 安装信息条 ----------------

enum class InfoChipTone { Neutral, Warning, Danger }

/** 小胶囊：和 PUI 的 Chip 同一套圆角，但支持危险 / 警告配色。 */
@Composable
fun InfoChip(text: String, tone: InfoChipTone = InfoChipTone.Neutral) {
    val scheme = MaterialTheme.colorScheme
    val bg = when (tone) {
        InfoChipTone.Neutral -> scheme.surfaceContainerHighest
        InfoChipTone.Warning -> scheme.tertiaryContainer
        InfoChipTone.Danger -> scheme.errorContainer
    }
    val fg = when (tone) {
        InfoChipTone.Neutral -> scheme.onSurfaceVariant
        InfoChipTone.Warning -> scheme.onTertiaryContainer
        InfoChipTone.Danger -> scheme.onErrorContainer
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * 安装信息条：签名 / AOSP / 来源 / OSDK。
 * 逐条判断，没有的就不加 —— 全空时整块不占位。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallInfoChips(
    info: ApkInfo,
    showOsdk: Boolean,
    modifier: Modifier = Modifier,
    /** 显示 目标 API / 最低 API。全屏页的详情卡已有这两行，就不重复加胶囊。 */
    showApi: Boolean = false,
    /** 胶囊多于 3 个时拆成两行均分，避免一行挤满、贴到两边。 */
    twoRows: Boolean = false
) {
    val chips = buildList {
        val signer = info.primarySigner
        when {
            info.signers.isEmpty() -> add(tr("未签名", "Unsigned") to InfoChipTone.Danger)
            signer == null -> Unit
            signer.isDebugKey -> add(tr("调试签名", "Debug key") to InfoChipTone.Warning)
            else -> {
                val cn = signer.subject.split(",")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("CN=") }
                    ?.removePrefix("CN=")
                if (!cn.isNullOrBlank()) add(cn to InfoChipTone.Neutral)
            }
        }
        if (info.signers.any { it.aospKey != null }) {
            add(tr("AOSP 密钥", "AOSP key") to InfoChipTone.Danger)
        }
        info.sourceLabel?.let { add(tr("来自 $it", "From $it") to InfoChipTone.Neutral) }
        // 只在国内库全按 16 KB 对齐时显示；纯 Java 应用（没有 .so）不显示
        if (info.supports16K == true) {
            add(tr("16 KB 页面", "16 KB pages") to InfoChipTone.Neutral)
        }
        // 架构：只在国内库存在时显示，纯 Java 应用不加这条
        val abi = ApkAbi.describe(info.abis)
        if (abi.isNotEmpty()) {
            add(abi to InfoChipTone.Neutral)
        }
        if (showApi) {
            // 合成一条 API 区间，省一个胶囊位
            val min = info.minSdk
            val target = info.targetSdk
            if (min > 0 && target > 0) {
                val blocked = target < 23 && android.os.Build.VERSION.SDK_INT >= 34
                val tooHigh = min > android.os.Build.VERSION.SDK_INT
                add(
                    tr("API $min–$target", "API $min–$target") to
                        if (blocked || tooHigh) InfoChipTone.Danger else InfoChipTone.Neutral
                )
            } else if (target > 0) {
                val blocked = target < 23 && android.os.Build.VERSION.SDK_INT >= 34
                add(tr("目标 API $target", "Target API $target") to if (blocked) InfoChipTone.Danger else InfoChipTone.Neutral)
            } else if (min > 0) {
                val tooHigh = min > android.os.Build.VERSION.SDK_INT
                add(tr("最低 API $min", "Min API $min") to if (tooHigh) InfoChipTone.Danger else InfoChipTone.Neutral)
            }
        }
        if (showOsdk) {
            val min = info.minOsdk
            val max = info.maxOsdk
            val text = when {
                min != null && max != null -> "OSDK $min – $max"
                min != null -> "OSDK ≥ $min"
                max != null -> "OSDK ≤ $max"
                else -> null
            }
            if (text != null) add(text to InfoChipTone.Neutral)
        }
    }
    if (chips.isEmpty()) return

    val perRow = if (twoRows && chips.size >= 4) (chips.size + 1) / 2 else Int.MAX_VALUE
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = perRow
    ) {
        chips.forEach { (text, tone) -> InfoChip(text, tone) }
    }
}


/** 底部渐隐：内容滚到底栏上方时溶进背景，而不是被硬切一刀。 */
@Composable
fun BoxScope.PuiBottomFade(
    height: Dp,
    color: Color = MaterialTheme.colorScheme.background
) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0f), color)
                )
            )
    )
}

/**
 * 带顶栏页面的顶部渐隐：上段完全盖住（顶栏区域），只在末尾一小段淡出。
 *
 * 普通的线性渐变（0.69 → 0）在顶栏位置还剩三四成透明度，滚动时下面的行会从
 * 标题后面透出来；这里把不透明段拉长到 [solidFraction]，标题后面就是干净的。
 */
@Composable
fun BoxScope.PuiTopBarFade(
    height: Dp,
    color: Color,
    solidFraction: Float = 0.86f
) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to color,
                    solidFraction to color,
                    1f to color.copy(alpha = 0f)
                )
            )
    )
}

