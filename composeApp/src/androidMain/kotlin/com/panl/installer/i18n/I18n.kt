package com.panl.installer.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.panl.pui.PuiLocale
import java.util.Locale

/**
 * 界面语言。
 *
 * [System] 表示跟随系统，只在中文 / 非中文之间二选一；其余是强制指定。
 */
enum class AppLanguage(
    val id: String,
    val nativeName: String,
    /** 是否已经完整翻译。false 的只在代码里留着，界面上不列出来。 */
    val supported: Boolean = false
) {
    System("system", "", true),
    Chinese("zh", "简体中文", true),
    TraditionalTaiwan("zh-TW", "繁體中文（台灣）", true),
    TraditionalHongKong("zh-HK", "繁體中文（香港）", true),
    Cantonese("yue", "粵語（廣東話）", true),
    CantoneseSimplified("yue-CN", "粤语（中国）", true),
    English("en", "English", true),
    // 以下还没翻译完，先不列在语言页里
    Japanese("ja", "日本語", true),
    Korean("ko", "한국어", true),
    KoreanNorth("ko-KP", "조선어", true),
    German("de", "Deutsch", true),
    French("fr", "Français", true),
    Italian("it", "Italiano", true),
    Spanish("es", "Español", true),
    Portuguese("pt", "Português", true),
    Russian("ru", "Русский", true),
    Ukrainian("uk", "Українська", true),
    Polish("pl", "Polski", true),
    Latin("la", "Latina", true),
    Dutch("nl", "Nederlands", true),
    Danish("da", "Dansk", true),
    Norwegian("nb", "Norsk", true),
    Arabic("ar", "العربية", true),
    Hindi("hi", "हिन्दी", true),
    Turkish("tr", "Türkçe", true),
    Vietnamese("vi", "Tiếng Việt", true);

    companion object {
        fun fromId(id: String?): AppLanguage = entries.firstOrNull { it.id == id } ?: System
    }
}

/**
 * 全局语言状态。
 *
 * [choice] 是 Compose 状态：所有文案都通过 [tr] 读取它，所以切换语言时整个界面
 * 会自动重组，不需要重建 Activity。
 */
object I18n {

    var choice: AppLanguage by mutableStateOf(AppLanguage.System)
        private set

    private var systemLanguage: String = Locale.getDefault().language

    /** 系统 / 应用级 locale 的完整标签，例如 `yue-Hant-HK`、`zh-Hant-TW`。 */
    private var systemTag: String = Locale.getDefault().toLanguageTag()

    /**
     * 真正生效的语言。
     *
     * 「跟随系统」时按系统 locale 判断：粤语优先，其次按繁简和地区落到
     * 台湾 / 香港 / 简体；认不出来的语言一律用英文。
     */
    val effective: AppLanguage
        get() = when (choice) {
            AppLanguage.System -> when {
                // 粤语按字形分：Hans 出简体，Hant 出繁体
                systemTag.startsWith("yue", true) -> if (systemTag.contains("Hans", true)) {
                    AppLanguage.CantoneseSimplified
                } else {
                    AppLanguage.Cantonese
                }

                systemTag.startsWith("zh", true) -> when {
                    // 显式写了 Hans 就是简体，哪怕地区是 HK（本机就是 zh-Hans-HK）
                    systemTag.contains("Hans", true) -> AppLanguage.Chinese
                    systemTag.contains("Hant", true) ||
                        systemTag.contains("TW", true) -> AppLanguage.TraditionalTaiwan
                    systemTag.contains("HK", true) ||
                        systemTag.contains("MO", true) -> AppLanguage.TraditionalHongKong
                    else -> AppLanguage.Chinese
                }

                systemTag.startsWith("ja", true) -> AppLanguage.Japanese
                systemTag.startsWith("ru", true) -> AppLanguage.Russian
                systemTag.startsWith("uk", true) -> AppLanguage.Ukrainian
                systemTag.startsWith("pl", true) -> AppLanguage.Polish
                systemTag.startsWith("la", true) -> AppLanguage.Latin
                systemTag.startsWith("nl", true) -> AppLanguage.Dutch
                systemTag.startsWith("da", true) -> AppLanguage.Danish
                systemTag.startsWith("nb", true) || systemTag.startsWith("no", true) || systemTag.startsWith("nn", true) -> AppLanguage.Norwegian
                systemTag.startsWith("pt", true) -> AppLanguage.Portuguese
                systemTag.startsWith("ko-kp", true) -> AppLanguage.KoreanNorth
                systemTag.startsWith("ko", true) -> AppLanguage.Korean
                systemTag.startsWith("de", true) -> AppLanguage.German
                systemTag.startsWith("fr", true) -> AppLanguage.French
                systemTag.startsWith("it", true) -> AppLanguage.Italian
                systemTag.startsWith("es", true) -> AppLanguage.Spanish
                systemTag.startsWith("ar", true) -> AppLanguage.Arabic
                systemTag.startsWith("hi", true) -> AppLanguage.Hindi
                systemTag.startsWith("tr", true) -> AppLanguage.Turkish
                systemTag.startsWith("vi", true) -> AppLanguage.Vietnamese
                systemTag.startsWith("en", true) -> AppLanguage.English
                else -> AppLanguage.English
            }

            else -> choice
        }

