package com.panl.installer.ui.install

import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.panl.installer.core.Settings

/**
 * 让弹窗窗口自己开系统模糊，**半径跟随设置里的「浮窗模糊」**。
 *
 * 为什么弹窗要单独开：Dialog 是独立窗口，页面上的 `puiCardBackdropBlur` 只能糊它自己那块内容，
 * 糊不到弹窗背后（批量安装时最容易看出来）。
 *
 * 之前这里写死 36dp，和设置项脱钩 —— 用户把浮窗模糊拉小/关掉，弹窗还是一样糊。
 * 现在直接读 `Settings.sheetBlurPx`（单位就是 px，和 InstallActivity.applyBlurBehind 一致）。
 */
@Composable
internal fun DialogBlurBehind() {
    val view = LocalView.current
    val settings = Settings.get(LocalContext.current)
    SideEffect {
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@runCatching
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@runCatching
            if (settings.blurEnabled && settings.sheetBlurPx > 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = window.attributes
                lp.setBlurBehindRadius(settings.sheetBlurPx)
                window.attributes = lp
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
}
