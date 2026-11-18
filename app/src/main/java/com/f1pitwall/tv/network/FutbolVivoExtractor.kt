package com.f1pitwall.tv.network

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

data class F1LiveStreamEvent(
    val title: String,
    val isLive: Boolean,
    val startTime: String?,
    val streamUrl: String?,
    val streamName: String?
)

class FutbolVivoExtractor(private val context: Context) {

    private var webView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    var onF1EventDetected: ((F1LiveStreamEvent) -> Unit)? = null
    var onNoEventDetected: (() -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun startExtraction() {
        mainHandler.post {
            try {
                if (webView == null) {
                    webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                        addJavascriptInterface(Bridge(), "AndroidBridge")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                injectWatcher()
                            }
                        }
                    }
                }
                webView?.loadUrl("https://futbolvivo.tv/")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun injectWatcher() {
        val js = """
            (function() {
                let attempts = 0;
                function scanMatches() {
                    attempts++;
                    const all = [...(window.seAllMatches || []), ...(window.seLatinMatches || [])];
                    const f1Matches = all.filter(m => {
                        const title = ((m.title || m.name || '') + '').toLowerCase();
                        // Filtro estricto: Solo carreras de F1 / Gran Premio
                        const isF1 = title.includes('f1') || title.includes('formula 1') || title.includes('gran premio') || title.includes('grand prix');
                        const isNotSubSession = !title.includes('practica') && !title.includes('practice') && !title.includes('qualy') && !title.includes('clasificacion');
                        return isF1 && isNotSubSession;
                    });

                    if (f1Matches.length > 0) {
                        const target = f1Matches[0];
                        window.AndroidBridge.postF1Event(JSON.stringify(target));
                    } else if (attempts > 6) {
                        window.AndroidBridge.postNoF1();
                    }
                }
                setInterval(scanMatches, 2500);
                scanMatches();
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    inner class Bridge {
        @JavascriptInterface
        fun postF1Event(jsonStr: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = gson.fromJson(jsonStr, JsonObject::class.java)
                    val title = json.get("title")?.asString
                        ?: json.get("name")?.asString
                        ?: "Fórmula 1 Gran Premio"

                    val isLive = json.get("live")?.asBoolean ?: false
                    val startsAt = json.get("begin_at")?.asString ?: json.get("starts_at")?.asString

                    var directHlsUrl: String? = null
                    var streamName: String? = null

                    // If streams are already attached in the match object
                    val streamsArray = json.getAsJsonArray("streams")
                    if (streamsArray != null && streamsArray.size() > 0) {
                        val firstStream = streamsArray.get(0).asJsonObject
                        val streamId = firstStream.get("stream_id")?.asInt
                        val realUrl = firstStream.get("real_url")?.asString

                        streamName = firstStream.get("name")?.asString ?: "Señal 1"

                        // Try extracting direct HLS from real_url or load-playlist endpoint
                        if (streamId != null) {
                            directHlsUrl = "https://chatgpt.hereisman.net/playlist/$streamId/load-playlist"
                        } else if (realUrl != null) {
                            directHlsUrl = resolveHlsFromEmbed(realUrl)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        onF1EventDetected?.invoke(
                            F1LiveStreamEvent(
                                title = title,
                                isLive = isLive,
                                startTime = startsAt,
                                streamUrl = directHlsUrl,
                                streamName = streamName
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        @JavascriptInterface
        fun postNoF1() {
            mainHandler.post {
                onNoEventDetected?.invoke()
            }
        }
    }

    private fun resolveHlsFromEmbed(embedUrl: String): String? {
        return try {
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://golygoals.com/")
                .build()

            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: return null

            // Look for SOURCE = "https://..."
            val matcher = Pattern.compile("SOURCE\\s*=\\s*[\"']([^\"']+)[\"']").matcher(html)
            if (matcher.find()) {
                val found = matcher.group(1)?.replace("\\/", "/")
                return found
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
