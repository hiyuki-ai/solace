package com.solace.app.data.douyin

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.solace.app.data.work.MediaKind
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * slides / 实况分享页的数据由抖音前端异步加载，静态分享页不包含每张图对应的
 * 视频地址。这里用不可见 WebView 完成页面自己的 anti-bot 校验，然后优先读取
 * 页面内完整的 RSC 作品数据，并保留已渲染 DOM 作为兜底。
 */
class DouyinSlidesResolver(context: Context) {

    private val appContext = context.applicationContext

    suspend fun resolve(finalUrl: String, awemeId: String): DouyinWork? =
        withContext(Dispatchers.Main.immediate) {
            val webView = try {
                createWebView()
            } catch (e: Exception) {
                return@withContext null
            }

            try {
                Log.d(TAG, "load slides page")
                webView.loadUrl(finalUrl)
                val deadline = SystemClock.elapsedRealtime() + LOAD_TIMEOUT_MS
                val extractScript = EXTRACT_SCRIPT.replace("__AWEME_ID__", awemeId)
                var lastSignature = ""
                var stableSince = 0L
                var lastLoggedSignature = ""
                while (SystemClock.elapsedRealtime() < deadline) {
                    delay(POLL_INTERVAL_MS)
                    val raw = evaluateJavascript(webView, extractScript) ?: continue
                    val jsonText = (JSONTokener(raw).nextValue() as? String) ?: continue
                    val payload = runCatching { JSONObject(jsonText) }.getOrNull() ?: continue
                    if (payload.optBoolean("ok")) {
                        val slides = payload.optJSONArray("slides")
                        val count = slides?.length() ?: 0
                        val expected = payload.optInt("totalCount", 0)
                        val source = payload.optString("source")
                        val signature =
                            "$source:$count:${slides?.optJSONObject(count - 1)?.optString("image")}"
                        val now = SystemClock.elapsedRealtime()
                        if (signature != lastLoggedSignature) {
                            lastLoggedSignature = signature
                            val liveCount = (0 until count).count {
                                slides?.optJSONObject(it)?.optString("video").orEmpty().isNotBlank()
                            }
                            Log.d(TAG, "slides=$count live=$liveCount source=$source")
                        }
                        if (count >= expected && expected > 0) {
                            Log.d(TAG, "resolved ${SystemClock.elapsedRealtime() - (deadline - LOAD_TIMEOUT_MS)}ms")
                            return@withContext buildWork(payload, awemeId)
                        }
                        if (signature != lastSignature) {
                            lastSignature = signature
                            stableSince = now
                        } else if (source == "dom" && now - stableSince >= DOM_STABLE_QUERY_MS) {
                            return@withContext buildWork(payload, awemeId)
                        }
                    }
                }
                Log.w(TAG, "slides page timed out")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "slides page failed", e)
                null
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView =
        WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.userAgentString = DESKTOP_USER_AGENT
            val cookies = CookieManager.getInstance()
            cookies.setAcceptCookie(true)
            cookies.setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    Log.d(TAG, "page started: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "page finished: $url")
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val scheme = request?.url?.scheme
                    return scheme != "http" && scheme != "https"
                }
            }
        }

    private suspend fun evaluateJavascript(webView: WebView, script: String): String? =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    private fun buildWork(payload: JSONObject, awemeId: String): DouyinWork? {
        val slides = payload.optJSONArray("slides") ?: return null
        val media = ArrayList<DouyinMedia>(slides.length())
        for (index in 0 until slides.length()) {
            val slide = slides.optJSONObject(index) ?: continue
            val imageUrl = slide.optString("image").playableUrl() ?: continue
            val videoUrl = slide.optString("video").playableUrl()
            media += DouyinMedia(
                kind = if (videoUrl != null) MediaKind.VIDEO else MediaKind.IMAGE,
                url = videoUrl ?: imageUrl,
                width = slide.optInt("width").takeIf { it > 0 },
                height = slide.optInt("height").takeIf { it > 0 },
                durationMs = slide.optLong("duration").takeIf { it > 0 },
                previewUrl = imageUrl,
            )
        }
        if (media.isEmpty()) return null

        val authorUid = payload.optString("authorUid").takeIf { it.isNotBlank() }
        val authorSecUid = payload.optString("authorSecUid").takeIf { it.isNotBlank() }
        val authorName = payload.optString("authorName").takeIf { it.isNotBlank() }
        val author = if (authorUid != null || authorSecUid != null || authorName != null) {
            DouyinAuthor(
                uid = authorUid,
                secUid = authorSecUid,
                uniqueId = null,
                shortId = null,
                nickname = authorName,
                avatarUrl = payload.optString("avatarUrl").playableUrl(),
            )
        } else {
            null
        }

        val audioUrl = payload.optString("audioUrl").playableUrl()
        return DouyinWork(
            awemeId = awemeId,
            kind = DouyinKind.IMAGES,
            caption = payload.optString("caption"),
            author = author,
            publishedAt = payload.optLong("publishedAtMs").takeIf { it > 0 }
                ?: parsePublishedAt(payload.optString("publishedAtText")),
            media = media,
            coverUrl = media.first().previewUrl,
            audio = audioUrl?.let { DouyinAudio(url = it, title = null, durationMs = null) },
        )
    }

    private fun parsePublishedAt(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.parse(value)?.time
        }.getOrNull()
    }

    private fun String.playableUrl(): String? {
        val value = trim()
        return when {
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) ->
                "https://" + value.removePrefix("http://")
            value.startsWith("//") -> "https:$value"
            else -> null
        }
    }

    private companion object {
        const val TAG = "DouyinSlides"
        const val LOAD_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 250L
        const val DOM_STABLE_QUERY_MS = 6_000L
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/158.0.0.0 Safari/537.36"

        val EXTRACT_SCRIPT = """
            (function () {
              function plain(value) {
                return value == null ? '' : String(value);
              }

              function playable(value) {
                value = plain(value).trim();
                if (value.indexOf('//') === 0) return 'https:' + value;
                if (value.indexOf('https://') === 0) return value;
                if (value.indexOf('http://') === 0) {
                  return 'https://' + value.slice(7);
                }
                return '';
              }

              function addressItems(address) {
                if (!address) return [];
                if (Array.isArray(address)) return address;
                var list = address.urlList || address.url_list;
                return list && list.length ? list : [address];
              }

              function itemUrl(item) {
                return playable(
                  typeof item === 'string' ? item :
                    (item && (item.src || item.url || item.uri))
                );
              }

              function firstImage(image) {
                var sources = [
                  image && image.originImage,
                  image && image.origin_image,
                  image && image.displayImage,
                  image && image.display_image,
                  image,
                  image && image.downloadImage,
                  image && image.download_image,
                  image && image.downloadUrl,
                  image && image.download_url
                ];
                var candidates = [];
                for (var i = 0; i < sources.length; i++) {
                  var items = addressItems(sources[i]);
                  for (var j = 0; j < items.length; j++) {
                    var url = itemUrl(items[j]);
                    if (url) candidates.push(url);
                  }
                }
                var clean = candidates.filter(function (url) {
                  return url.toLowerCase().indexOf('watermark') < 0;
                });
                if (!clean.length) clean = candidates;
                var jpeg = clean.find(function (url) {
                  return /\.(jpe?g|png)(\?|$)/i.test(url);
                });
                return jpeg || clean[0] || '';
              }

              function videoUrl(video) {
                if (!video) return '';
                var addresses = [
                  video.playAddr,
                  video.play_addr,
                  video.downloadAddr,
                  video.download_addr
                ];
                var bitRates = video.bitRateList || video.bit_rate || video.bitRate || [];
                for (var b = 0; b < bitRates.length; b++) {
                  var bitRate = bitRates[b] || {};
                  addresses.push(bitRate.playAddr, bitRate.play_addr);
                }
                for (var i = 0; i < addresses.length; i++) {
                  var items = addressItems(addresses[i]);
                  for (var j = 0; j < items.length; j++) {
                    var url = itemUrl(items[j]);
                    if (url) return url;
                  }
                }
                return '';
              }

              function compactSlide(image) {
                return {
                  image: firstImage(image),
                  video: videoUrl(image && image.video),
                  width: image && (image.width || '') || '',
                  height: image && (image.height || '') || '',
                  duration: image && image.video &&
                    (image.video.duration || image.video.durationMs) || ''
                };
              }

              var targetAwemeId = '__AWEME_ID__';
              var marker = 'self.__pace_f.push(';
              var scripts = document.scripts;
              var detail = null;

              // 抖音在 anti-bot reload 后把完整作品数据放在 RSC 脚本中。
              // 这份数据比幻灯片 DOM 更早、更完整，也能直接拿到实况 MP4。
              for (var s = 0; s < scripts.length && !detail; s++) {
                var text = scripts[s].textContent || '';
                var call = text.indexOf(marker);
                if (call < 0 || text.indexOf(targetAwemeId) < 0) continue;
                try {
                  var literal = text.slice(call + marker.length, text.lastIndexOf(')'));
                  var chunks = JSON.parse(literal);
                  var flight = chunks
                    .filter(function (value) { return typeof value === 'string'; })
                    .join('');
                  var colon = flight.indexOf(':');
                  if (colon < 0) continue;
                  var root = JSON.parse(flight.slice(colon + 1));
                  var stack = [root];
                  while (stack.length && !detail) {
                    var value = stack.pop();
                    if (!value || typeof value !== 'object') continue;
                    var candidate = value.aweme && value.aweme.detail;
                    if (candidate && plain(candidate.awemeId) === targetAwemeId) {
                      detail = candidate;
                      break;
                    }
                    var children = Object.values(value);
                    for (var c = 0; c < children.length; c++) {
                      if (children[c] && typeof children[c] === 'object') {
                        stack.push(children[c]);
                      }
                    }
                  }
                } catch (ignored) {}
              }

              if (detail && detail.images && detail.images.length) {
                var author = detail.authorInfo || detail.author || {};
                var avatar = author.avatarThumb || author.avatar_thumb || {};
                var music = detail.music || {};
                var playUrl = music.playUrl || music.play_url || {};
                return JSON.stringify({
                  ok: true,
                  source: 'rsc',
                  slides: detail.images.map(compactSlide),
                  totalCount: detail.images.length,
                  caption: plain(detail.desc || detail.caption),
                  authorName: plain(author.nickname),
                  authorUid: plain(author.uid),
                  authorSecUid: plain(author.secUid || author.sec_uid),
                  avatarUrl: itemUrl(addressItems(avatar)[0]),
                  publishedAtMs: Number(detail.createTime || 0) * 1000,
                  audioUrl: itemUrl(addressItems(playUrl)[0])
                });
              }

              // 兜底：部分版本没有 RSC 数据时，读取已经渲染出的幻灯片。
              var slides = [];
              var nodes = document.querySelectorAll('.dySwiperSlide');
              for (var i = 0; i < nodes.length; i++) {
                var slide = nodes[i];
                var image = slide.querySelector('img[src]');
                if (!image || !image.src) continue;
                var video = slide.querySelector('video source[src]');
                slides.push({
                  image: playable(image.src),
                  video: video && video.src ? playable(video.src) : ''
                });
              }
              if (!slides.length) return JSON.stringify({
                ok: false,
                url: location.href,
                readyState: document.readyState
              });

              var title = plain(document.title).replace(/\s*-\s*抖音\s*$/, '');
              var bodyText = document.body ? plain(document.body.innerText) : '';
              var published = bodyText.match(
                /发布时间：(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/
              );
              var counter = document.querySelector('.YoycnaPk');
              var counterText = counter ? plain(counter.innerText) : bodyText;
              var numbers = counterText.match(/\d+/g) || [];
              var totalCount = numbers.length >= 2 ? parseInt(numbers[1], 10) : 0;
              var audioSource = document.querySelector(
                'source[src*=".mp3"], video[data-index="-1"] source[src]'
              );
              var avatar = document.querySelector('img[src*="aweme-avatar"]');
              var authorName = '';
              var authorSecUid = '';
              var ldScripts = document.querySelectorAll(
                'script[type="application/ld+json"]'
              );
              for (var l = 0; l < ldScripts.length; l++) {
                try {
                  var ld = JSON.parse(ldScripts[l].textContent || '{}');
                  var items = ld.itemListElement || [];
                  for (var itemIndex = 0; itemIndex < items.length; itemIndex++) {
                    var userUrl = plain(items[itemIndex] && items[itemIndex].item);
                    if (userUrl.indexOf('/user/') >= 0) {
                      authorName = plain(items[itemIndex].name);
                      authorSecUid = userUrl.split('/user/')[1].split(/[?#/]/)[0];
                    }
                  }
                } catch (ignored) {}
              }
              return JSON.stringify({
                ok: true,
                source: 'dom',
                slides: slides,
                caption: title,
                authorName: authorName,
                authorSecUid: authorSecUid,
                avatarUrl: avatar && avatar.src ? avatar.src : '',
                publishedAtText: published ? published[1] : '',
                audioUrl: audioSource && audioSource.src ? audioSource.src : '',
                totalCount: totalCount
              });
            })()
        """.trimIndent()
    }
}
