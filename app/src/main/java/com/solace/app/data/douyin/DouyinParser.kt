package com.solace.app.data.douyin

import com.solace.app.data.work.MediaKind
import java.io.IOException
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DouyinParseException(message: String) : IOException(message)

/**
 * 解析抖音分享链接，拿到文案、作者、发布时间和素材直链。
 *
 * 走的是抖音给 H5 分享页下发的 SSR 数据：
 * 1. 短链 302 到 www.iesdouyin.com/share/video/{aweme_id}；
 * 2. 第一次请求分享页只下发 ttwid Cookie，带上它再请求一次才返回完整数据；
 * 3. 页面里的 `window._ROUTER_DATA` 就是作品 JSON，视频和图文都挂在
 *    `item_list[0]` 上（图文走同一个 /share/video/ 地址也能拿到）。
 *
 * 全程不需要登录态和签名；作品被删、设为私密或触发风控时页面只有空壳，
 * 这里统一提示作品不可见。
 */
class DouyinParser {

    suspend fun parse(shareText: String): DouyinWork = withContext(Dispatchers.IO) {
        val input = shareText.trim()
        if (input.isEmpty()) throw DouyinParseException("请先粘贴抖音分享链接")

        val link = findShareLink(input)
            ?: throw DouyinParseException("没有识别到抖音链接，请检查后重试")

        val finalUrl = if (findAwemeId(link) != null) link else resolveFinalUrl(link)
        val awemeId = findAwemeId(finalUrl)
            ?: throw DouyinParseException("无法识别作品，请确认链接是否有效")

        val html = loadSharePage(awemeId)
        val root = extractRouterData(html)
            ?: throw DouyinParseException("解析失败，请稍后重试")
        val item = findAwemeItem(root, awemeId)
            ?: throw DouyinParseException("作品不存在、已删除或暂时无法解析")
        buildWork(item, awemeId)
    }

    /** 从整段分享文案里抠出链接。 */
    private fun findShareLink(text: String): String? =
        URL_PATTERN.findAll(text)
            .map { it.value.trimEnd(')', ']', '>', '。', '，', '.', '!', '?', '"') }
            .firstOrNull { it.contains("douyin.com", ignoreCase = true) }

    /** 短链要跟随 302 才能拿到真实作品地址；网络异常直接抛给上层提示。 */
    private fun resolveFinalUrl(link: String): String {
        val opened = DouyinHttp.open(link)
        val url = opened.url
        opened.connection.disconnect()
        return url
    }

