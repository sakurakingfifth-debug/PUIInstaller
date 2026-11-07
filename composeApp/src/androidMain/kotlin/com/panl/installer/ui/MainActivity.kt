package com.panl.installer.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.panl.installer.ui.main.BrandSplash
import com.panl.installer.ui.main.MainShell
import com.panl.installer.ui.theme.InstallerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前：它负责把启动主题换成 postSplashScreenTheme
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 首帧真正画出来之前不撤启动图，否则中间会闪一下空背景。
        // 加个 2s 上限兜底，避免万一没画出来就永远停在启动图。
        val startedAt = SystemClock.uptimeMillis()
        var firstFrameDrawn = false
        splash.setKeepOnScreenCondition {
            !firstFrameDrawn && SystemClock.uptimeMillis() - startedAt < 2_000
        }
        window.decorView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    firstFrameDrawn = true
                    return true
                }
            }
        )

        // 退出动画：图标放大淡出、整层跟着淡出，正好接上同底色的品牌页
        splash.setOnExitAnimationListener { splashView ->
            splashView.iconView?.animate()
                ?.scaleX(1.12f)
                ?.scaleY(1.12f)
                ?.alpha(0f)
                ?.setDuration(220L)
                ?.setInterpolator(DecelerateInterpolator())
                ?.start()
            splashView.view.animate()
                .alpha(0f)
                .setStartDelay(40L)
                .setDuration(200L)
                .withEndAction { splashView.remove() }
                .start()
        }

        // 只有真正的冷启动才放品牌页：旋转屏幕 / 切换深浅色重建 Activity 时直接进主界面
        val coldStart = savedInstanceState == null

        setContent {
            InstallerTheme {
                // 品牌页至少停留 480ms（含图标入场动画），再 260ms 交叉淡入主界面
                var ready by remember { mutableStateOf(!coldStart) }
                LaunchedEffect(Unit) {
                    if (coldStart) {
                        delay(480)
                        ready = true
                    }
                }
                Crossfade(
                    targetState = ready,
                    animationSpec = tween(durationMillis = 260),
                    label = "splash-to-content"
                ) { showContent ->
                    if (showContent) MainShell() else BrandSplash()
                }
            }
        }
    }
}
