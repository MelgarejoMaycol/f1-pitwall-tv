package com.f1pitwall.tv.ui.pitwall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.BroadcastSource
import com.f1pitwall.tv.core.storage.PreferencesManager
import com.f1pitwall.tv.core.util.BroadcastDetectorUtils
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.*
import com.f1pitwall.tv.network.FlxExtractor
import com.f1pitwall.tv.network.FutbolLibreExtractor
import com.f1pitwall.tv.network.FutbolVivoExtractor
import com.f1pitwall.tv.ui.LeaderboardAdapter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

class PitWallViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val scope: LifecycleCoroutineScope,
    private val onNavigateToSettings: () -> Unit,
    private val defaultWebSourceUrl: String? = null
) {
    private val context: Context = rootView.context
    private val preferencesManager = PreferencesManager(context)
    private val futbolVivoExtractor = FutbolVivoExtractor(context)
    private val flxExtractor = FlxExtractor(scope)
    private val futbolLibreExtractor = FutbolLibreExtractor(scope)

    // Video Player & Standby
    private val playerSurface: FrameLayout = rootView.findViewById(R.id.framePitWallSurface)
    private val playerView: PlayerView = rootView.findViewById(R.id.playerView)
    private val webVideoView: WebView = rootView.findViewById(R.id.webVideoView)
    private val layoutPlayerStandby: View = rootView.findViewById(R.id.layoutPlayerStandby)
    private val tvStandbyStatusBadge: TextView = rootView.findViewById(R.id.tvStandbyStatusBadge)
    private val tvStandbyTitle: TextView = rootView.findViewById(R.id.tvStandbyTitle)
    private val tvStandbySubtitle: TextView = rootView.findViewById(R.id.tvStandbySubtitle)
    private val tvStandbyCircuitSpecs: TextView? = rootView.findViewById(R.id.tvStandbyCircuitSpecs)
    private val btnStandbyPreview: Button = rootView.findViewById(R.id.btnStandbyPreview)
    private val btnStandbyRecap: Button = rootView.findViewById(R.id.btnStandbyRecap)
    private val btnStandbyOpenWeb: Button = rootView.findViewById(R.id.btnStandbyOpenWeb)
    private val btnStandbyStopVideo: Button = rootView.findViewById(R.id.btnStandbyStopVideo)

    // Alert Banner & Content Hub Bar
    private val layoutLiveSessionAlert: View = rootView.findViewById(R.id.layoutLiveSessionAlert)
    private val tvLiveSessionAlertTitle: TextView = rootView.findViewById(R.id.tvLiveSessionAlertTitle)
    private val btnLiveSessionAction: Button = rootView.findViewById(R.id.btnLiveSessionAction)
    private val layoutServerSelector: View = rootView.findViewById(R.id.layoutServerSelector)
    private val scrollServerSelector: HorizontalScrollView? = rootView.findViewById(R.id.scrollServerSelector)
    private val btnSourceBadgePitWall: Button? = rootView.findViewById(R.id.btnSourceBadgePitWall)
    private val btnSessionSelector: Button? = rootView.findViewById(R.id.btnSessionSelector)
    private val llDynamicChannelsContainer: LinearLayout? = rootView.findViewById(R.id.llDynamicChannelsContainer)
    private val dynamicChannelButtons = mutableListOf<Button>()
    private val serverButtons: List<Button> get() = dynamicChannelButtons
    private var selectedServerIndex: Int = 0
    private var activeSpanishBroadcasts: List<SpanishBroadcast> = emptyList()
    private var selectedBroadcastIndex: Int = 0
    private var currentLoadedSource: BroadcastSource? = null
    private var futbolLibreEvents: List<FutbolLibreEvent> = emptyList()
    private var selectedFutbolLibreEventIndex: Int = 0
    private var futbolLibreSourcesJob: Job? = null
    private var pendingFutbolLibreSourceId: String? = null
    private val btnContentPreview: Button = rootView.findViewById(R.id.btnContentPreview)
    private val btnContentRecap: Button = rootView.findViewById(R.id.btnContentRecap)
    private val btnContentBoxes: Button = rootView.findViewById(R.id.btnContentBoxes)

    // Radio
    private val tvRadioTitle: TextView = rootView.findViewById(R.id.tvRadioTitle)
    private val btnPlayRadio: Button = rootView.findViewById(R.id.btnPlayRadio)
    private val btnRadioVer: Button = rootView.findViewById(R.id.btnRadioVer)
    private val btnRadioNor: Button = rootView.findViewById(R.id.btnRadioNor)
    private val btnRadioLec: Button = rootView.findViewById(R.id.btnRadioLec)
    private val btnRadioHam: Button = rootView.findViewById(R.id.btnRadioHam)
    private val btnRadioRus: Button = rootView.findViewById(R.id.btnRadioRus)
    private val btnRadioPia: Button = rootView.findViewById(R.id.btnRadioPia)
    private val btnRadioAlo: Button = rootView.findViewById(R.id.btnRadioAlo)

    // Header Status & Live Refresh
    private val tvSessionTitle: TextView = rootView.findViewById(R.id.tvSessionTitle)
    private val btnRefreshLive: Button? = rootView.findViewById(R.id.btnRefreshLive)

    // Metrics Switcher & Containers
    private val btnMetricsTiming: Button = rootView.findViewById(R.id.btnMetricsTiming)
    private val btnMetricsTelemetry: Button = rootView.findViewById(R.id.btnMetricsTelemetry)
    private val rvLeaderboard: RecyclerView = rootView.findViewById(R.id.rvLeaderboard)
    private val layoutTelemetryPanel: View = rootView.findViewById(R.id.layoutTelemetryPanel)

    // Telemetry Gauges
    private val tvTelemetryDriverHeader: TextView = rootView.findViewById(R.id.tvTelemetryDriverHeader)
    private val tvTelemetryDrs: TextView = rootView.findViewById(R.id.tvTelemetryDrs)
    private val tvTelemetrySpeed: TextView = rootView.findViewById(R.id.tvTelemetrySpeed)
    private val tvTelemetryGear: TextView = rootView.findViewById(R.id.tvTelemetryGear)
    private val tvTelemetryRpm: TextView = rootView.findViewById(R.id.tvTelemetryRpm)
    private val pbTelemetryRpm: ProgressBar = rootView.findViewById(R.id.pbTelemetryRpm)
    private val tvTelemetryThrottle: TextView = rootView.findViewById(R.id.tvTelemetryThrottle)
    private val pbTelemetryThrottle: ProgressBar = rootView.findViewById(R.id.pbTelemetryThrottle)
    private val tvTelemetryBrake: TextView = rootView.findViewById(R.id.tvTelemetryBrake)
    private val pbTelemetryBrake: ProgressBar = rootView.findViewById(R.id.pbTelemetryBrake)
    private val btnTelemetryDemoToggle: Button = rootView.findViewById(R.id.btnTelemetryDemoToggle)

    // State & Players
    private var exoPlayer: ExoPlayer? = null
    private var radioPlayer: ExoPlayer? = null
    private var latestRadioUrl: String? = null
    private var currentSessionKey: Long? = null
    private var isLiveSessionActive: Boolean = false
    private var isUpcomingSession: Boolean = false
    private var currentScheduleTitle: String = "Calendario F1 en preparación"
    private var futbolVivoStatusText: String = "FÚTBOL VIVO • BUSCANDO CANALES EN ESPAÑOL"
    private var discoveredFutbolEventUrl: String? = null
    private var discoveredAlternativeUrls: List<String> = emptyList()
    private var discoveredFutbolEventTitle: String = "Evento de Fórmula 1"
    private var currentWebUrl: String? = null
    private var customFullScreenView: View? = null
    private var customFullScreenCallback: WebChromeClient.CustomViewCallback? = null

    val leaderboardAdapter = LeaderboardAdapter { item ->
        tvTelemetryDriverHeader.text = "#${item.driverNumber} ${item.fullName} • ${item.teamName}"
        btnMetricsTelemetry.performClick()
        loadDriverTelemetry(item.driverNumber)
    }

    init {
        setupWebView()
        initPlayers()
        setupListeners()
        setupLeaderboardView()
        initBroadcastSource()
    }

    /**
     * Loads a user-selected website as a top-level page. The provider's own
     * JavaScript, cookies and HTML5 player are handled by Android WebView.
     * Server-side frame and access policies are not bypassed.
     */
    private fun setupWebView() {
        webVideoView.apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                userAgentString = userAgentString
                    .replace("; wv", "")
                    .replace(" Mobile", "")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(webVideoView, true)
                }
            }

            addJavascriptInterface(PitWallBridge(), "AndroidPitWallBridge")

            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                    triggerPlayerTap()
                    true
                } else {
                    false
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString()?.lowercase() ?: return null
                    val isAd = listOf(
                        "acscdn.com", "al5sm.com", "deltalcherup.com", "histats.com",
                        "popads", "adsterra", "doubleclick", "googlesyndication",
                        "googleads", "adnxs", "propellerads", "trafficjunky",
                        "exoclick", "juicyads", "syndication", "adsystem", "adservice"
                    ).any { url.contains(it) }
                    if (isAd) {
                        return WebResourceResponse("text/javascript", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase() ?: ""
                    val host = uri.host?.lowercase() ?: ""
                    if (scheme != "http" && scheme != "https") return true

                    val isStreamHost = listOf(
                        "vivogoal", "futbol", "golygoals", "kora-plus",
                        "sport", "streameast", "strm-api", "cdn",
                        "flive", "taifood", "videocdn", "weellz"
                    ).any { host.contains(it) }

                    if (!isStreamHost && request.isForMainFrame) {
                        return true
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        val reason = error?.description?.toString() ?: "error de red"
                        Toast.makeText(context, "No se pudo cargar la página: " + reason, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: android.webkit.WebResourceResponse?
                ) {
                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                        Toast.makeText(
                            context,
                            "El sitio respondió HTTP " + errorResponse?.statusCode,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val sessionPrefix = if (isLiveSessionActive) "🔴 EN VIVO" else "⏳ PRÓXIMA SESIÓN"
                    tvLiveSessionAlertTitle.text = "$sessionPrefix: $currentScheduleTitle"
                    btnLiveSessionAction.text = "🔄 Refrescar"

                    val cleanJs = """
                        (function() {
                            try { window.open = function() { return null; }; } catch(e) {}
                            function cleanAndMaximize() {
                                const style = document.createElement('style');
                                style.id = '__f1_tv_clean';
                                style.innerHTML = `
                                    html, body {
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        width: 100vw !important;
                                        height: 100vh !important;
                                        background: #000000 !important;
                                        overflow: hidden !important;
                                    }
                                    header, footer, nav, .se-match-head, .se-teams, .se-match-title,
                                    .se-tabs, .navbar, .se-footer, .nav-mobile-only, .se-home-link,
                                    #streamTabs, #matchTitle, #matchTeams, .se-sport-pill,
                                    .site-header, .site-footer, .breadcrumb, .match-info,
                                    #cq-back-to-top, .cq-back-to-top,
                                    .histats, [id*="histats"], [class*="histats"], [id*="aclib"],
                                    .homepage-faq-title, #cq-branding-layout-style {
                                        display: none !important;
                                    }
                                    .se-hero, .se-container, .se-match-card, .se-player, .se-player-wrap, #playerWrap, #playerContainer,
                                    .aspect-video, [class*="aspect-video"], #player, [id*="player"] {
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        border: none !important;
                                        background: #000000 !important;
                                    }
                                    .player-panel {
                                        display: flex !important;
                                        flex-direction: column !important;
                                        position: fixed !important;
                                        top: 0 !important;
                                        left: 0 !important;
                                        width: 100vw !important;
                                        height: 100vh !important;
                                        max-width: 100vw !important;
                                        max-height: 100vh !important;
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        border: none !important;
                                        background: #000000 !important;
                                        z-index: 999999 !important;
                                    }
                                    .player-toolbar, .server-switcher, .event-source-switcher {
                                        display: flex !important;
                                        visibility: visible !important;
                                        opacity: 1 !important;
                                        flex-shrink: 0 !important;
                                        background: rgba(10, 15, 20, 0.90) !important;
                                        padding: 6px 12px !important;
                                        z-index: 1000001 !important;
                                        align-items: center !important;
                                        overflow-x: auto !important;
                                    }
                                    .player-source-state {
                                        display: flex !important;
                                        visibility: visible !important;
                                        align-items: center !important;
                                        margin-right: 12px !important;
                                    }
                                    button.event-source-choice, .source-pill {
                                        display: inline-flex !important;
                                        visibility: visible !important;
                                        opacity: 1 !important;
                                        cursor: pointer !important;
                                    }
                                    .player-box {
                                        flex: 1 1 auto !important;
                                        position: relative !important;
                                        width: 100% !important;
                                        height: 100% !important;
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        background: #000000 !important;
                                    }
                                    #source-frame, #chinaq-video, iframe {
                                        position: absolute !important;
                                        top: 0 !important;
                                        left: 0 !important;
                                        width: 100% !important;
                                        height: 100% !important;
                                        border: none !important;
                                        background: #000 !important;
                                    }
                                `;
                                if (!document.getElementById('__f1_tv_clean')) {
                                    document.head.appendChild(style);
                                }

                                document.querySelectorAll('video').forEach(v => {
                                    v.muted = false;
                                    v.play().catch(() => {
                                        v.muted = true;
                                        v.play().catch(() => {});
                                    });
                                });

                                document.querySelectorAll('div').forEach(d => {
                                    if (d.id !== 'playerWrap' && d.id !== 'playerContainer' && !d.classList.contains('se-player-wrap') && !d.classList.contains('se-player')) {
                                        const s = window.getComputedStyle(d);
                                        if ((s.position === 'fixed' || s.position === 'absolute') && parseInt(s.zIndex) > 999999) {
                                            d.remove();
                                        }
                                    }
                                });
                            }

                            function reportServers() {
                                try {
                                    if (window.location.hostname.indexOf('futbolvivo') === -1 && window.location.hostname.indexOf('futbollibre') === -1) return;
                                    if (window.currentMatch) {
                                        console.log("F1_DEBUG: currentMatch=" + JSON.stringify({
                                            id: window.currentMatch.id,
                                            name: window.currentMatch.name,
                                            streams: window.currentMatch.streams,
                                            channels: window.currentMatch.channels
                                        }));
                                    }
                                    let servers = [];
                                    let active = 0;
                                    if (window.currentMatch && Array.isArray(window.currentMatch.channels) && window.currentMatch.channels.length > 0) {
                                        servers = window.currentMatch.channels.map((c, i) => c.server_name || ('Opción ' + (i + 1)));
                                        active = typeof window.activeServer !== 'undefined' ? window.activeServer : 0;
                                    } else if (window.currentMatch && Array.isArray(window.currentMatch.streams) && window.currentMatch.streams.length > 0) {
                                        servers = window.currentMatch.streams.map((s, i) => s.language || s.name || ('Opción ' + (i + 1)));
                                        active = typeof window.activeStream !== 'undefined' ? window.activeStream : 0;
                                    } else {
                                        const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                                        if (tabs && tabs.length > 0) {
                                            tabs.forEach((t, i) => {
                                                servers.push(t.innerText.trim() || ('Opción ' + (i + 1)));
                                                if (t.classList.contains('active')) active = i;
                                            });
                                        }
                                    }
                                    if (servers.length > 0 && window.AndroidPitWallBridge) {
                                        window.AndroidPitWallBridge.onServersAvailable(servers.length, active, JSON.stringify(servers));
                                    }
                                } catch(e) {}
                            }

                            function scanSpanishBroadcasts() {
                                try {
                                    var host = (window.location.hostname || '').toLowerCase();
                                    var pathname = window.location.pathname || '';
                                    var isFutbolLibre = host === 'futbol-libre.app' || host.endsWith('.futbol-libre.app');
                                    if (isFutbolLibre) {
                                        if (pathname.indexOf('/match/') !== -1) {
                                            var libreBroadcasts = [];
                                            var sourceButtons = Array.prototype.slice.call(
                                                document.querySelectorAll('button.event-source-choice[data-source]')
                                            );
                                            sourceButtons.forEach(function(button, idx) {
                                                var sourceId = (button.getAttribute('data-source') || '').trim();
                                                if (!/^[A-Za-z0-9_-]{1,32}$/.test(sourceId)) return;
                                                var providerNode = button.querySelector('.source-provider');
                                                var channelNode = button.querySelector('.source-channel');
                                                var provider = (providerNode && providerNode.textContent || '').trim() || ('Fuente ' + (idx + 1));
                                                var channel = (channelNode && channelNode.textContent || '').trim() || provider;
                                                var pageUrl = window.location.href.split('#')[0];
                                                var targetUrl = pageUrl + '#source=' + encodeURIComponent(sourceId);
                                                var clickJs = "(function(){var id=" + JSON.stringify(sourceId) + ";" +
                                                    "function tryClick(){var b=document.querySelector('button.event-source-choice[data-source=\"'+id+'\"]');if(b){b.click();return true;}return false;}" +
                                                    "if(!tryClick()){var attempts=0;var timer=setInterval(function(){attempts++;if(tryClick()||attempts>=25){clearInterval(timer);}},200);}})();";
                                                libreBroadcasts.push({
                                                    index: idx + 1,
                                                    network: channel,
                                                    language: '',
                                                    serverName: provider,
                                                    originalTitle: provider + ' · ' + channel,
                                                    streamUrl: targetUrl,
                                                    clickActionJs: clickJs,
                                                    sourceId: sourceId,
                                                    customFormattedTitle: 'Transmisión ' + (idx + 1) + ' — ' + channel,
                                                    customButtonLabel: 'T' + (idx + 1) + ': ' + channel
                                                });
                                            });

                                            if (libreBroadcasts.length > 0 &&
                                                window.AndroidPitWallBridge &&
                                                typeof window.AndroidPitWallBridge.onSpanishBroadcastsDetected === 'function') {
                                                window.AndroidPitWallBridge.onSpanishBroadcastsDetected(JSON.stringify(libreBroadcasts));
                                            }
                                            return;
                                        }

                                        if (pathname.indexOf('/agenda') !== -1) {
                                            var agendaEvents = [];
                                            var eventDivs = Array.prototype.slice.call(document.querySelectorAll('div.source-agenda-event'));
                                            eventDivs.forEach(function(div, evIdx) {
                                                var btn = div.querySelector('button.source-agenda-eventbtn');
                                                if (!btn) return;
                                                var timeSpan = btn.querySelector('span.source-agenda-time');
                                                var timeRaw = (timeSpan && timeSpan.textContent || '').trim();
                                                var compSpan = btn.querySelector('span.source-agenda-competition');
                                                var compRaw = (compSpan && compSpan.textContent || '').trim();
                                                var textSpan = btn.querySelector('span.source-agenda-eventtext');
                                                var textRaw = (textSpan && textSpan.textContent || '').trim();
                                                var combined = (compRaw ? (compRaw + ' ' + textRaw) : textRaw).replace(/\s+/g, ' ').trim();

                                                var t = combined.toLowerCase();
                                                var hasF1 = /\bf1\b|f[oó]rmula\s*(?:1|one)|f1\s*academy|motorsport.*f1/i.test(t);
                                                var isOther = /\bf2\b|\bf3\b|formula\s*[23]|motogp|indycar|nascar/i.test(t);
                                                if (isOther && !hasF1) return;
                                                if (!hasF1) {
                                                    var hasGp = /gran\s*premio|grand\s*prix|\bgp\b/i.test(t);
                                                    var hasSess = /pr[aá]cticas?\s*libres?|pr[aá]ctica\s*[123]|clasificaci[oó]n|carrera/i.test(t);
                                                    if (!hasGp || !hasSess) return;
                                                }

                                                var instant = div.getAttribute('data-source-instant') || '';
                                                var srcLinks = Array.prototype.slice.call(div.querySelectorAll('a.agenda-source-button'));
                                                var openMatch = div.querySelector('a.agenda-open-match');
                                                var href = (openMatch && openMatch.getAttribute('href') || '').trim();
                                                if (!href && srcLinks.length > 0) {
                                                    href = (srcLinks[0].getAttribute('href') || '').split('#')[0].trim();
                                                }
                                                if (!href) return;
                                                var matchUrl = href.indexOf('http') === 0 ? href : ('https://futbol-libre.app' + href);
                                                var slug = href.replace('/match/', '').split('/')[0].split('#')[0];

                                                var broadcastList = [];
                                                srcLinks.forEach(function(anchor, sIdx) {
                                                    var sp = anchor.querySelector('span');
                                                    var sm = anchor.querySelector('small');
                                                    var prov = (sp && sp.textContent || '').trim() || ('Fuente ' + (sIdx + 1));
                                                    var chan = (sm && sm.textContent || '').trim() || prov;
                                                    var sHref = (anchor.getAttribute('href') || '').trim();
                                                    var srcMatch = sHref.match(/[?#&]source=([A-Za-z0-9_-]+)/);
                                                    var sId = srcMatch ? srcMatch[1] : null;
                                                    var sTarget = sHref.indexOf('http') === 0 ? sHref : ('https://futbol-libre.app' + sHref);
                                                    var sClick = sId ? ("(function(){var id=" + JSON.stringify(sId) + ";" +
                                                        "function tryClick(){var b=document.querySelector('button.event-source-choice[data-source=\"'+id+'\"]');if(b){b.click();return true;}return false;}" +
                                                        "if(!tryClick()){var attempts=0;var timer=setInterval(function(){attempts++;if(tryClick()||attempts>=25){clearInterval(timer);}},200);}})();") : null;

                                                    broadcastList.push({
                                                        index: sIdx + 1,
                                                        network: chan,
                                                        language: '',
                                                        serverName: prov,
                                                        originalTitle: prov + ' · ' + chan,
                                                        streamUrl: sTarget,
                                                        clickActionJs: sClick,
                                                        sourceId: sId,
                                                        customFormattedTitle: 'Transmisión ' + (sIdx + 1) + ' — ' + chan,
                                                        customButtonLabel: 'T' + (sIdx + 1) + ': ' + chan
                                                    });
                                                });

                                                agendaEvents.push({
                                                    id: slug || ('event_' + (evIdx + 1)),
                                                    title: combined,
                                                    sessionName: combined.split(':').pop().trim() || combined,
                                                    gpName: 'GP de Las Vegas',
                                                    utcInstant: instant,
                                                    colombiaTime: timeRaw,
                                                    matchPath: href,
                                                    fullMatchUrl: matchUrl,
                                                    sources: broadcastList
                                                });
                                            });

                                            if (agendaEvents.length > 0 &&
                                                window.AndroidPitWallBridge &&
                                                typeof window.AndroidPitWallBridge.onFutbolLibreAgendaDetected === 'function') {
                                                window.AndroidPitWallBridge.onFutbolLibreAgendaDetected(JSON.stringify(agendaEvents));
                                            }
                                            return;
                                        }
                                    }

                                    if (host.indexOf('futbolvivo') === -1 && host.indexOf('futbollibre') === -1) return;
                                    let broadcasts = [];
                                    let counter = 1;
                                    function isEsp(txt) {
                                        return /(?:\b(?:español|spanish|castellano)\b|\[(?:spanish|español|esp|es)\]|\((?:spanish|español|esp|es)\)|\b(?:esp|es)\b)/i.test(txt || '');
                                    }
                                    function detectNet(txt) {
                                        const l = (txt || '').toLowerCase();
                                        if (l.indexOf('dazn') !== -1) return 'DAZN';
                                        if (l.indexOf('fox sports') !== -1 || l.indexOf('foxsports') !== -1) return 'FOX Sports';
                                        if (l.indexOf('fox') !== -1) return 'FOX Sports';
                                        if (l.indexOf('espn premium') !== -1) return 'ESPN Premium';
                                        if (l.indexOf('espn 1') !== -1 || l.indexOf('espn1') !== -1) return 'ESPN 1';
                                        if (l.indexOf('espn 2') !== -1 || l.indexOf('espn2') !== -1) return 'ESPN 2';
                                        if (l.indexOf('espn 3') !== -1 || l.indexOf('espn3') !== -1) return 'ESPN 3';
                                        if (l.indexOf('espn') !== -1) return 'ESPN';
                                        if (l.indexOf('movistar') !== -1) return 'Movistar';
                                        if (l.indexOf('star+') !== -1) return 'Star+';
                                        return null;
                                    }
                                    if (window.currentMatch && Array.isArray(window.currentMatch.channels)) {
                                        window.currentMatch.channels.forEach((ch, idx) => {
                                            const combined = [ch.name, ch.channel, ch.server_name, ch.language].filter(Boolean).join(' ');
                                            if (isEsp(combined)) {
                                                broadcasts.push({
                                                    index: counter++,
                                                    network: detectNet(combined),
                                                    language: 'Español',
                                                    serverName: ch.server_name || ('Server ' + (idx + 1)),
                                                    originalTitle: combined,
                                                    streamUrl: ch.stream_url || ch.url || null,
                                                    clickActionJs: '(function(){ if(window.updatePlayer && window.currentMatch && window.currentMatch.channels[' + idx + ']) window.updatePlayer(window.currentMatch.channels[' + idx + ']); })()'
                                                });
                                            }
                                        });
                                    }
                                    if (broadcasts.length === 0 && window.currentMatch && Array.isArray(window.currentMatch.streams)) {
                                        window.currentMatch.streams.forEach((st, idx) => {
                                            const combined = [st.name, st.language].filter(Boolean).join(' ');
                                            if (isEsp(combined)) {
                                                broadcasts.push({
                                                    index: counter++,
                                                    network: detectNet(combined),
                                                    language: 'Español',
                                                    serverName: st.name || ('Server ' + (idx + 1)),
                                                    originalTitle: combined,
                                                    streamUrl: st.url || null,
                                                    clickActionJs: '(function(){ window.activeStream = ' + idx + '; if(window.renderPlayer) window.renderPlayer(); })()'
                                                });
                                            }
                                        });
                                    }
                                    if (broadcasts.length === 0) {
                                        const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                                        tabs.forEach((t, idx) => {
                                            const label = (t.innerText || '').trim();
                                            if (isEsp(label)) {
                                                broadcasts.push({
                                                    index: counter++,
                                                    network: detectNet(label),
                                                    language: 'Español',
                                                    serverName: 'Server ' + (idx + 1),
                                                    originalTitle: label,
                                                    clickActionJs: 'document.querySelectorAll("#streamTabs .se-tab-btn")[' + idx + '].click()'
                                                });
                                            }
                                        });
                                    }

                                    if (broadcasts.length > 0 && window.AndroidPitWallBridge &&
                                        typeof window.AndroidPitWallBridge.onSpanishBroadcastsDetected === 'function') {
                                        window.AndroidPitWallBridge.onSpanishBroadcastsDetected(JSON.stringify(broadcasts));
                                    } else if (broadcasts.length === 0 && window.AndroidPitWallBridge &&
                                        typeof window.AndroidPitWallBridge.onNoSpanishBroadcastsDetected === 'function') {
                                        window.AndroidPitWallBridge.onNoSpanishBroadcastsDetected();
                                    }
                                } catch(e) {}
                            }

                            cleanAndMaximize();
                            reportServers();
                            scanSpanishBroadcasts();
                            setTimeout(function() { cleanAndMaximize(); reportServers(); scanSpanishBroadcasts(); }, 600);
                            setTimeout(function() { cleanAndMaximize(); reportServers(); scanSpanishBroadcasts(); }, 1500);
                            setTimeout(function() { cleanAndMaximize(); reportServers(); scanSpanishBroadcasts(); }, 3000);
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(cleanJs, null)

                    if (currentLoadedSource == BroadcastSource.FUTBOL_LIBRE) {
                        val sourceId = pendingFutbolLibreSourceId ?: activeSpanishBroadcasts.getOrNull(selectedBroadcastIndex)?.sourceId
                        if (!sourceId.isNullOrBlank()) {
                            val ensureJs = """
                                (function() {
                                    var id = '$sourceId';
                                    function ensureActive() {
                                        var b = document.querySelector('button.event-source-choice[data-source="' + id + '"]');
                                        if (b) {
                                            if (!b.classList.contains('selected') && !b.classList.contains('active-source')) {
                                                b.click();
                                            }
                                            return true;
                                        }
                                        return false;
                                    }
                                    if (!ensureActive()) {
                                        var count = 0;
                                        var t = setInterval(function() {
                                            count++;
                                            if (ensureActive() || count >= 20) {
                                                clearInterval(t);
                                            }
                                        }, 250);
                                    }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(ensureJs, null)
                        }
                        pendingFutbolLibreSourceId = null
                    }

                    view?.postDelayed({ triggerPlayerTap() }, 3000)
                    view?.postDelayed({ triggerPlayerTap() }, 5500)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean = false

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null) return
                    hideCustomView()
                    customFullScreenView = view
                    customFullScreenCallback = callback
                    webVideoView.visibility = View.GONE
                    playerSurface.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    view.bringToFront()
                    view.requestFocus()
                }

                override fun onHideCustomView() {
                    hideCustomView()
                }
            }
        }

        playerSurface.setOnClickListener {
            triggerPlayerTap()
        }
    }

    fun triggerPlayerTap() {
        if (webVideoView.visibility != View.VISIBLE) return
        val w = webVideoView.width.toFloat()
        val h = webVideoView.height.toFloat()
        if (w <= 0 || h <= 0) return
        val density = context.resources.displayMetrics.density

        val focusedView = rootView.findFocus() ?: btnSourceBadgePitWall ?: btnSessionSelector ?: dynamicChannelButtons.firstOrNull()

        sendTouch(w / 2f, h / 2f)
        webVideoView.postDelayed({
            sendTouch(w / 2f, 35f * density)
            focusedView?.post {
                focusedView.requestFocus()
            }
        }, 300)
    }

    private fun sendTouch(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
        webVideoView.dispatchTouchEvent(down)
        webVideoView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun hideCustomView() {
        val fullscreen = customFullScreenView ?: return
        playerSurface.removeView(fullscreen)
        customFullScreenView = null
        val callback = customFullScreenCallback
        customFullScreenCallback = null
        webVideoView.visibility = View.VISIBLE
        callback?.onCustomViewHidden()
    }

    private fun isDirectMediaUrl(rawUrl: String): Boolean {
        val path = runCatching { Uri.parse(rawUrl).path.orEmpty().lowercase() }.getOrDefault("")
        return listOf(".m3u8", ".mpd", ".mp4", ".m4v").any { path.endsWith(it) }
    }

    fun openDiscoveredFutbolEvent() {
        val eventUrl = discoveredFutbolEventUrl
        if (eventUrl.isNullOrBlank()) {
            openDefaultWebSource("Fútbol Vivo / Fútbol Libre")
            return
        }

        val uri = runCatching { Uri.parse(eventUrl) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        if (uri == null || (scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) {
            openDefaultWebSource("Fútbol Vivo / Fútbol Libre")
            return
        }

        playWebPage(eventUrl, discoveredFutbolEventTitle)
    }

    private fun openDefaultWebSource(title: String) {
        val url = defaultWebSourceUrl
        if (url.isNullOrBlank()) {
            onNavigateToSettings()
            Toast.makeText(context, "Configura una fuente web en Ajustes de Video", Toast.LENGTH_LONG).show()
        } else {
            playWebPage(url, title)
        }
    }

    fun playWebPage(url: String, title: String = "Fuente web") {
        val parsed = runCatching { Uri.parse(url) }.getOrNull()
        if (parsed == null || (parsed.scheme != "http" && parsed.scheme != "https") || parsed.host.isNullOrBlank()) {
            Toast.makeText(context, "Introduce una URL web válida (http o https)", Toast.LENGTH_LONG).show()
            return
        }

        var targetUrl = url
        if (selectedServerIndex > 0 && !url.contains("s=")) {
            val sep = if (url.contains("?")) "&" else "?"
            targetUrl = "$url${sep}s=$selectedServerIndex"
        }

        currentWebUrl = targetUrl
        exoPlayer?.pause()
        exoPlayer?.stop()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.GONE
        webVideoView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE
        tvLiveSessionAlertTitle.text = "🏎️ " + title
        webVideoView.loadUrl(targetUrl)
        val targetBtn = btnSourceBadgePitWall ?: btnSessionSelector ?: dynamicChannelButtons.firstOrNull()
        targetBtn?.post { targetBtn.requestFocus() }
    }

    fun handleBackPressed(): Boolean {
        if (customFullScreenView != null) {
            hideCustomView()
            return true
        }
        if (webVideoView.visibility == View.VISIBLE && webVideoView.canGoBack()) {
            webVideoView.goBack()
            return true
        }
        return false
    }

    private fun initPlayers() {
        exoPlayer = ExoPlayer.Builder(context).build()
        playerView.player = exoPlayer

        radioPlayer = ExoPlayer.Builder(context).build()

        // By default off-race: Standby visible, playerView hidden, NO auto-playing demo video!
        switchToStandby()
    }

    private fun setupListeners() {
        // Los accesos de contenido abren la fuente web configurada; no muestran vídeos de prueba.
        btnStandbyPreview.setOnClickListener { openDiscoveredFutbolEvent() }
        btnStandbyRecap.setOnClickListener { openDefaultWebSource("Cartelera de eventos F1") }
        btnStandbyOpenWeb.setOnClickListener { openDefaultWebSource("Fútbol Vivo / Fútbol Libre") }

        btnStandbyStopVideo.setOnClickListener {
            switchToStandby()
        }

        // Selección de fuentes y agenda
        btnSourceBadgePitWall?.setOnClickListener {
            showSourceSelectionDialog()
        }
        btnSessionSelector?.setOnClickListener {
            if (futbolLibreEvents.isNotEmpty()) {
                showSessionSelectionDialog(futbolLibreEvents)
            } else if (preferencesManager.broadcastSource == BroadcastSource.FUTBOL_LIBRE) {
                loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
            } else {
                preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
                updateSourceBadgeUI()
                loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
            }
        }
        updateSourceBadgeUI()
        updateDynamicServerFocusChain()

        btnPlayRadio.nextFocusUpId = R.id.btnSourceBadgePitWall
        btnRadioVer.nextFocusUpId = R.id.btnSourceBadgePitWall
        btnRadioNor.nextFocusUpId = R.id.btnSourceBadgePitWall
        btnRadioLec.nextFocusUpId = R.id.btnSessionSelector
        btnRadioHam.nextFocusUpId = R.id.btnSessionSelector
        btnRadioRus.nextFocusUpId = R.id.btnSessionSelector
        btnRadioPia.nextFocusUpId = R.id.btnSessionSelector
        btnRadioAlo.nextFocusUpId = R.id.btnLiveSessionAction

        // Botón de refrescar / recargar la señal en vivo si cae
        btnLiveSessionAction.setOnClickListener {
            refreshStream()
        }
        layoutLiveSessionAlert.setOnClickListener {
            refreshStream()
        }

        // Radio Player
        btnPlayRadio.setOnClickListener {
            latestRadioUrl?.let { url ->
                val mediaItem = MediaItem.fromUri(url)
                radioPlayer?.setMediaItem(mediaItem)
                radioPlayer?.prepare()
                radioPlayer?.play()
                Toast.makeText(context, "Reproduciendo audio de radio...", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(context, "No hay audio de radio disponible en este momento", Toast.LENGTH_SHORT).show()
            }
        }

        // Radio Driver Chips
        btnRadioVer.setOnClickListener { tuneRadioDriver(1, "Max Verstappen") }
        btnRadioNor.setOnClickListener { tuneRadioDriver(4, "Lando Norris") }
        btnRadioLec.setOnClickListener { tuneRadioDriver(16, "Charles Leclerc") }
        btnRadioHam.setOnClickListener { tuneRadioDriver(44, "Lewis Hamilton") }
        btnRadioRus.setOnClickListener { tuneRadioDriver(63, "George Russell") }
        btnRadioPia.setOnClickListener { tuneRadioDriver(81, "Oscar Piastri") }
        btnRadioAlo.setOnClickListener { tuneRadioDriver(14, "Fernando Alonso") }

        // Metrics Switcher
        btnMetricsTiming.setOnClickListener {
            rvLeaderboard.visibility = View.VISIBLE
            layoutTelemetryPanel.visibility = View.GONE
            btnMetricsTiming.isSelected = true
            btnMetricsTelemetry.isSelected = false
        }

        btnMetricsTelemetry.setOnClickListener {
            rvLeaderboard.visibility = View.GONE
            layoutTelemetryPanel.visibility = View.VISIBLE
            btnMetricsTiming.isSelected = false
            btnMetricsTelemetry.isSelected = true
            loadDriverTelemetry(1)
        }
        btnMetricsTiming.isSelected = true

        btnTelemetryDemoToggle.setOnClickListener {
            loadDriverTelemetry(1)
        }
    }

    private fun initBroadcastSource() {
        val selectedSource = preferencesManager.broadcastSource
        loadBroadcastSource(selectedSource, forceReload = false)
    }

    fun loadBroadcastSource(source: BroadcastSource, forceReload: Boolean = false) {
        val isSourceChange = (currentLoadedSource != source)
        if (!forceReload && !isSourceChange && activeSpanishBroadcasts.isNotEmpty() && isVideoPlaying()) {
            return
        }
        futbolLibreSourcesJob?.cancel()
        currentLoadedSource = source
        activeSpanishBroadcasts = emptyList()
        selectedBroadcastIndex = 0
        selectedServerIndex = 0
        layoutServerSelector.visibility = View.GONE
        if (source != BroadcastSource.FUTBOL_LIBRE) {
            futbolLibreEvents = emptyList()
            selectedFutbolLibreEventIndex = 0
        }

        if (forceReload || isSourceChange) {
            currentWebUrl = null
            webVideoView.stopLoading()
            webVideoView.loadUrl("about:blank")
            webVideoView.visibility = View.GONE
            exoPlayer?.pause()
            exoPlayer?.stop()
            playerView.visibility = View.GONE
            layoutPlayerStandby.visibility = View.VISIBLE
        }

        tvStandbyStatusBadge.text = "${source.displayName.uppercase()} • BUSCANDO CANALES EN ESPAÑOL"
        tvStandbySubtitle.text = "Consultando servidores y opciones en español de ${source.displayName}..."
        tvStandbyCircuitSpecs?.text = "FUENTE ACTIVA: ${source.displayName} (${source.defaultUrl})"
        tvLiveSessionAlertTitle.text = "📡 Conectando a ${source.displayName}..."

        when (source) {
            BroadcastSource.FLX -> {
                btnSessionSelector?.visibility = View.VISIBLE
                btnSessionSelector?.text = "📅 Ver Agenda F1"
                btnSessionSelector?.setOnClickListener {
                    preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
                    updateSourceBadgeUI()
                    loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
                    Toast.makeText(context, "Cambiando a Fútbol Libre — Agenda...", Toast.LENGTH_SHORT).show()
                }
                updateDynamicServerFocusChain()
                futbolVivoExtractor.stop()
                futbolLibreExtractor.destroy()
                flxExtractor.onSpanishBroadcastsDetected = { list ->
                    onSpanishBroadcastsFound(list, BroadcastSource.FLX)
                }
                flxExtractor.onNoSpanishBroadcastsDetected = {
                    onNoSpanishBroadcastsFound(BroadcastSource.FLX)
                }
                flxExtractor.startExtraction(source.defaultUrl)
            }
            BroadcastSource.FUTBOL_VIVO -> {
                btnSessionSelector?.visibility = View.VISIBLE
                btnSessionSelector?.text = "📅 Ver Agenda F1"
                btnSessionSelector?.setOnClickListener {
                    preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
                    updateSourceBadgeUI()
                    loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
                    Toast.makeText(context, "Cambiando a Fútbol Libre — Agenda...", Toast.LENGTH_SHORT).show()
                }
                updateDynamicServerFocusChain()
                flxExtractor.destroy()
                futbolLibreExtractor.destroy()
                futbolVivoExtractor.onF1EventDetected = { event ->
                    val status = if (event.isLive) "EN VIVO" else "PROGRAMADA"
                    futbolVivoStatusText = "FÚTBOL VIVO • $status"
                    discoveredFutbolEventUrl = event.eventUrl
                    discoveredAlternativeUrls = event.alternativeUrls
                    discoveredFutbolEventTitle = event.title
                    val start = event.startTime?.let { " • $it" }.orEmpty()
                    tvStandbySubtitle.text = "Evento F1 detectado: ${event.title}$start"
                    updateFutbolVivoStatusBadge()
                    btnLiveSessionAction.text = "🔄 Refrescar"

                    if (!event.eventUrl.isNullOrBlank()) {
                        playWebPage(event.eventUrl, event.title)
                    }
                }
                futbolVivoExtractor.onNoEventDetected = {
                    discoveredFutbolEventUrl = null
                    discoveredAlternativeUrls = emptyList()
                    onNoSpanishBroadcastsFound(BroadcastSource.FUTBOL_VIVO)
                }
                futbolVivoExtractor.startExtraction(source.defaultUrl)
            }
            BroadcastSource.FUTBOL_LIBRE -> {
                flxExtractor.destroy()
                futbolVivoExtractor.stop()
                btnSessionSelector?.visibility = View.VISIBLE
                btnSessionSelector?.text = "⏳ Cargando agenda..."
                btnSessionSelector?.setOnClickListener {
                    Toast.makeText(context, "Consultando agenda de carreras en futbol-libre.app/agenda...", Toast.LENGTH_SHORT).show()
                }
                updateDynamicServerFocusChain()
                futbolLibreExtractor.onEventsDetected = { events ->
                    onFutbolLibreEventsFound(events)
                }
                futbolLibreExtractor.onNoEventsDetected = {
                    onNoSpanishBroadcastsFound(BroadcastSource.FUTBOL_LIBRE)
                }
                futbolLibreExtractor.startExtraction(source.defaultUrl)
            }
        }
    }

    private fun onFutbolLibreEventsFound(events: List<FutbolLibreEvent>) {
        futbolLibreEvents = events
        selectedFutbolLibreEventIndex = 0

        if (events.isEmpty()) {
            onNoSpanishBroadcastsFound(BroadcastSource.FUTBOL_LIBRE)
            return
        }

        btnSessionSelector?.visibility = View.VISIBLE
        btnSessionSelector?.setOnClickListener {
            showSessionSelectionDialog(futbolLibreEvents)
        }

        selectFutbolLibreEvent(0)
    }

    private fun updateSessionSelectorButton(event: FutbolLibreEvent) {
        val count = futbolLibreEvents.size
        btnSessionSelector?.text = "📅 ${event.getShortLabel()} ($count) ▼"
    }

    fun selectFutbolLibreEvent(index: Int) {
        if (index !in futbolLibreEvents.indices) return
        futbolLibreSourcesJob?.cancel()
        selectedFutbolLibreEventIndex = index
        selectedBroadcastIndex = 0
        activeSpanishBroadcasts = emptyList()
        layoutServerSelector.visibility = View.GONE

        val event = futbolLibreEvents[index]
        updateSessionSelectorButton(event)
        val categoryPrefix = if (event.isF1Academy) "F1 ACADEMY • " else ""
        tvStandbyTitle.text = (categoryPrefix + event.gpName).uppercase()
        tvStandbySubtitle.text = event.title + " • " + event.colombiaTime
        tvStandbyStatusBadge.text = (if (event.isF1Academy) "F1 ACADEMY" else "FÚTBOL LIBRE") + " • " + event.sources.size + " TRANSMISIÓN(ES)"

        if (event.sources.isNotEmpty()) {
            onSpanishBroadcastsFound(event.sources, BroadcastSource.FUTBOL_LIBRE)
            val currentBroadcast = event.sources[0]
            val catAlert = if (event.isF1Academy) " [F1 Academy]" else ""
            tvLiveSessionAlertTitle.text = "🏎️ " + event.gpName + catAlert + " — " + event.sessionName + " — " + currentBroadcast.getFormattedTitle()
            return
        }

        tvLiveSessionAlertTitle.text = "Consultando fuentes de " + event.sessionName + "..."
        futbolLibreSourcesJob = scope.launch(Dispatchers.IO) {
            val matchSources = futbolLibreExtractor.fetchSourcesForMatch(event.fullMatchUrl)
            withContext(Dispatchers.Main) {
                val selectedEvent = futbolLibreEvents.getOrNull(selectedFutbolLibreEventIndex)
                if (selectedEvent?.id != event.id || currentLoadedSource != BroadcastSource.FUTBOL_LIBRE) {
                    return@withContext
                }
                if (matchSources.isNotEmpty()) {
                    futbolLibreEvents = futbolLibreEvents.toMutableList().also { events ->
                        events[index] = events[index].copy(sources = matchSources)
                    }
                    onSpanishBroadcastsFound(matchSources, BroadcastSource.FUTBOL_LIBRE)
                    val currentBroadcast = matchSources[0]
                    val catAlert = if (event.isF1Academy) " [F1 Academy]" else ""
                    tvLiveSessionAlertTitle.text = "🏎️ " + event.gpName + catAlert + " — " + event.sessionName + " — " + currentBroadcast.getFormattedTitle()
                } else {
                    tvLiveSessionAlertTitle.text = event.title
                    tvStandbySubtitle.text = "No se encontraron fuentes en el HTML inicial. Abriendo la ficha del evento..."
                    playWebPage(event.fullMatchUrl, event.title)
                }
            }
        }
    }

    private fun showSessionSelectionDialog(events: List<FutbolLibreEvent>) {
        val sessionNames = events.map {
            val prefix = if (it.isF1Academy) "🏁 [F1 Academy] " else "🏎️ [F1] "
            "$prefix${it.title} (${it.colombiaTime})"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Seleccionar Sesión de Carreras")
            .setSingleChoiceItems(sessionNames, selectedFutbolLibreEventIndex) { dialog, which ->
                dialog.dismiss()
                selectFutbolLibreEvent(which)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun updateFutbolVivoStatusBadge() {
        if (currentLoadedSource == BroadcastSource.FUTBOL_VIVO && activeSpanishBroadcasts.isEmpty()) {
            tvStandbyStatusBadge.text = futbolVivoStatusText
        }
    }

    private fun setupLeaderboardView() {
        rvLeaderboard.layoutManager = LinearLayoutManager(context)
        rvLeaderboard.adapter = leaderboardAdapter
    }

    fun isVideoPlaying(): Boolean {
        val isExoPlaying = exoPlayer?.isPlaying == true && playerView.visibility == View.VISIBLE
        val isWebPlaying = webVideoView.visibility == View.VISIBLE && currentWebUrl != null && currentWebUrl != "about:blank"
        return isExoPlaying || isWebPlaying
    }

    fun onPitWallTabSelected() {
        updateSourceBadgeUI()
        val targetFocus = btnSourceBadgePitWall ?: btnSessionSelector ?: dynamicChannelButtons.firstOrNull()
        targetFocus?.post { targetFocus.requestFocus() }
        val selectedSource = preferencesManager.broadcastSource

        if (currentLoadedSource != selectedSource) {
            // Source changed in Settings! Immediate reload for new provider
            loadBroadcastSource(selectedSource, forceReload = true)
        } else if (!isVideoPlaying()) {
            if (activeSpanishBroadcasts.isNotEmpty()) {
                selectSpanishBroadcast(selectedBroadcastIndex)
            } else {
                loadBroadcastSource(selectedSource, forceReload = false)
            }
        }
    }

    fun playStream(url: String, title: String? = null) {
        if (!isDirectMediaUrl(url)) {
            playWebPage(url, title ?: "Fuente web")
            return
        }

        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE

        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }

        if (title != null) {
            tvLiveSessionAlertTitle.text = "🏎️ $title"
            Toast.makeText(context, "Cargando señal: $title", Toast.LENGTH_SHORT).show()
        }
    }

    fun switchToStandby() {
        if (customFullScreenView != null) hideCustomView()
        webVideoView.stopLoading()
        webVideoView.loadUrl("about:blank")
        webVideoView.visibility = View.GONE
        currentWebUrl = null
        exoPlayer?.pause()
        exoPlayer?.stop()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.GONE
        updateScheduleBanner()
    }

    fun tuneRadioDriver(driverNumber: Int, driverName: String) {
        tvRadioTitle.text = "📻 Sintonizando Radio: $driverName (#$driverNumber)..."
        scope.launch {
            try {
                val key = currentSessionKey
                if (key == null) {
                    tvRadioTitle.text = "📻 Radio: espera a que el calendario identifique una sesión"
                    Toast.makeText(context, "No hay una sesión F1 seleccionada", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val radio = repository.getDriverTeamRadio(key, driverNumber)
                if (radio != null && !radio.recordingUrl.isNullOrEmpty()) {
                    latestRadioUrl = radio.recordingUrl
                    tvRadioTitle.text = "📻 Radio Oficial: $driverName (#$driverNumber) • Conectado"
                    val mediaItem = MediaItem.fromUri(radio.recordingUrl)
                    radioPlayer?.setMediaItem(mediaItem)
                    radioPlayer?.prepare()
                    radioPlayer?.play()
                    Toast.makeText(context, "Reproduciendo radio de $driverName", Toast.LENGTH_SHORT).show()
                } else {
                    tvRadioTitle.text = "📻 Radio: $driverName (#$driverNumber) [Canal en espera]"
                    Toast.makeText(context, "Sin transmisión de radio activa para $driverName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadDriverTelemetry(driverNumber: Int) {
        scope.launch {
            try {
                val key = currentSessionKey
                if (key == null) {
                    Toast.makeText(context, "No hay una sesión F1 seleccionada", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val telemetry = repository.getTelemetryForDriver(key, driverNumber)

                if (telemetry != null && (telemetry.speed ?: 0) > 0) {
                    val spd = telemetry.speed ?: 0
                    tvTelemetrySpeed.text = spd.toString()

                    val gear = telemetry.gear ?: 0
                    tvTelemetryGear.text = if (gear == 0) "N" else gear.toString()

                    val rpm = telemetry.rpm ?: 0
                    tvTelemetryRpm.text = String.format("%,d RPM", rpm)
                    pbTelemetryRpm.progress = rpm

                    val thr = telemetry.throttle ?: 0
                    tvTelemetryThrottle.text = "Acelerador: $thr%"
                    pbTelemetryThrottle.progress = thr

                    val brk = if ((telemetry.brake ?: 0) > 0) 100 else 0
                    tvTelemetryBrake.text = "Freno: $brk%"
                    pbTelemetryBrake.progress = brk

                    val drsActive = (telemetry.drs ?: 0) in listOf(10, 12, 14)
                    if (drsActive) {
                        tvTelemetryDrs.text = "DRS ACTIVO"
                        tvTelemetryDrs.setBackgroundColor(Color.parseColor("#15803D"))
                    } else {
                        tvTelemetryDrs.text = "DRS INACTIVO"
                        tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
                    }
                } else {
                    tvTelemetrySpeed.text = "--"
                    tvTelemetryGear.text = "--"
                    tvTelemetryRpm.text = "Sin datos disponibles"
                    pbTelemetryRpm.progress = 0
                    tvTelemetryThrottle.text = "Acelerador: --"
                    pbTelemetryThrottle.progress = 0
                    tvTelemetryBrake.text = "Freno: --"
                    pbTelemetryBrake.progress = 0
                    tvTelemetryDrs.text = "DRS --"
                    tvTelemetryDrs.setBackgroundColor(Color.parseColor("#4B5563"))
                    val message = if (isUpcomingSession) {
                        "La telemetría estará disponible cuando comience $currentScheduleTitle"
                    } else {
                        "OpenF1 todavía no ha publicado telemetría para esta sesión"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onSessionUpdated(
        sessionKey: Long,
        isLive: Boolean,
        isUpcoming: Boolean,
        sessionTitleText: String
    ) {
        currentSessionKey = sessionKey
        isLiveSessionActive = isLive
        isUpcomingSession = isUpcoming
        currentScheduleTitle = sessionTitleText
        tvSessionTitle.text = sessionTitleText
        tvStandbyTitle.text = sessionTitleText.uppercase()
        updateScheduleBanner()
    }

    fun onScheduleUnavailable() {
        currentSessionKey = null
        isLiveSessionActive = false
        isUpcomingSession = false
        currentScheduleTitle = "Calendario de F1 no disponible"
        tvSessionTitle.text = currentScheduleTitle
        tvStandbyTitle.text = "F1 PIT WALL TV"
        updateScheduleBanner()
    }

    private fun updateScheduleBanner() {
        if (activeSpanishBroadcasts.isEmpty()) {
            when {
                isLiveSessionActive -> {
                    tvLiveSessionAlertTitle.text = "🔴 EN DIRECTO: $currentScheduleTitle"
                }
                isUpcomingSession -> {
                    tvLiveSessionAlertTitle.text = "⏳ PRÓXIMA SESIÓN: $currentScheduleTitle"
                }
                currentScheduleTitle == "Calendario de F1 no disponible" -> {
                    tvLiveSessionAlertTitle.text = "⚠️ Calendario F1 no disponible"
                }
                else -> {
                    tvLiveSessionAlertTitle.text = "🏁 ÚLTIMA SESIÓN: $currentScheduleTitle"
                }
            }
        }
        btnLiveSessionAction.text = "🔄 Refrescar"
        updateFutbolVivoStatusBadge()
    }

    inner class PitWallBridge {
        @JavascriptInterface
        fun onServersAvailable(count: Int, activeIdx: Int, titlesJson: String?) {
            rootView.post {
                val source = currentLoadedSource ?: preferencesManager.broadcastSource
                if (source != BroadcastSource.FLX && activeSpanishBroadcasts.isEmpty()) {
                    updateServerButtons(count, activeIdx, titlesJson)
                }
            }
        }

        @JavascriptInterface
        fun onSpanishBroadcastsDetected(json: String?) {
            rootView.post {
                if (!json.isNullOrBlank()) {
                    try {
                        val array = Gson().fromJson(json, Array<SpanishBroadcast>::class.java)
                        if (array != null && array.isNotEmpty()) {
                            val source = currentLoadedSource ?: preferencesManager.broadcastSource
                            if (source == BroadcastSource.FUTBOL_LIBRE) {
                                val currentEvent = futbolLibreEvents.getOrNull(selectedFutbolLibreEventIndex)
                                val detectedPage = array.firstOrNull()?.streamUrl?.substringBefore("#")
                                val expectedPage = currentEvent?.fullMatchUrl?.substringBefore("#")
                                if (currentEvent != null && detectedPage != null && expectedPage != null &&
                                    !detectedPage.equals(expectedPage, ignoreCase = true)) {
                                    Log.d("PitWallViewManager", "Ignoring broadcast update from different page: $detectedPage != $expectedPage")
                                    return@post
                                }
                            }
                            onSpanishBroadcastsFound(
                                array.toList(),
                                source
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onFutbolLibreAgendaDetected(json: String?) {
            rootView.post {
                val source = currentLoadedSource ?: preferencesManager.broadcastSource
                if (source == BroadcastSource.FUTBOL_LIBRE && futbolLibreEvents.isEmpty() && !json.isNullOrBlank()) {
                    try {
                        val events = Gson().fromJson(json, Array<FutbolLibreEvent>::class.java)?.toList()
                        if (!events.isNullOrEmpty()) {
                            onFutbolLibreEventsFound(events)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onNoSpanishBroadcastsDetected() {
            rootView.post {
                val source = currentLoadedSource ?: preferencesManager.broadcastSource
                if (source != BroadcastSource.FLX && activeSpanishBroadcasts.isEmpty()) {
                    onNoSpanishBroadcastsFound(source)
                }
            }
        }
    }

    fun showSourceSelectionDialog() {
        val sources = BroadcastSource.entries.toTypedArray()
        val sourceNames = sources.map {
            val activeMarker = if (it == preferencesManager.broadcastSource) "✓ " else "  "
            "$activeMarker${it.displayName} (${it.defaultUrl})"
        }.toTypedArray()
        val currentIndex = sources.indexOf(preferencesManager.broadcastSource).coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Seleccionar Fuente de Transmisiones")
            .setSingleChoiceItems(sourceNames, currentIndex) { dialog, which ->
                dialog.dismiss()
                val chosen = sources[which]
                if (chosen != preferencesManager.broadcastSource) {
                    preferencesManager.broadcastSource = chosen
                    updateSourceBadgeUI()
                    loadBroadcastSource(chosen, forceReload = true)
                    Toast.makeText(context, "Fuente cambiada a: ${chosen.displayName}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    fun updateSourceBadgeUI() {
        val source = preferencesManager.broadcastSource
        btnSourceBadgePitWall?.text = "📡 FUENTE: ${source.displayName.uppercase()} ▼"
        btnSourceBadgePitWall?.visibility = View.VISIBLE
    }

    private fun renderDynamicChannelButtons(list: List<SpanishBroadcast>) {
        val container = llDynamicChannelsContainer ?: return
        container.removeAllViews()
        dynamicChannelButtons.clear()

        if (list.isEmpty()) {
            updateDynamicServerFocusChain()
            return
        }

        val density = context.resources.displayMetrics.density
        val targetIdx = if (selectedBroadcastIndex in list.indices) selectedBroadcastIndex else 0

        list.forEachIndexed { idx, broadcast ->
            val btn = Button(context).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (28 * density).toInt()
                ).apply {
                    marginEnd = (4 * density).toInt()
                }
                background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.btn_tab_selector)
                text = broadcast.getButtonLabel()
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding((9 * density).toInt(), 0, (9 * density).toInt(), 0)
                isFocusable = true
                isClickable = true
                isSelected = (idx == targetIdx)

                setOnClickListener {
                    selectSpanishBroadcast(idx)
                }

                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        scrollServerSelector?.smoothScrollTo(left - (50 * density).toInt(), 0)
                    }
                }
            }
            dynamicChannelButtons.add(btn)
            container.addView(btn)
        }

        updateDynamicServerFocusChain()
    }

    private fun onSpanishBroadcastsFound(list: List<SpanishBroadcast>, source: BroadcastSource) {
        activeSpanishBroadcasts = list
        layoutServerSelector.visibility = View.VISIBLE
        updateSourceBadgeUI()
        renderDynamicChannelButtons(list)

        val targetIdx = if (selectedBroadcastIndex in list.indices) selectedBroadcastIndex else 0
        highlightBroadcastButton(targetIdx)

        val target = list[targetIdx]
        tvLiveSessionAlertTitle.text = "🏎️ " + target.getFormattedTitle()
        tvStandbySubtitle.text = "Transmisión identificada: ${target.getFormattedTitle()}"
        val badgeText = if (source == BroadcastSource.FUTBOL_LIBRE) {
            "FÚTBOL LIBRE • ${list.size} TRANSMISIÓN(ES)"
        } else {
            "${source.displayName.uppercase()} • ${list.size} OPCIÓN(ES) EN ESPAÑOL"
        }
        tvStandbyStatusBadge.text = badgeText

        if (!isVideoPlaying() || currentWebUrl != target.streamUrl) {
            playSpanishBroadcast(target)
        }
    }

    private fun updateDynamicServerFocusChain() {
        val buttons = dynamicChannelButtons
        val hasSessionSelector = btnSessionSelector?.visibility == View.VISIBLE && btnSessionSelector != null
        val hasSourceBadge = btnSourceBadgePitWall?.visibility == View.VISIBLE && btnSourceBadgePitWall != null

        val leftAnchorId = when {
            hasSessionSelector -> btnSessionSelector!!.id
            hasSourceBadge -> btnSourceBadgePitWall!!.id
            else -> View.NO_ID
        }

        buttons.forEachIndexed { index, button ->
            button.nextFocusUpId = R.id.btnTabPitWall
            button.nextFocusDownId = R.id.webVideoView
            button.nextFocusLeftId = if (index > 0) {
                buttons[index - 1].id
            } else if (leftAnchorId != View.NO_ID) {
                leftAnchorId
            } else {
                button.id
            }
            button.nextFocusRightId = if (index < buttons.size - 1) {
                buttons[index + 1].id
            } else {
                R.id.btnLiveSessionAction
            }
        }

        btnSessionSelector?.let { sel ->
            sel.nextFocusUpId = R.id.btnTabPitWall
            sel.nextFocusDownId = R.id.webVideoView
            sel.nextFocusLeftId = if (hasSourceBadge) btnSourceBadgePitWall!!.id else sel.id
            sel.nextFocusRightId = buttons.firstOrNull()?.id ?: R.id.btnLiveSessionAction
        }

        btnSourceBadgePitWall?.let { src ->
            src.nextFocusUpId = R.id.btnTabPitWall
            src.nextFocusDownId = R.id.webVideoView
            src.nextFocusLeftId = src.id
            src.nextFocusRightId = if (hasSessionSelector) btnSessionSelector!!.id else buttons.firstOrNull()?.id ?: R.id.btnLiveSessionAction
        }

        btnLiveSessionAction.nextFocusLeftId = buttons.lastOrNull()?.id ?: btnSessionSelector?.id ?: btnSourceBadgePitWall?.id ?: R.id.btnLiveSessionAction
    }

    private fun onNoSpanishBroadcastsFound(source: BroadcastSource) {
        activeSpanishBroadcasts = emptyList()
        renderDynamicChannelButtons(emptyList())
        updateSourceBadgeUI()

        if (source == BroadcastSource.FUTBOL_LIBRE) {
            btnSessionSelector?.visibility = View.VISIBLE
            btnSessionSelector?.text = "⚠️ Agenda vacía (Reintentar)"
            btnSessionSelector?.setOnClickListener {
                Toast.makeText(context, "Reintentando conectar a Fútbol Libre Agenda...", Toast.LENGTH_SHORT).show()
                loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
            }
            tvLiveSessionAlertTitle.text = "⚠️ No hay eventos de Fórmula 1 disponibles actualmente en Fútbol Libre Agenda"
            tvStandbySubtitle.text = "No se encontraron eventos o sesiones de F1 en la agenda de Fútbol Libre. Puedes cambiar la fuente en Ajustes o recargar."
            tvStandbyStatusBadge.text = "FÚTBOL LIBRE • SIN EVENTOS F1"
        } else {
            btnSessionSelector?.visibility = View.VISIBLE
            btnSessionSelector?.text = "📅 Ver Agenda F1"
            btnSessionSelector?.setOnClickListener {
                preferencesManager.broadcastSource = BroadcastSource.FUTBOL_LIBRE
                updateSourceBadgeUI()
                loadBroadcastSource(BroadcastSource.FUTBOL_LIBRE, forceReload = true)
            }
            tvLiveSessionAlertTitle.text = "⚠️ No hay transmisiones en español disponibles actualmente para ${source.displayName}"
            tvStandbySubtitle.text = "No se encontraron transmisiones en español disponibles en ${source.displayName}. Puedes cambiar la fuente en Ajustes o recargar."
            tvStandbyStatusBadge.text = "${source.displayName.uppercase()} • SIN TRANSMISIONES EN ESPAÑOL"
        }
        btnLiveSessionAction.text = "🔄 Refrescar"
        updateDynamicServerFocusChain()
    }

    fun selectSpanishBroadcast(index: Int) {
        if (index !in activeSpanishBroadcasts.indices) {
            selectServer(index)
            return
        }
        selectedBroadcastIndex = index
        highlightBroadcastButton(index)

        val broadcast = activeSpanishBroadcasts[index]
        if ((currentLoadedSource ?: preferencesManager.broadcastSource) == BroadcastSource.FUTBOL_LIBRE && selectedFutbolLibreEventIndex in futbolLibreEvents.indices) {
            val event = futbolLibreEvents[selectedFutbolLibreEventIndex]
            tvLiveSessionAlertTitle.text = "🏎️ ${event.gpName} — ${event.sessionName} — ${broadcast.getFormattedTitle()}"
        }
        Toast.makeText(context, "Sintonizando: ${broadcast.getFormattedTitle()}...", Toast.LENGTH_SHORT).show()
        playSpanishBroadcast(broadcast)
    }

    private fun highlightBroadcastButton(index: Int) {
        selectedBroadcastIndex = index
        dynamicChannelButtons.forEachIndexed { i, btn ->
            btn.isSelected = (i == index)
        }
    }

    fun playSpanishBroadcast(broadcast: SpanishBroadcast) {
        exoPlayer?.pause()
        exoPlayer?.stop()
        playerView.visibility = View.GONE
        layoutPlayerStandby.visibility = View.GONE
        webVideoView.visibility = View.VISIBLE
        btnStandbyStopVideo.visibility = View.VISIBLE
        tvLiveSessionAlertTitle.text = "🏎️ " + broadcast.getFormattedTitle()

        if (currentLoadedSource == BroadcastSource.FUTBOL_LIBRE) {
            val targetUrl = broadcast.streamUrl?.takeIf { it.isNotBlank() }
            if (targetUrl.isNullOrBlank()) {
                Toast.makeText(context, "La fuente seleccionada no tiene una página de evento válida.", Toast.LENGTH_SHORT).show()
                return
            }

            val targetPage = targetUrl.substringBefore("#")
            val loadedPage = (webVideoView.url ?: currentWebUrl.orEmpty()).substringBefore("#")
            currentWebUrl = targetUrl
            pendingFutbolLibreSourceId = broadcast.sourceId

            // Load the public event page first; its visible source selector then changes
            // the provider inside that page. Do not extract private media URLs.
            if (loadedPage.equals(targetPage, ignoreCase = true) && !broadcast.clickActionJs.isNullOrBlank()) {
                webVideoView.evaluateJavascript(broadcast.clickActionJs, null)
            } else {
                webVideoView.loadUrl(targetUrl)
            }

            webVideoView.postDelayed({ triggerPlayerTap() }, 1800)
            webVideoView.postDelayed({ triggerPlayerTap() }, 4000)
            dynamicChannelButtons.firstOrNull()?.let { targetBtn ->
                targetBtn.post { targetBtn.requestFocus() }
            }
            return
        }

        // Keep the existing FLX and Fútbol Vivo playback path unchanged.
        if (!broadcast.clickActionJs.isNullOrBlank()) {
            webVideoView.evaluateJavascript(broadcast.clickActionJs, null)
            webVideoView.postDelayed({ triggerPlayerTap() }, 2000)
            webVideoView.postDelayed({ triggerPlayerTap() }, 4500)
        } else if (!broadcast.streamUrl.isNullOrBlank()) {
            currentWebUrl = broadcast.streamUrl
            webVideoView.loadUrl(broadcast.streamUrl)
            dynamicChannelButtons.firstOrNull()?.let { targetBtn ->
                targetBtn.post { targetBtn.requestFocus() }
            }
        } else {
            Toast.makeText(context, "Transmisión no disponible. Selecciona otra opción.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateServerButtons(count: Int, activeIdx: Int, titlesJson: String?) {
        if (activeSpanishBroadcasts.isNotEmpty() || (currentLoadedSource ?: preferencesManager.broadcastSource) == BroadcastSource.FLX) {
            return
        }
        android.util.Log.d("PitWallServer", "updateServerButtons: count=$count, active=$activeIdx, titles=$titlesJson")

        val titles = if (!titlesJson.isNullOrBlank()) {
            runCatching { Gson().fromJson(titlesJson, Array<String>::class.java)?.toList().orEmpty() }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val syntheticBroadcasts = (0 until count).map { i ->
            val title = titles.getOrNull(i)?.let { formatServerTitle(it, i + 1) } ?: ("Opción " + (i + 1))
            SpanishBroadcast(
                index = i + 1,
                network = title,
                language = "",
                serverName = title,
                originalTitle = title,
                streamUrl = currentWebUrl.orEmpty(),
                clickActionJs = "window.switchServer && window.switchServer($i);",
                sourceId = "$i",
                customFormattedTitle = title,
                customButtonLabel = "S${i + 1}: $title"
            )
        }
        renderDynamicChannelButtons(syntheticBroadcasts)
        highlightBroadcastButton(activeIdx.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }

    private fun formatServerTitle(raw: String, fallbackNum: Int): String {
        val clean = raw.trim()
        if (clean.equals("English", ignoreCase = true)) return "Opción $fallbackNum (ENG)"
        if (clean.equals("Spanish", ignoreCase = true) || clean.equals("Español", ignoreCase = true)) return "Opción $fallbackNum (ESP)"
        return if (clean.length in 1..14) clean else "Opción $fallbackNum"
    }

    fun selectServer(index: Int) {
        selectedServerIndex = index
        serverButtons.forEachIndexed { buttonIndex, button ->
            button.isSelected = buttonIndex == index
        }

        Toast.makeText(context, "Conectando a Opción ${index + 1}...", Toast.LENGTH_SHORT).show()

        val altUrl = if (index < discoveredAlternativeUrls.size) discoveredAlternativeUrls[index] else null

        val switchJs = """
            (function(idx, fallbackAltUrl) {
                try {
                    // 1. soccer.html (currentMatch.channels)
                    if (window.currentMatch && Array.isArray(window.currentMatch.channels) && window.currentMatch.channels[idx]) {
                        window.activeServer = idx;
                        if (typeof window.updatePlayer === 'function') {
                            window.updatePlayer(window.currentMatch.channels[idx]);
                        }
                        const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                        tabs.forEach((b, i) => b.classList.toggle('active', i === idx));
                        return 'switched_channel_' + idx;
                    }
                    // 2. us.html / latin.html (currentMatch.streams)
                    if (window.currentMatch && Array.isArray(window.currentMatch.streams) && window.currentMatch.streams[idx]) {
                        window.activeStream = idx;
                        if (typeof window.renderPlayer === 'function') {
                            window.renderPlayer();
                        }
                        const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                        tabs.forEach((b, i) => b.classList.toggle('active', i === idx));
                        return 'switched_stream_' + idx;
                    }
                    // 3. Fallback: click tab button in DOM
                    const tabs = document.querySelectorAll('#streamTabs .se-tab-btn');
                    if (tabs && tabs[idx]) {
                        tabs[idx].click();
                        return 'clicked_tab_' + idx;
                    }
                    // 4. Fallback: navigate to alternative discovered F1 match URL
                    if (fallbackAltUrl && fallbackAltUrl !== window.location.href) {
                        window.location.replace(fallbackAltUrl);
                        return 'navigated_alt_url_' + fallbackAltUrl;
                    }
                    // 5. URL navigation with &s=idx for direct reload
                    const u = new URL(window.location.href);
                    u.searchParams.set('s', idx);
                    window.location.replace(u.toString());
                    return 'navigated_param_' + idx;
                } catch(e) {
                    return 'error: ' + e.message;
                }
            })($index, ${altUrl?.let { "'$it'" } ?: "null"});
        """.trimIndent()

        webVideoView.evaluateJavascript(switchJs) { res ->
            android.util.Log.d("PitWallServer", "Server switch result: $res")
        }

        webVideoView.postDelayed({ triggerPlayerTap() }, 2000)
        webVideoView.postDelayed({ triggerPlayerTap() }, 4500)
    }

    fun refreshStream() {
        val source = preferencesManager.broadcastSource
        Toast.makeText(context, "🔄 Recargando transmisiones (${source.displayName})...", Toast.LENGTH_SHORT).show()
        if (webVideoView.visibility == View.VISIBLE && currentWebUrl != null && currentWebUrl != "about:blank" && activeSpanishBroadcasts.isNotEmpty()) {
            selectSpanishBroadcast(selectedBroadcastIndex)
        } else {
            loadBroadcastSource(source, forceReload = true)
        }
    }

    fun onPause() {
        exoPlayer?.pause()
        webVideoView.onPause()
        flxExtractor.destroy()
        futbolVivoExtractor.onPause()
        futbolLibreExtractor.destroy()
    }

    fun onResume() {
        webVideoView.onResume()
        webVideoView.resumeTimers()
        futbolVivoExtractor.onResume()
    }

    fun onDestroy() {
        futbolLibreSourcesJob?.cancel()
        flxExtractor.destroy()
        futbolVivoExtractor.destroy()
        futbolLibreExtractor.destroy()
        webVideoView.stopLoading()
        webVideoView.destroy()
        exoPlayer?.release()
        radioPlayer?.release()
    }
}
