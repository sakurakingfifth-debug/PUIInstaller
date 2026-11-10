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
    }

    @Volatile
    private var cancelled = false

    /** 用户在浮窗里点了取消 / 页面被关掉。 */
    fun cancel() {
        cancelled = true
    }

    /**
     * @param onProgress received / total（total 未知时是 -1）/ 速度（字节每秒）
     */
    fun download(url: String, onProgress: (received: Long, total: Long, speedBps: Long) -> Unit): Result {
        val dir = File(context.cacheDir, "downloads").apply { mkdirs() }
        sweep(dir)
        var conn: HttpURLConnection? = null
        var out: File? = null
        return try {
            val (response, finalUrl) = open(url)
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
                val raw = runCatching { out.readBytes().take(400).toByteArray() }.getOrDefault(ByteArray(0))
                out.delete()
                val head = String(raw).lowercase()
                // gzip 开头（1f 8b）多半是服务器无视 identity 压过的网页
                val gzip = raw.size >= 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()
                return if (gzip || head.contains("<html") || head.contains("<!doctype") || head.contains("<head")) {
                    Result.Failure("这个链接是网页，不是安装包的下载地址")
                } else {
                    Result.Failure("这个链接返回的不是安装包文件")
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
    private fun open(url: String): Pair<HttpURLConnection, URL> {
        var current = URL(url)
        repeat(8) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Accept-Encoding", "identity")
                // 有些 CDN 会拒掉默认的 "Dalvik/..." UA
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) PUIInstaller"
                )
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
