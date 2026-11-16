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

    private const val API =
        "https://api.github.com/repos/sakurakingfifth-debug/PUIInstaller/releases/latest"

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
        runCatching {
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
