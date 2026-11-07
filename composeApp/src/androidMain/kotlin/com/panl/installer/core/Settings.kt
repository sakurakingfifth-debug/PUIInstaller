package com.panl.installer.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 全局设置。字段本身是 Compose 状态，读写即落盘，界面直接 `settings.xxx` 就能响应。
 *
 * 和 PUI Demo 里的 PuiPrefs 同一套做法：只在需要「改完立刻生效 + 重启还在」的地方用。
 */
class Settings private constructor(private val sp: SharedPreferences) {

    /** 安装成功后删除目标 APK 文件。 */
    var deleteApkAfterInstall: Boolean by bool("delete_apk_after_install", false)

    /** 删除前弹一次确认（防止误删用户唯一的安装包）。 */
    var confirmBeforeDelete: Boolean by bool("confirm_before_delete", true)

    /** 检测到 AOSP 公开测试密钥时在安装页给出警告。 */
    var warnAospSignature: Boolean by bool("warn_aosp_signature", true)

    /** 安装 / 卸载结束后发通知。 */
    var showNotification: Boolean by bool("show_notification", true)

    /** 安装前校验 APK 完整性（zip 结构 + 签名块可解析）。 */
    var verifyBeforeInstall: Boolean by bool("verify_before_install", true)

    /** 界面语言：system（跟随系统）/ zh / en。 */
    var language: String by string("language", "system")

    /** 外观：System / Light / Dark。 */
    var themeMode: String by string("theme_mode", "System")

    /** 安全模式：禁止伪造安装者 / 测试包等操作；检测到系统拦截安装时自动开启。 */
    var safeMode: Boolean by bool("safe_mode", false)

    /** 应用列表是否显示系统应用。默认关：系统应用能卸载，误操作容易开不了机。 */
    var showSystemApps: Boolean by bool("show_system_apps", false)

    /** 主题色来源：Default（PUI 洋红）/ Wallpaper（跟随壁纸）/ Custom（自定义 ARGB）。 */
    var themeAccentMode: String by string("theme_accent_mode", "Default")

    /** 自定义主题色，ARGB 整数。默认就是 PUI 的洋红 #BE3455。 */
    var themeAccentArgb: Int by int("theme_accent_argb", 0xFFBE3455.toInt())

    // ---------------- 模糊 / 毛玻璃（对应 PUI 令牌页那组开关） ----------------

    /** 顶部渐进模糊 + 导航栏毛玻璃总开关。关掉整屏不再挂 RenderEffect。 */
    var blurEnabled: Boolean by bool("blur_enabled", true)

    /** 顶部渐进模糊深度，单位 px（2~100）。 */
    var topBlurPx: Int by int("top_blur_px", 72)

    /** 卡片（对话框）展开时背景的模糊半径，单位 px（0~120）。 */
    var cardBlurPx: Int by int("card_blur_px", 64)

    /** 关闭遮罩：浮窗 / 弹窗背后不再压那层灰黑。 */
    var noScrim: Boolean by bool("no_scrim", false)

    /** 导航栏毛玻璃半径，单位 px（2~64）。 */
    var navBlurPx: Int by int("nav_blur_px", 48)

    /** 导航栏玻璃底色不透明度，0~100（%）。 */
    var navGlassPercent: Int by int("nav_glass_percent", 100)

    // ---------------- 安装界面形态 ----------------

    /** 安装页形态：Sheet（底部浮窗）/ Full（全屏）。 */
    var installStyle: String by string("install_style", "Sheet")

    /** 实时通知模式：解析完直接装，不再弹「装不装」。 */
    var autoInstallLive: Boolean by bool("auto_install_live", false)

    /** 自动安装结束后，用横幅把完成通知弹出来。 */
    var popupOnDone: Boolean by bool("popup_on_done", false)

    /** 浮窗位置：Bottom（贴底）/ Center（居中）。 */
    var sheetPosition: String by string("sheet_position", "Bottom")

    /** 安装成功后多少秒自动清掉通知；0 = 从不。 */
    var autoClearSeconds: Int by int("auto_clear_seconds", 15)

    /**
     * 走不走「实时通知」（Android 16 Live Updates / ColorOS 流体云胶囊）。
     *
     * 只跟**安装样式**走：只有选了「实时通知」的安装样式才会提升成胶囊；
     * 浮窗 / 全屏模式下即使安装结束也只发普通通知，不弹胶囊。
     */
    val notificationsLive: Boolean get() = installStyle == "Live"

    /** 浮窗背后那层系统模糊的半径（px，2–100）。 */
    var sheetBlurPx: Int by int("sheet_blur_px", 60)

    /** 是否显示欧加系应用的 OSDK 目标版本（AndroidManifest 的 min/maxOsdkVersion）。 */
    var showOsdk: Boolean by bool("show_osdk", true)

    private fun bool(key: String, default: Boolean) = Pref(sp.getBoolean(key, default)) {
        sp.edit().putBoolean(key, it).apply()
    }

    private fun int(key: String, default: Int) = Pref(sp.getInt(key, default)) {
        sp.edit().putInt(key, it).apply()
    }

    private fun string(key: String, default: String) = Pref(sp.getString(key, default) ?: default) {
        sp.edit().putString(key, it).apply()
    }

    private class Pref<T>(initial: T, private val onWrite: (T) -> Unit) : ReadWriteProperty<Any?, T> {
        private var state by mutableStateOf(initial)
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state = value
            onWrite(value)
        }
    }

    companion object {
        @Volatile
        private var instance: Settings? = null

        fun get(context: Context): Settings = instance ?: synchronized(this) {
            instance ?: Settings(
                context.applicationContext.getSharedPreferences("installer_settings", Context.MODE_PRIVATE)
            ).also { instance = it }
        }
    }
}
