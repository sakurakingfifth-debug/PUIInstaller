package com.panl.installer.core

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 从 http(s) 链接直接把安装包下到本地。
 *
 * 为什么不用浏览器下载再交给我们：用户分享的是**链接**（GitHub Release、酷安、网盘直链），
 * 浏览器下载完还要在通知栏/文件管理器里再点一次，中间任何一步点错就断了。
 * 这里在安装器内部完成「下载 → 解析 → 安装」，链接是唯一入口。
 *
 * 只做最必要的健壮性：
 *  - 手动跟 3xx 跳转（GitHub Release 会跳到 objects.githubusercontent.com，跨域且换协议）；
 *  - `Accept-Encoding: identity`，避免 gzip 让 Content-Length 和实际字节数对不上；
 *  - 下完检查 ZIP 魔数（PK），拿到 HTML 错误页时立刻判失败，而不是后面「无法解析安装包」。
 */
class ApkDownloader(private val context: Context) {

    sealed interface Result {
        data class Success(val file: File) : Result
        data class Failure(val message: String) : Result
        data object Cancelled : Result

        /** 服务器返回的是 JS 验证页（蓝奏云/阿里云 ESA 这类），需要先用 WebView 过一遍验证。 */
        data object NeedsWeb : Result
    }

    @Volatile
    private var cancelled = false

    /** 用户在浮窗里点了取消 / 页面被关掉。 */
    fun cancel() {
        cancelled = true
    }