    /** 当前是否显示英文。 */
    val isEnglish: Boolean
        get() = effective == AppLanguage.English

    /** 启动时调用：把设置里的选择灌进来。 */
    fun apply(
        choice: AppLanguage,
        systemLanguage: String = Locale.getDefault().language,
        systemTag: String = Locale.getDefault().toLanguageTag()
    ) {
        this.systemLanguage = systemLanguage
        this.systemTag = systemTag
        this.choice = choice
        syncPuiDefaults()
    }

    /** 设置页切换语言。 */
    fun select(choice: AppLanguage) = apply(choice)

    /** PUI 组件自带的默认文案（确定 / 取消 / 搜索）也跟着换。 */
    private fun syncPuiDefaults() {
        PuiLocale.confirm = tr("确定", "OK")
        PuiLocale.cancel = tr("取消", "Cancel")
        PuiLocale.search = tr("搜索", "Search")
        PuiLocale.searchApps = tr("搜索应用", "Search apps")
    }
}

/**
 * 中英双写：界面文案统一走它，切语言后自动跟着变。
 *
 * 用「两种语言写在一起」而不是资源文件，是因为安装器里很多文案是在
 * 非 Composable 的地方生成的（通知、Receiver、Controller），拿不到 Context。
 */
fun tr(zh: String, en: String): String = when (I18n.effective) {
    AppLanguage.Chinese -> zh
    // 繁体 / 粤语不另写一套文案，按字表转换，避免两份文案漂移
    AppLanguage.TraditionalTaiwan -> ChineseVariant.taiwan(zh)
    AppLanguage.TraditionalHongKong -> ChineseVariant.hongKong(zh)
    AppLanguage.Cantonese -> ChineseVariant.cantonese(zh)
    AppLanguage.CantoneseSimplified -> ChineseVariant.cantoneseSimplified(zh)
    AppLanguage.Japanese -> japaneseOverrides[zh] ?: en
    AppLanguage.Russian -> russianOverrides[zh] ?: en
    AppLanguage.Ukrainian -> ukrainianOverrides[zh] ?: en
    AppLanguage.Polish -> polishOverrides[zh] ?: en
    AppLanguage.Latin -> latinOverrides[zh] ?: en
    AppLanguage.Dutch -> dutchOverrides[zh] ?: en
    AppLanguage.Danish -> danishOverrides[zh] ?: en
    AppLanguage.Norwegian -> norwegianOverrides[zh] ?: en
    AppLanguage.Portuguese -> portugueseOverrides[zh] ?: en
    AppLanguage.Korean -> koreanOverrides[zh] ?: en
    AppLanguage.KoreanNorth -> northKoreanOverrides[zh] ?: en
    AppLanguage.German -> germanOverrides[zh] ?: en
    AppLanguage.French -> frenchOverrides[zh] ?: en
    AppLanguage.Italian -> italianOverrides[zh] ?: en
    AppLanguage.Spanish -> spanishOverrides[zh] ?: en
    AppLanguage.Arabic -> arabicOverrides[zh] ?: en
    AppLanguage.Hindi -> hindiOverrides[zh] ?: en
    AppLanguage.Turkish -> turkishOverrides[zh] ?: en
    AppLanguage.Vietnamese -> vietnameseOverrides[zh] ?: en
    else -> en
}
