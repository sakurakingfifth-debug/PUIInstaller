package com.panl.installer.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查 GitHub Release 上有没有新版本。
 *
 * 走公开的 releases API（匿名可用，每小时 60 次，够用了；仓库要是改成私有这里会 404，
 * 界面上会显示「检查失败」而不是假装最新）。
 *
 * 版本比较用 versionName 的数字段（3.7.94 vs 3.7.95），不看 versionCode ——
 * 因为 Release 包里只有 APK，versionCode 得下下来才知道，没必要为了比较先下 7MB。
 */
object UpdateChecker {

    private const val OWNER = "sakurakingfifth-debug"
    private const val REPO = "PUIInstaller"

    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** API 被拦（403/限流）时的兜底：github.com 的网页版，不占 API 配额。 */
    private const val WEB_LATEST = "https://github.com/$OWNER/$REPO/releases/latest"
    private const val WEB_ASSETS = "https://github.com/$OWNER/$REPO/releases/expanded_assets/"

    data class Release(
        /** 原始 tag，例如 v3.7.95 */
        val tag: String,
        /** 去掉前缀 v 的版本号，例如 3.7.95 */
        val version: String,
        val notes: String,
        val pageUrl: String,
        /** APK 直链；没有 APK 附件时为 null（那就只能打开发布页） */
        val apkUrl: String?,
        val apkSize: Long
    )

    suspend fun fetchLatest(): Result<Release> = withContext(Dispatchers.IO) {
        val api = runCatching { fetchApi() }
        if (api.isSuccess) return@withContext api
        // API 403 的两种情况：代理拦了 api.github.com、或同一 IP 超过 60 次/小时。
        // 退回 github.com 网页版：/releases/latest 会 302 到 /releases/tag/<tag>，
        // 再去 expanded_assets/<tag> 里捞 APK 链接 —— 这条路子不消耗 API 配额。
        val web = runCatching { fetchWeb() }
        if (web.isSuccess) web else api
    }

    private fun fetchApi(): Release {
        return run {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub 要求带 UA，否则直接 403
                setRequestProperty("User-Agent", "PUInstaller-Updater")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    // 403 基本都是「被代理拦了」或「同一 IP 请求过多」，别让用户对着数字发愣
                    error(
                        when (code) {
                            403 -> "被拒绝（403）：可能是代理拦了 api.github.com，或请求过于频繁"
                            404 -> "仓库不可访问（404）"
                            else -> "HTTP $code"
                        }
                    )
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val tag = json.optString("tag_name").ifBlank { error("没有 tag_name") }

                var apkUrl: String? = null
                var apkSize = 0L
                val assets = json.optJSONArray("assets")
                for (i in 0 until (assets?.length() ?: 0)) {
                    val item = assets!!.getJSONObject(i)
                    if (item.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = item.optString("browser_download_url").ifBlank { null }
                        apkSize = item.optLong("size")
                        break
                    }
                }

                Release(
                    tag = tag,
                    version = tag.trimStart('v', 'V'),
                    notes = json.optString("body"),
                    pageUrl = json.optString("html_url"),
                    apkUrl = apkUrl,
                    apkSize = apkSize
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * 网页版兜底。
     *
     * 1. `GET /releases/latest`：跟随 302，最终地址里的 `/tag/<tag>` 就是最新版本号；
     * 2. `GET /releases/expanded_assets/<tag>`：这个片段页里带着资产直链，
     *    正则捞一个 `.apk` 出来（拿不到就只给发布页，界面上会退化成打开浏览器）。
     *
     * 拿不到更新说明（body）——网页版没有结构化字段，而界面也不需要它。
     */
    private fun fetchWeb(): Release {
        val (_, finalUrl) = httpGet(WEB_LATEST)
        val tag = finalUrl.toString().substringAfterLast("/tag/", "").trim().trim('/')
        if (tag.isBlank() || tag.contains("?")) error("读不到版本号")
        val (html, _) = httpGet(WEB_ASSETS + tag)
        val apkPath = Regex("""(/[^"'\s]+/releases/download/[^"'\s]+\.apk)""").find(html)?.groupValues?.get(1)
        return Release(
            tag = tag,
            version = tag.trimStart('v', 'V'),
            notes = "",
            pageUrl = "https://github.com/$OWNER/$REPO/releases/tag/$tag",
            apkUrl = apkPath?.let { "https://github.com$it" },
            apkSize = 0
        )
    }

    private fun httpGet(url: String): Pair<String, java.net.URL> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            // 网页版要“像浏览器”，GitHub 对非浏览器 UA 的 HTML 也会挑刺
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) PUIInstaller")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return body to conn.url
        } finally {
            conn.disconnect()
        }
    }

    /**
     * latest 是否比 current 新。
     *
     * 按 `.` 切段逐位比数值（3.7.9 < 3.7.10），前面有非数字的（比如 3.7.95-beta）先掐掉后缀。
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(v: String): List<Int> =
        v.substringBefore('-').substringBefore('+')
            .split('.')
            .mapNotNull { it.trim().toIntOrNull() }
}
