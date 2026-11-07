package com.panl.installer.i18n

/**
 * 简体 → 繁体 / 港澳用词转换。
 *
 * 界面文案只有中文一份（[tr] 的 zh 参数），繁体不做第二套文案，
 * 而是在输出时按字表转换：180 个字形差异 + 十几个港台用词。
 * 比逐条翻译省事，也不会出现「改了中文忘了改繁体」的漂移。
 */
object ChineseVariant {

    /** 简体字形（与 [TRADITIONAL] 一一对应）。 */
    private const val SIMPLIFIED = "为与专业个临义书于云仅从们优会传伪体侧储关内册写冲决准击划创删别务动区单发变号后启员场坏块声备复导将层属带库应异弹归当录径态恶户扫择挂损换数无旧时显术机权来极构标栏样检浅测满点状现盖码确称签简红级纳纸纹线组织经结绕给统继续缴获营装见观规计认许设访证评识译试话该详语误说请读谁调谢负责败账质费资转软载输达过运还这进选里钥链锁错键闭问间阶险随隐静页顶项颁预题颜风验开没"

    /** 对应繁体字形。 */
    private const val TRADITIONAL = "為與專業個臨義書於雲僅從們優會傳偽體側儲關內冊寫衝決準擊劃創刪別務動區單發變號後啟員場壞塊聲備復導將層屬帶庫應異彈歸當錄徑態惡戶掃擇掛損換數無舊時顯術機權來極構標欄樣檢淺測滿點狀現蓋碼確稱簽簡紅級納紙紋線組織經結繞給統繼續繳獲營裝見觀規計認許設訪證評識譯試話該詳語誤說請讀誰調謝負責敗賬質費資轉軟載輸達過運還這進選裡鑰鏈鎖錯鍵閉問間階險隨隱靜頁頂項頒預題顏風驗開沒"

    private val glyphs: Map<Char, Char> by lazy {
        SIMPLIFIED.zip(TRADITIONAL).toMap()
    }

    /** 反向表：繁体 → 简体（简体粤语用）。 */
    private val glyphsBack: Map<Char, Char> by lazy {
        TRADITIONAL.zip(SIMPLIFIED).toMap()
    }

    /** 台湾习惯用词（在繁体字形之后替换）。 */
    private val taiwanTerms = listOf(
        "設置" to "設定",
        "默認" to "預設",
        "信息" to "資訊",
        "視頻" to "影片",
        "文件" to "檔案",
        "打印" to "列印",
        "質量" to "品質",
        "網絡" to "網路",
        "軟件" to "軟體",
        "硬件" to "硬體",
        "數據" to "資料",
        "用戶" to "使用者",
        "程序" to "程式",
        "內存" to "記憶體"
    )

    /** 港澳习惯用词（在繁体字形之后替换）。 */
    private val hongKongTerms = listOf(
        "設置" to "設定",
        "默認" to "預設",
        "信息" to "訊息",
        "視頻" to "影片",
        "文件" to "檔案",
        "打印" to "列印",
        "質量" to "質素",
        "網絡" to "網絡",
        "軟件" to "軟件",
        "數據" to "數據",
        "用戶" to "用戶",
        "程序" to "程式"
    )

    /** 简体 → 繁体字形（不做地区用词）。 */
    fun traditional(text: String): String = buildString(text.length) {
        text.forEach { append(glyphs[it] ?: it) }
    }

    /** 简体 → 繁体（台湾用词）。 */
    fun taiwan(text: String): String {
        var out = traditional(text)
        taiwanTerms.forEach { (from, to) -> out = out.replace(from, to) }
        return out
    }

    /** 粤语口语用词（在繁体字形之后替换，长词优先）。 */
    private val cantonesePhrases = listOf(
        "這個" to "呢個", "這些" to "呢啲", "這裡" to "呢度", "這樣" to "噉樣",
        "那個" to "嗰個", "那些" to "嗰啲", "那裡" to "嗰度",
        "我們" to "我哋", "你們" to "你哋", "他們" to "佢哋", "它們" to "佢哋",
        "沒有" to "冇", "不能" to "唔可以", "不會" to "唔會", "不支持" to "唔支援",
        "不要" to "唔好", "不同" to "唔同", "不是" to "唔係", "不夠" to "唔夠",
        "現在" to "而家", "當前" to "而家",
        "什麼" to "乜嘢", "怎麼" to "點樣", "為什麼" to "為乜",
        "還是" to "仲係", "但是" to "但係", "只是" to "只係", "就是" to "就係",
        "一起" to "一齊", "與" to "同", "給" to "畀",
        // 更口语的动词 / 名词
        "點擊" to "撳", "點按" to "撳", "長按" to "長撳", "支持" to "支援",
        "進行" to "做", "保存" to "儲存", "加載" to "載入", "刷新" to "重新整理",
        "搜索" to "搵", "設備" to "裝置", "手機" to "電話", "賬號" to "帳號",
        "文件夾" to "資料夾", "網絡" to "網絡", "圖片" to "圖片", "密碼" to "密碼"
    )

    /** 粤语常用字：的→嘅、了→咗、是→係、不→唔、們→哋、它→佢… */
    private val cantoneseChars = mapOf(
        '的' to '嘅', '了' to '咗', '是' to '係', '不' to '唔',
        '們' to '哋', '它' to '佢', '這' to '呢', '那' to '嗰', '些' to '啲'
    )

    /** 简体 → 繁体 + 港澳用词。 */
    fun hongKong(text: String): String {
        var out = traditional(text)
        hongKongTerms.forEach { (from, to) -> out = out.replace(from, to) }
        return out
    }

    /**
     * 简体 → 粤语（广东话）书写。
     *
     * 繁体字形打底，再套港澳用词，最后按粤语习惯替换虚词和代词：
     * 「与已安装版本的签名不一致」→「同已安裝版本嘅簽名唔一致」。
     * 不是逐句重写，但日常界面的语气已经是粤语。
     */
    /** 粤语，但用简体字形输出（粤语（中国））。 */
    fun cantoneseSimplified(text: String): String {
        val traditionalCantonese = cantonese(text)
        return buildString(traditionalCantonese.length) {
            traditionalCantonese.forEach { append(glyphsBack[it] ?: it) }
        }
    }

    fun cantonese(text: String): String {
        // 人工译过的直接返回，剩下的才走规则转换
        cantoneseOverrides[text]?.let { return it }
        var out = hongKong(text)
        cantonesePhrases.forEach { (from, to) -> out = out.replace(from, to) }
        return buildString(out.length) {
            out.forEach { append(cantoneseChars[it] ?: it) }
        }
    }
}
