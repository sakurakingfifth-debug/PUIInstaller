package com.panl.installer.core

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 有些网盘直链外面套了一层 JS 验证（蓝奏云的 CDN 走阿里云 ESA，先返回一段算 cookie 的脚本，
 * 浏览器把脚本跑完、带上 cookie 再来才给文件）。
 *
 * `HttpURLConnection` 跑不了 JS，所以这里挂一个几乎看不见的 WebView 让页面自己把验证走完，
 * 再把该链接的 cookie 交回去，由 [ApkDownloader] 带着 cookie 重新下一次。
 * 这是「用内置浏览器把网页打开一次」，不涉及破解或绕过登录。
 */
object WebGate {

    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    /** 验证结果：cookie 一定有；[downloadUrl] 是页面自己跳到的下载地址（网盘常见的一次性直链）。 */
    data class Result(val cookie: String?, val downloadUrl: String?)

    /**
     * @return 验证结果；完全拿不到东西返回 null。
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun harvest(
        activity: Activity,
        url: String,
        timeoutMs: Long = 8_000
    ): Result? = withContext(Dispatchers.Main) {
        val web = WebView(activity.applicationContext)
        val cookieManager = CookieManager.getInstance()
        runCatching { cookieManager.setAcceptCookie(true) }
        runCatching { cookieManager.setAcceptThirdPartyCookies(web, true) }
        runCatching {
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.userAgentString = UA
        }
        web.webViewClient = WebViewClient()

        // 验证通过后服务器会直接回文件（Content-Disposition），WebView 自己下不了，
        // 会走 DownloadListener —— 这个 URL 往往才是一次性直链，比原始链接更该拿去下载
        var downloadUrl: String? = null
        web.setDownloadListener { u, _, _, _, _ -> downloadUrl = u }

        // 必须挂进窗口、而且要有实际尺寸才会跑 JS 定时器（Chromium 会把不可见/被遮挡的页降频），
        // 所以铺满窗口但透明度压到 1%：用户看不出，验证却能跑起来
        val parent = activity.window?.decorView as? ViewGroup
        runCatching {
            parent?.addView(
                web,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            web.alpha = 0.01f
        }
        try {
            runCatching { web.loadUrl(url) }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val cookie = runCatching { cookieManager.getCookie(url) }.getOrNull()
                // 验证通过后 ESA 一定会下发 acw_sc__v2；拿到它就可以去下载了
                if (!cookie.isNullOrBlank() && cookie.contains("acw_sc__v2")) {
                    // 再等一小会儿，让页面完成跳转，好拿到 DownloadListener 里的直链
                    for (i in 0 until 8) {
                        if (downloadUrl != null) break
                        delay(250)
                    }
                    return@withContext Result(cookie, downloadUrl)
                }
                delay(250)
            }
            val cookie = runCatching { cookieManager.getCookie(url) }.getOrNull()
            if (cookie.isNullOrBlank()) null else Result(cookie, downloadUrl)
        } finally {
            runCatching { parent?.removeView(web) }
            runCatching { web.destroy() }
        }
    }
}
