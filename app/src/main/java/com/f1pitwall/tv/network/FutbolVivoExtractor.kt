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

/**
 * Reads the public F1 event listings exposed by Fútbol Libre/FútbolVivo.
 *
 * This class only discovers event titles, status and start time. It deliberately
 * does not extract hidden media URLs or bypass the provider's own player/access rules.
 */
data class F1LiveStreamEvent(
    val title: String,
    val isLive: Boolean,
    val startTime: String?,
    val eventUrl: String?,
    val alternativeUrls: List<String> = emptyList()
)

class FutbolVivoExtractor(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    private var webView: WebView? = null
    private var destroyed = false
    private var timeoutRunnable: Runnable? = null

    var onF1EventDetected: ((F1LiveStreamEvent) -> Unit)? = null
    var onNoEventDetected: (() -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun startExtraction(sourceUrl: String = "https://futbolvivo.tv/") {
        mainHandler.post {
            destroyed = false
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }

            if (!sourceUrl.contains("futbolvivo.tv", ignoreCase = true)) {
                onNoEventDetected?.invoke()
                return@post
            }

            // Fallback timeout in case page fails to load or no events within 15 seconds
            val timeout = Runnable {
                if (!destroyed) {
                    onNoEventDetected?.invoke()
                }
            }
            timeoutRunnable = timeout
            mainHandler.postDelayed(timeout, 15000)

            try {
                val scanner = webView ?: WebView(context).also { created ->
                    created.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        javaScriptCanOpenWindowsAutomatically = false
                        mediaPlaybackRequiresUserGesture = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    }
                    created.addJavascriptInterface(Bridge(), "AndroidBridge")
                    created.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url?.contains("futbolvivo.tv", ignoreCase = true) == true) {
                                injectWatcher()
                            }
                        }

                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                            onNoEventDetected?.invoke()
                        }
                    }
                    webView = created
                }
                scanner.loadUrl(sourceUrl)
            } catch (_: Exception) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                onNoEventDetected?.invoke()
            }
        }
    }

    private fun injectWatcher() {
        val js = """
            (function () {
                if (window.__f1PitwallWatcher) {
                    try { clearInterval(window.__f1PitwallWatcher); } catch (e) {}
                }

                let attempts = 0;
                let lastEventKey = "";
                let noEventSent = false;

                function textOf(value) {
                    if (value == null) return "";
                    if (typeof value === "string" || typeof value === "number") return String(value);
                    if (typeof value === "object") {
                        return String(value.name || value.title || value.label || "");
                    }
                    return "";
                }

                function eventTitle(item) {
                    if (typeof item === "string") return item;
                    return [
                        textOf(item.title),
                        textOf(item.name),
                        textOf(item.match_name),
                        textOf(item.event_name),
                        textOf(item.competition),
                        textOf(item.league),
                        textOf(item.category)
                    ].filter(Boolean).join(" ").replace(/\s+/g, " ").trim();
                }

                function isFormulaOne(title) {
                    const value = title.toLowerCase();
                    const isF1 = /\bf1\b|formula\s*1|formula\s*one|gran\s*premio|grand\s*prix|motorsport.*f1|racing.*f1|las\s*vegas\s*gp/.test(value);
                    const isOtherSeries = /f1\s*academy|formula\s*2|formula\s*3/.test(value);
                    return isF1 && !isOtherSeries;
                }

                function isLive(item, extraText) {
                    const state = String(item.status || item.state || "").toLowerCase();
                    return item.live === true || item.is_live === true || item.isLive === true ||
                        /live|en vivo|en directo|playing/.test(state + " " + (extraText || "").toLowerCase());
                }

                function startOf(item) {
                    return item.begin_at || item.starts_at || item.start_at ||
                        item.start_time || item.scheduled_at || item.date_start || item.date || null;
                }

                function scan() {
                    attempts++;
                    const candidates = [];
                    const sources = [
                        window.seAllMatches,
                        window.seLatinMatches,
                        window.seKoraMatches,
                        window.seMatches,
                        window.allMatches,
                        window.matches
                    ];

                    sources.forEach(source => {
                        if (Array.isArray(source)) {
                            source.forEach(item => {
                                if (item && typeof item === "object") {
                                    candidates.push({ item: item, extra: "" });
                                } else if (typeof item === "string") {
                                    candidates.push({ item: { title: item }, extra: item });
                                }
                            });
                        }
                    });

                    // Fallback to the visible match cards used by the site's schedule.
                    document.querySelectorAll(
                        "#GelecekMaclar .se-row a.se-row-link, .se-row a.se-row-link, a.se-row-link, [data-match]"
                    ).forEach(node => {
                        const card = node.closest(".se-row") || node.parentElement || node;
                        const label = (card.innerText || node.innerText || node.textContent || "").trim();
                        if (label) {
                            candidates.push({
                                item: {
                                    title: label,
                                    href: node.href || node.getAttribute("href"),
                                    begin_at: node.getAttribute("data-start") || node.getAttribute("datetime"),
                                    live: /live|en vivo|en directo/i.test(label)
                                },
                                extra: label
                            });
                        }
                    });

                    // Generic fallback for layout changes: inspect all visible links.
                    document.querySelectorAll("a[href]").forEach(node => {
                        const label = (node.innerText || node.textContent || "").trim().replace(/\s+/g, " ");
                        if (label && isFormulaOne(label)) {
                            candidates.push({
                                item: {
                                    title: label,
                                    href: node.href || node.getAttribute("href"),
                                    live: /live|en vivo|en directo/i.test(label)
                                },
                                extra: label
                            });
                        }
                    });

                    // Last fallback: inspect visible text lines if the provider changed its card markup.
                    const bodyLines = (document.body?.innerText || "").split(/\n+/);
                    bodyLines.forEach(line => {
                        const label = line.trim().replace(/\s+/g, " ");
                        if (label && isFormulaOne(label)) {
                            candidates.push({ item: { title: label }, extra: label });
                        }
                    });

                    const f1Matches = candidates.map(entry => {
                        const item = entry.item;
                        const title = eventTitle(item) || entry.extra;
                        return {
                            title: title,
                            live: isLive(item, entry.extra),
                            startTime: startOf(item),
                            eventUrl: (() => {
                                let candidate = item.url || item.href || item.link;
                                if (!candidate) {
                                    if (typeof window.seLatinStreamUrl === "function" && window.seLatinMatches && window.seLatinMatches.indexOf(item) !== -1) {
                                        candidate = window.seLatinStreamUrl(item);
                                    } else if (typeof window.seKoraStreamUrl === "function" && window.seKoraMatches && window.seKoraMatches.indexOf(item) !== -1) {
                                        candidate = window.seKoraStreamUrl(item);
                                    } else if (typeof window.seStreamUrl === "function") {
                                        candidate = window.seStreamUrl(item);
                                    }
                                }
                                return typeof candidate === "string" ? candidate : null;
                            })()
                        };
                    }).filter(event => event.title && isFormulaOne(event.title));

                    f1Matches.sort((a, b) => {
                        if (a.live !== b.live) return a.live ? -1 : 1;
                        const aTime = a.startTime ? Date.parse(a.startTime) : Number.MAX_SAFE_INTEGER;
                        const bTime = b.startTime ? Date.parse(b.startTime) : Number.MAX_SAFE_INTEGER;
                        return aTime - bTime;
                    });

                    if (f1Matches.length) {
                        noEventSent = false;
                        const target = f1Matches[0];
                        const uniqueUrls = [];
                        f1Matches.forEach(m => {
                            if (m.eventUrl && uniqueUrls.indexOf(m.eventUrl) === -1) {
                                uniqueUrls.push(m.eventUrl);
                            }
                        });
                        const key = [target.title, target.live, target.startTime || "", target.eventUrl || "", uniqueUrls.join(",")].join("|");
                        if (key !== lastEventKey && window.AndroidBridge) {
                            lastEventKey = key;
                            const payload = {
                                title: target.title,
                                live: target.live,
                                startTime: target.startTime,
                                eventUrl: target.eventUrl,
                                alternativeUrls: uniqueUrls
                            };
                            window.AndroidBridge.postF1Event(JSON.stringify(payload));
                        }
                    } else if (attempts >= 8 && !noEventSent && window.AndroidBridge) {
                        noEventSent = true;
                        window.AndroidBridge.postNoF1();
                    }
                }

                scan();
                window.__f1PitwallWatcher = setInterval(scan, 2500);
            })();
        """.trimIndent()

        webView?.evaluateJavascript(js, null)
    }

    inner class Bridge {
        @JavascriptInterface
        fun postF1Event(json: String) {
            mainHandler.post {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                try {
                    val item = gson.fromJson(json, JsonObject::class.java)
                    val title = item.get("title")?.takeIf { !it.isJsonNull }?.asString
                        ?: "Evento de Fórmula 1"
                    val live = item.get("live")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                    val start = item.get("startTime")?.takeIf { !it.isJsonNull }?.asString
                    val url = item.get("eventUrl")?.takeIf { !it.isJsonNull }?.asString
                    val altList = mutableListOf<String>()
                    val altArray = item.getAsJsonArray("alternativeUrls")
                    if (altArray != null) {
                        for (elem in altArray) {
                            if (!elem.isJsonNull && !elem.asString.isNullOrBlank()) {
                                altList.add(elem.asString)
                            }
                        }
                    }
                    onF1EventDetected?.invoke(F1LiveStreamEvent(title, live, start, url, altList))
                } catch (_: Exception) {
                    onNoEventDetected?.invoke()
                }
            }
        }

        @JavascriptInterface
        fun postNoF1() {
            mainHandler.post {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                onNoEventDetected?.invoke()
            }
        }
    }

    fun stop() {
        mainHandler.post {
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
        }
    }

    fun onPause() {
        webView?.onPause()
    }

    fun onResume() {
        webView?.onResume()
    }

    fun destroy() {
        destroyed = true
        mainHandler.post {
            timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            webView?.let {
                it.stopLoading()
                it.removeJavascriptInterface("AndroidBridge")
                it.loadUrl("about:blank")
                it.destroy()
            }
            webView = null
        }
    }
}