    /**
     * @param onProgress received / total（total 未知时是 -1）/ 速度（字节每秒）
     * @param cookie 过了站点验证之后拿到的 Cookie，会原样带上
     */
    fun download(
        url: String,
        cookie: String? = null,
        onProgress: (received: Long, total: Long, speedBps: Long) -> Unit
    ): Result {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        sweep(dir)
        var conn: HttpURLConnection? = null
        var out: File? = null
        return try {
            // 带 cookie 时把几种常见组合都试一遍：不同网盘对 Referer / Cookie 范围要求不一样，
            // 400 / 403 基本都是这两个头不对
            val (response, finalUrl) = if (cookie.isNullOrBlank()) {
                open(url)
            } else {
                val referer = runCatching { URL(url).let { "${it.protocol}://${it.host}/" } }.getOrNull()
                val onlyVerify = cookie.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("acw_sc__v2=") }
                val variants = buildList {
                    if (onlyVerify != null) {
                        add(onlyVerify to null)
                        add(onlyVerify to referer)
                    }
                    add(cookie to null)
                    add(cookie to referer)
                }
                var picked: Pair<HttpURLConnection, URL>? = null
                for ((c, r) in variants) {
                    val pair = open(url, c, r)
                    val code = pair.first.responseCode
                    InstallerLog.record(
                        context, "DOWNLOAD",
                        "重试验证 cookie=${if (c.length > 24) c.take(24) + "…" else c} referer=$r -> $code ${pair.second}"
                    )
                    if (code in 200..299) {
                        picked = pair
                        break
                    }
                    runCatching { pair.first.disconnect() }
                }
                picked ?: open(url, cookie, null)
            }
            conn = response
            if (response.responseCode !in 200..299) {
                return Result.Failure("服务器返回 HTTP ${response.responseCode}")
            }
            val name = fileName(response, finalUrl)
            out = File(dir, name)
            val total = response.contentLengthLong
            var received = 0L
            var lastAt = 0L
            var lastBytes = 0L
            var speed = 0L

            response.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (cancelled) {
                            out.delete()
                            return Result.Cancelled
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        val now = System.currentTimeMillis()
                        // 250ms 节流：进度只用来画界面，推太密纯属浪费
                        if (now - lastAt >= 250) {
                            if (lastAt > 0) {
                                val deltaBytes = received - lastBytes
                                val deltaMs = now - lastAt
                                if (deltaMs > 0) speed = deltaBytes * 1000 / deltaMs
                            }
                            lastAt = now
                            lastBytes = received
                            onProgress(received, total, speed)
                        }
                    }
                }
            }
            onProgress(received, total, speed)

            if (total > 0 && received < total) {
                out.delete()
                return Result.Failure("下载不完整（${Formatters.size(received)} / ${Formatters.size(total)}）")
            }
            if (!looksLikeZip(out)) {
                val text = peekText(out)
                out.delete()
                val head = text.lowercase()
                // 阿里云 ESA 的 JS 验证：一段算 arg1 的脚本，跑完才给 cookie
                val challenge = head.contains("arg1=") &&
                    (head.contains("acw_sc") || head.contains("document.cookie"))
                val html = head.contains("<html") || head.contains("<!doctype") ||
                    head.contains("<head") || head.contains("<script")
                return when {
                    challenge && cookie == null -> Result.NeedsWeb
                    html -> Result.Failure(
                        if (cookie == null) "这个链接是网页，不是安装包的下载地址"
                        else "验证没通过，链接仍然返回网页"
                    )
                    else -> Result.Failure("这个链接返回的不是安装包文件")
                }
            }
            Result.Success(out)
        } catch (e: IOException) {
            out?.delete()
            if (cancelled) Result.Cancelled else Result.Failure(e.message ?: "网络错误")
        } catch (e: Exception) {
            out?.delete()
            if (cancelled) Result.Cancelled else Result.Failure(e.message ?: "下载失败")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 打开连接并跟完跳转，返回最后一跳的响应和最终 URL。 */
    private fun open(
        url: String,
        cookie: String? = null,
        referer: String? = null
    ): Pair<HttpURLConnection, URL> {
        var current = URL(url)
        repeat(8) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept-Encoding", "identity")
                // 统一用浏览器 UA：网盘的 Cookie 是跟 UA 绑定的，
                // 拿 WebView 领的 cookie 却用 "Dalvik/..." 去请求会被判定为异常
                setRequestProperty("User-Agent", WebGate.UA)
                if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
                if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("跳转缺少地址（HTTP $code）")
                current = URL(current, location)
            } else {
                return conn to current
            }
        }
        throw IOException("跳转次数过多")
    }

    /** 文件名：优先 Content-Disposition，其次 URL 最后一段。 */
    private fun fileName(conn: HttpURLConnection, url: URL): String {
        val disposition = conn.getHeaderField("Content-Disposition")
        val fromHeader = disposition
            ?.let { Regex("""filename\*?=(?:UTF-8'')?"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(it) }
            ?.groupValues?.getOrNull(1)
        val raw = fromHeader
            ?: url.path.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: "download.apk"
        val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        // 去掉路径分隔符等非法字符，避免写文件时越界
        val safe = decoded.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return safe.ifBlank { "download.apk" }.take(120)
    }

    /**
     * 读文件开头当文本看（可能是网页）。
     *
     * 服务器经常无视 `Accept-Encoding: identity` 硬塞 gzip，这时候不能直接看字节，
     * 否则永远认不出「JS 验证页」。
     */
    private fun peekText(file: File, limit: Int = 8192): String = runCatching {
        val head = ByteArray(2)
        val isGzip = file.inputStream().use { input ->
            val n = input.read(head)
            n == 2 && head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte()
        }
        if (isGzip) {
            java.util.zip.GZIPInputStream(file.inputStream()).use { gz ->
                String(readUpTo(gz, limit))
            }
        } else {
            file.inputStream().use { input -> String(readUpTo(input, limit)) }
        }
    }.getOrDefault("")

    private fun readUpTo(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(limit)
        val buf = ByteArray(4096)
        var left = limit
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size, left))
            if (n <= 0) break
            out.write(buf, 0, n)
            left -= n
        }
        return out.toByteArray()
    }

    /** 是 ZIP 吗（APK / .apks / .xapk / .apkm 都是 zip）。 */
    private fun looksLikeZip(file: File): Boolean = runCatching {
        if (file.length() < 4) return@runCatching false
        file.inputStream().use { input ->
            val head = ByteArray(4)
            if (input.read(head) < 4) return@runCatching false
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        }
    }.getOrDefault(false)

    /** 清掉 6 小时前的残留下载，和 ApkStaging 一个口径。 */
    private fun sweep(dir: File) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { if (now - it.lastModified() > 6 * 3600_000) it.delete() }
    }

    companion object {
        /** 从分享文本里挑出第一个 http(s) 链接（分享面板常带「标题 + 链接」两行）。 */
        fun extractUrl(text: String?): String? {
            if (text.isNullOrBlank()) return null
            val match = Regex("""https?://[^\s"'<>）)】]+""").find(text) ?: return null
            return match.value.trimEnd('.', ',', '。', '，', ';', '；')
        }

        /** 给界面用的文件名（还没下载，只能从链接猜）。 */
        fun guessName(url: String): String =
            runCatching {
                val path = URL(url).path.substringAfterLast('/')
                val decoded = java.net.URLDecoder.decode(path, "UTF-8")
                decoded.ifBlank { url.substringAfterLast('/').ifBlank { "安装包" } }
            }.getOrDefault("安装包")

        fun speedText(bps: Long): String = when {
            bps <= 0 -> ""
            bps < 1024 -> "$bps B/s"
            bps < 1024 * 1024 -> String.format(Locale.US, "%.0f KB/s", bps / 1024f)
            else -> String.format(Locale.US, "%.1f MB/s", bps / 1024f / 1024f)
        }
    }
}
