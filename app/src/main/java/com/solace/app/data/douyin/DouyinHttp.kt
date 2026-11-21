package com.solace.app.data.douyin

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 抖音请求用的极简 HTTP 客户端。
 *
 * 手动处理 302：短链跳转和 CDN 直链都依赖重定向，同时要把手机 UA / Referer
 * 带到每一跳，避免被 CDN 拒绝。
 */
internal object DouyinHttp {

    private const val USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    private const val REFERER = "https://www.douyin.com/"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 8

    class Opened(val url: String, val connection: HttpURLConnection)

    /** 发起 GET 并跟随重定向，返回最终连接；调用方负责 disconnect。 */
    fun open(url: String, cookie: String? = null): Opened {
        var current = url
        var redirects = 0
        while (true) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", REFERER)
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                setRequestProperty("Accept-Encoding", "identity")
                if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
            }

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || redirects >= MAX_REDIRECTS) {
                    throw IOException("链接跳转失败，请稍后重试")
                }
                redirects++
                current = URL(URL(current), location).toString()
                continue
            }
            return Opened(current, connection)
        }
    }

    fun readText(opened: Opened): String =
        opened.connection.inputStream.use { input ->
            input.reader(Charsets.UTF_8).readText()
        }

    /** 下载到 [destination]，返回响应的 Content-Type。 */
    fun download(url: String, destination: File, cookie: String? = null): String {
        val opened = open(url, cookie)
        val connection = opened.connection
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val contentType = connection.contentType.orEmpty()
            if (contentType.startsWith("text/", ignoreCase = true)) {
                throw IOException("链接已失效")
            }
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            return contentType
        } finally {
            connection.disconnect()
        }
    }
}