    private fun findAwemeId(url: String): String? =
        AWEME_ID_PATTERNS.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }

    /**
     * 分享页要用 ttwid Cookie 才会吐数据：第一次请求只为拿 Cookie，
     * 第二次带 Cookie 再请求。Cookie 缓存起来给后续解析复用。
     */
    private fun loadSharePage(awemeId: String): String {
        val url = "https://www.iesdouyin.com/share/video/$awemeId/"

        cachedTtwid?.let { cookie ->
            fetchText(url, cookie)?.takeIf { it.contains("item_list") }?.let { return it }
            cachedTtwid = null
        }

        val opened = DouyinHttp.open(url)
        val (html, cookie) = try {
            DouyinHttp.readText(opened) to ttwidFrom(opened.connection)
        } finally {
            opened.connection.disconnect()
        }

        if (cookie != null) {
            cachedTtwid = cookie
            fetchText(url, cookie)?.let { return it }
        }
        return html
    }

    private fun fetchText(url: String, cookie: String?): String? {
        val opened = try {
            DouyinHttp.open(url, cookie)
        } catch (e: Exception) {
            return null
        }
        return try {
            if (opened.connection.responseCode !in 200..299) {
                null
            } else {
                DouyinHttp.readText(opened)
            }
        } catch (e: Exception) {
            null
        } finally {
            opened.connection.disconnect()
        }
    }

    private fun ttwidFrom(connection: HttpURLConnection): String? {
        val headers = connection.headerFields ?: return null
        for ((name, values) in headers) {
            if (!name.equals("Set-Cookie", ignoreCase = true)) continue
            for (value in values.orEmpty()) {
                if (value.startsWith("ttwid=")) return value.substringBefore(';')
            }
        }
        return null
    }

    /** `window._ROUTER_DATA = {...}` 里取出 JSON。 */
    private fun extractRouterData(html: String): JSONObject? {
        for (marker in ROUTER_MARKERS) {
            val markerIndex = html.indexOf(marker)
            if (markerIndex < 0) continue
            val start = html.indexOf('{', markerIndex)
            if (start < 0) continue
            val end = matchingBrace(html, start) ?: continue
            runCatching { JSONObject(html.substring(start, end + 1)) }.getOrNull()?.let { return it }
        }
        return null
    }

    /** 从 start 处的 `{` 开始做字符串感知的括号匹配。 */
    private fun matchingBrace(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
        }
        return null
    }

    /** 页面结构会随版本变化，直接按特征找 item，不依赖固定的 loaderData key。 */
    private fun findAwemeItem(root: JSONObject, awemeId: String): JSONObject? {
        val queue = ArrayDeque<Any>()
        queue.add(root)
        var fallback: JSONObject? = null
        while (queue.isNotEmpty()) {
            when (val current = queue.removeFirst()) {
                is JSONObject -> {
                    val id = current.stringOrNull("aweme_id")
                    val looksLikeItem = current.has("desc") &&
                        (current.has("video") || current.has("images"))
                    if (looksLikeItem && id == awemeId) return current
                    if (fallback == null && looksLikeItem && !id.isNullOrBlank()) {
                        fallback = current
                    }
                    for (key in current.keys()) {
                        val value = current.get(key)
                        if (value is JSONObject || value is JSONArray) queue.add(value)
                    }
                }

                is JSONArray -> {
                    for (index in 0 until current.length()) {
                        val value = current.opt(index)
                        if (value is JSONObject || value is JSONArray) queue.add(value)
                    }
                }
            }
        }
        return fallback
    }

    private fun buildWork(item: JSONObject, awemeId: String): DouyinWork {
        val images = item.optJSONArray("images")
        val video = item.optJSONObject("video")
        val media = ArrayList<DouyinMedia>()

        if (images != null) {
            for (index in 0 until images.length()) {
                val image = images.optJSONObject(index) ?: continue
                val url = bestImageUrl(image.optJSONArray("url_list").toStringList()) ?: continue
                media += DouyinMedia(
                    kind = MediaKind.IMAGE,
                    url = url,
                    width = image.intOrNull("width"),
                    height = image.intOrNull("height"),
                    durationMs = null,
                )
            }
        }

        if (media.isEmpty() && video != null) {
            val url = bestVideoUrl(video)
                ?: throw DouyinParseException("没有解析到可播放的视频地址")
            media += DouyinMedia(
                kind = MediaKind.VIDEO,
                url = url,
                width = video.intOrNull("width"),
                height = video.intOrNull("height"),
                durationMs = video.longOrNull("duration"),
            )
        }

        if (media.isEmpty()) throw DouyinParseException("没有解析到可用的视频或图片")

        val isImagePost = media.first().kind == MediaKind.IMAGE
        return DouyinWork(
            awemeId = awemeId,
            kind = if (isImagePost) DouyinKind.IMAGES else DouyinKind.VIDEO,
            caption = item.stringOrNull("desc").orEmpty(),
            author = item.optJSONObject("author")?.let(::buildAuthor),
            publishedAt = item.longOrNull("create_time")?.let { seconds ->
                if (seconds > 10_000_000_000L) seconds else seconds * 1000L
            },
            media = media,
            coverUrl = if (isImagePost) null else coverUrlOf(video),
        )
    }

    private fun buildAuthor(author: JSONObject) = DouyinAuthor(
        uid = author.stringOrNull("uid"),
        secUid = author.stringOrNull("sec_uid"),
        uniqueId = author.stringOrNull("unique_id"),
        shortId = author.stringOrNull("short_id"),
        nickname = author.stringOrNull("nickname"),
        avatarUrl = firstUrl(author.optJSONObject("avatar_thumb"))
            ?: firstUrl(author.optJSONObject("avatar_medium"))
            ?: firstUrl(author.optJSONObject("avatar_larger")),
    )

    private fun coverUrlOf(video: JSONObject?): String? {
        if (video == null) return null
        return firstUrl(video.optJSONObject("cover"))
            ?: firstUrl(video.optJSONObject("origin_cover"))
            ?: firstUrl(video.optJSONObject("dynamic_cover"))
    }

    /**
     * 优先挑最高码率的 H.264 版本；没有码率表就退回 play_addr。
     * playwm 是带水印地址，换成 play 拿无水印版本。
     */
    private fun bestVideoUrl(video: JSONObject): String? {
        val candidates = ArrayList<String>()

        val bitRates = video.optJSONArray("bit_rate")
        if (bitRates != null) {
            val entries = (0 until bitRates.length())
                .mapNotNull { bitRates.optJSONObject(it) }
                .sortedByDescending { it.optInt("bit_rate", 0) }
            for (entry in entries) {
                val format = entry.optString("format")
                if (entry.optBoolean("is_h265", false) || format.contains("265")) continue
                entry.optJSONObject("play_addr")
                    ?.optJSONArray("url_list")
                    ?.toStringList()
                    ?.let(candidates::addAll)
            }
        }

        video.optJSONObject("play_addr")
            ?.optJSONArray("url_list")
            ?.toStringList()
            ?.let(candidates::addAll)
        video.optJSONObject("download_addr")
            ?.optJSONArray("url_list")
            ?.toStringList()
            ?.let(candidates::addAll)

        return candidates.asSequence()
            .filter { it.isNotBlank() }
            .map { it.https().replace("playwm", "play") }
            .firstOrNull()
    }

    /** url_list 里通常同时有 webp 和 jpeg，优先拿 jpeg 原图。 */
    private fun bestImageUrl(urls: List<String>): String? =
        urls.firstOrNull {
            it.contains(".jpeg", true) || it.contains(".png", true) || it.contains(".heic", true)
        }
            ?: urls.firstOrNull { !it.contains(".webp", true) }
            ?: urls.firstOrNull()

    private fun firstUrl(image: JSONObject?): String? =
        image?.optJSONArray("url_list").toStringList().firstOrNull { it.isNotBlank() }?.https()

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).takeIf { it.isNotBlank() }
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it > 0 }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key).takeIf { it > 0L }

    private fun String.https(): String =
        if (startsWith("http://")) "https://" + drop("http://".length) else this

    private companion object {
        val URL_PATTERN = Regex("""https?://[A-Za-z0-9._~:/?#\[\]@!${'$'}&'()*+,;=%-]+""")

        val AWEME_ID_PATTERNS = listOf(
            Regex("""/(?:video|note|slide|slides)/(\d{8,})"""),
            Regex("""(?:modal_id|aweme_id|item_id)=(\d{8,})"""),
        )

        val ROUTER_MARKERS = listOf("window._ROUTER_DATA", "window._SSR_HYDRATED_DATA")

        @Volatile
        var cachedTtwid: String? = null
    }
}
