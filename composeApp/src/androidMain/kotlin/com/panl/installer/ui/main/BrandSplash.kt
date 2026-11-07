package com.panl.installer.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.panl.installer.R
import com.panl.installer.i18n.tr

/**
 * 品牌启动页。
 *
 * 系统启动图（Theme.PuiInstaller.Splash）撤走后由它接手：底色和系统启动图完全一致，
 * 图标从圆形底里「弹」出来、应用名跟着淡入，然后交叉淡入主界面。
 */
@Composable
fun BrandSplash() {
    val scheme = MaterialTheme.colorScheme
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val logoScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.86f,
        animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        label = "splash-logo-scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "splash-logo-alpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 360, delayMillis = 120),
        label = "splash-text-alpha"
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center
    ) {
        // 跟屏宽走：手机上大约 40% 宽，平板 / 折叠屏有上限，不会大得离谱
        val logoSize = (maxWidth * 0.40f).coerceIn(140.dp, 220.dp)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 和系统启动图用同一张 drawable，衔接时图标不会跳变
            Image(
                painter = painterResource(R.drawable.ic_splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(logoSize)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    }
            )

            Spacer(Modifier.height(26.dp))

            Text(
                tr("安装器", "Installer"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
            )
        }
    }
}
