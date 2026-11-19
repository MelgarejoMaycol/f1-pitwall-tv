package com.f1pitwall.tv.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.f1pitwall.tv.core.config.VideoProviderItem
import com.f1pitwall.tv.core.config.VideoSourcesConfig
import com.f1pitwall.tv.ui.discovery.DiscoveredChannel
import com.f1pitwall.tv.ui.discovery.DiscoveredEvent
import com.f1pitwall.tv.ui.discovery.F1ContentDiscovery

/**
 * Pantalla de reproducción dedicada para señales WEB (WebView) y directas (Media3/ExoPlayer)
 * diseñada específicamente para Android TV.
 *
 * Características avanzadas implementadas:
 * - Soporte completo de múltiples ventanas (WebChromeClient.onCreateWindow) para target="_blank" y window.open.
 * - Navegación dentro de la actividad sin abrir navegadores externos.
 * - Pantalla completa HTML5 mediante onShowCustomView() y onHideCustomView().
 * - Soporte de cookies de terceros y almacenamiento DOM.
 * - Motor de descubrimiento de sesiones y canales de Fórmula 1 (Live 1, Live 2, Live 3).
 * - Normalización de dominios para cumplimiento de directivas CSP frame-ancestors.
 * - Navegación fluida con control remoto físico (D-Pad).
 * - Diagnósticos técnicos detallados y sanitización de credenciales.
 */
class PitWallTvActivity : AppCompatActivity() {

    // ---- UI Elements ----
    private lateinit var root: LinearLayout
    private lateinit var headerLayout: LinearLayout
    private lateinit var providerScroll: HorizontalScrollView
    private lateinit var providerContainer: LinearLayout
    private lateinit var channelScroll: HorizontalScrollView
    private lateinit var channelContainer: LinearLayout
    private lateinit var eventScroll: HorizontalScrollView
    private lateinit var eventContainer: LinearLayout
    private lateinit var mediaArea: FrameLayout
    private lateinit var webView: WebView
    private lateinit var playerView: PlayerView
    private lateinit var customViewContainer: FrameLayout
    private lateinit var popupContainer: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var statusBar: TextView
    private lateinit var diagBar: TextView

    // ---- Botones principales ----
    private lateinit var btnAutoF1: Button
    private lateinit var btnSessions: Button
    private lateinit var btnMuteToggle: Button
    private lateinit var btnFullscreen: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnDiag: Button

    // ---- Estado ----
    private var player: ExoPlayer? = null
    private var providers: List<VideoProviderItem> = emptyList()
    private var selectedProvider: VideoProviderItem? = null
    private var diagVisible = false
    private var isMuted = false

    // Estado del descubrimiento F1
    private enum class PageMode { DIRECTORY, EVENT_PLAYER, DIRECT_STREAM }
    private var currentMode = PageMode.DIRECTORY
    private var autoOpenFirstF1 = true
    private var discoveredEvents: List<DiscoveredEvent> = emptyList()
    private var currentEvent: DiscoveredEvent? = null
    private var discoveredChannels: List<DiscoveredChannel> = emptyList()
    private var activeChannelIndex = 0
    private var channelButtons: MutableList<Button> = mutableListOf()

    // Gestión de ventanas secundarias y pantalla completa HTML5
    private val activePopupWebViews: MutableList<WebView> = mutableListOf()
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    companion object {
        private const val TAG = "PitWallTV"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    // ======================================================================
    // JavascriptInterface Bridge
    // ======================================================================

    inner class DiscoveryBridge {
        @JavascriptInterface
        fun onF1Events(json: String) {
            runOnUiThread {
                handleF1EventsReceived(json)
            }
        }

        @JavascriptInterface
        fun onChannels(json: String) {
            runOnUiThread {
                handleChannelsReceived(json)
            }
        }

        @JavascriptInterface
        fun onLog(msg: String) {
            Log.d(TAG, "Bridge: $msg")
            if (diagVisible) {
                runOnUiThread { appendDiag("JS: $msg") }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        WebView.setWebContentsDebuggingEnabled(true)

        buildScreen()

        providers = VideoSourcesConfig.loadProviders(this)
        val previewUrl = intent.getStringExtra("preview_url")
        if (!previewUrl.isNullOrBlank()) {
            val previewName = intent.getStringExtra("preview_name") ?: "Vista previa WEB"
            providers = providers + VideoProviderItem(
                id = "preview_web",
                name = previewName,
                streamUrl = previewUrl,
                type = "WEB",
                isDefault = false
            )
        }

        if (providers.isEmpty()) {
            statusBar.text = "No hay canales configurados. Añade fuentes en Ajustes."
            return
        }

        buildProviderButtons()

        // Seleccionar la fuente predeterminada o la primera disponible
        val targetId = if (!previewUrl.isNullOrBlank()) "preview_web" else providers.first().id
        selectProvider(targetId)
    }

    // ======================================================================
    // Construcción de la interfaz de usuario para TV
    // ======================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }
        setContentView(root)

        // 1. Barra superior de navegación y controles
        headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setBackgroundColor(Color.rgb(12, 16, 24))
        }

        val logoTitle = TextView(this).apply {
            text = "🏎 F1 PITWALL TV"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        headerLayout.addView(logoTitle, LinearLayout.LayoutParams(0, dp(38), 1f))

        val btnBack = createTvButton("⬅ Salir", Color.rgb(40, 20, 20), Color.rgb(240, 120, 120)) {
            finish()
        }
        headerLayout.addView(btnBack)

        btnAutoF1 = createTvButton("🔍 Auto F1", Color.rgb(225, 6, 0), Color.WHITE) {
            triggerF1AutoDiscovery()
        }
        headerLayout.addView(btnAutoF1)

        btnSessions = createTvButton("🏁 Sesiones F1", Color.rgb(30, 60, 90), Color.WHITE) {
            toggleSessionsList()
        }.apply { visibility = View.GONE }
        headerLayout.addView(btnSessions)

        btnMuteToggle = createTvButton("🔊 Sonido", Color.rgb(30, 45, 65), Color.WHITE) {
            toggleMute()
        }
        headerLayout.addView(btnMuteToggle)

        btnFullscreen = createTvButton("⛶ Pantalla Completa", Color.rgb(25, 50, 40), Color.rgb(160, 230, 180)) {
            toggleFullscreen()
        }
        headerLayout.addView(btnFullscreen)

        btnRefresh = createTvButton("⟳ Actualizar", Color.rgb(35, 45, 60), Color.WHITE) {
            refreshCurrent()
        }
        headerLayout.addView(btnRefresh)

        btnDiag = createTvButton("⚙ Diag", Color.rgb(25, 30, 40), Color.rgb(200, 200, 200)) {
            toggleDiag()
        }
        headerLayout.addView(btnDiag)

        root.addView(headerLayout)

        // 2. Fila de fuentes configuradas (video_sources.json / variables)
        providerScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        providerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        providerScroll.addView(providerContainer)
        root.addView(providerScroll, LinearLayout.LayoutParams(-1, dp(42)))

        // 3. Fila de canales descubiertos (Live 1, Live 2, Live 3)
        channelScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
        }
        channelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        channelScroll.addView(channelContainer)
        root.addView(channelScroll, LinearLayout.LayoutParams(-1, dp(44)))

        // 4. Fila de sesiones de F1 disponibles
        eventScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
        }
        eventContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        eventScroll.addView(eventContainer)
        root.addView(eventScroll, LinearLayout.LayoutParams(-1, dp(42)))

        // 5. Área de reproducción multimedia
        mediaArea = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // 5a. WebView Principal
        webView = WebView(this).also { wv ->
            wv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            wv.setBackgroundColor(Color.BLACK)

            val ws: WebSettings = wv.settings
            ws.javaScriptEnabled = true
            ws.domStorageEnabled = true
            ws.databaseEnabled = true
            ws.mediaPlaybackRequiresUserGesture = false
            ws.useWideViewPort = true
            ws.loadWithOverviewMode = true
            ws.builtInZoomControls = false
            ws.displayZoomControls = false
            ws.setSupportZoom(false)
            ws.allowFileAccess = true
            ws.allowContentAccess = true

            // Soporte de múltiples ventanas para target="_blank" y window.open
            ws.setSupportMultipleWindows(true)
            ws.javaScriptCanOpenWindowsAutomatically = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            ws.userAgentString = DESKTOP_UA

            wv.addJavascriptInterface(DiscoveryBridge(), "F1Bridge")

            // Cookies de terceros para reproductores embebidos (kora, clappr, edge)
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAcceptThirdPartyCookies(wv, true)
                }
            }

            wv.webChromeClient = object : WebChromeClient() {
                // 1. Manejo de nuevas ventanas (target="_blank" o window.open)
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    Log.i(TAG, "onCreateWindow: isDialog=$isDialog, isUserGesture=$isUserGesture")
                    appendDiag("Ventana nueva solicitada (gesture=$isUserGesture)")
                    if (resultMsg == null) return false

                    val childWv = createChildWebView()
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                    transport.webView = childWv
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    Log.i(TAG, "onCloseWindow invocado")
                    closeChildWebView(window)
                }

                // 2. Pantalla completa HTML5 (onShowCustomView / onHideCustomView)
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    Log.i(TAG, "onShowCustomView: entrando a pantalla completa HTML5")
                    appendDiag("HTML5 Fullscreen activado")
                    if (customView != null) {
                        onHideCustomView()
                        return
                    }
                    customView = view
                    customViewCallback = callback

                    headerLayout.visibility = View.GONE
                    providerScroll.visibility = View.GONE
                    channelScroll.visibility = View.GONE
                    eventScroll.visibility = View.GONE
                    statusBar.visibility = View.GONE
                    diagBar.visibility = View.GONE
                    webView.visibility = View.GONE

                    customViewContainer.removeAllViews()
                    customViewContainer.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    customViewContainer.visibility = View.VISIBLE
                    customViewContainer.bringToFront()
                    view?.requestFocus()
                    setImmersiveMode(true)
                }

                override fun onHideCustomView() {
                    hideCustomViewInternal()
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                    Log.i(TAG, "Permisos multimedia concedidos: ${request?.resources?.toList()}")
                }

                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    val level = msg?.messageLevel()?.name ?: "LOG"
                    val text = msg?.message() ?: ""
                    val line = msg?.lineNumber() ?: 0
                    val src = msg?.sourceId()?.substringAfterLast('/') ?: ""
                    Log.d(TAG, "JS[$level] ($src:$line) $text")

                    if (diagVisible && (level == "ERROR" || level == "WARNING")) {
                        appendDiag("JS[$level] $text")
                    }
                    return true
                }

                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                    result?.confirm()
                    return true
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                }
            }

            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return true
                    val scheme = url.scheme ?: ""
                    val urlStr = url.toString()
                    val sanitized = sanitizeUrl(urlStr)

                    Log.d(TAG, "shouldOverrideUrlLoading: $sanitized (scheme=$scheme)")

                    if (scheme == "http" || scheme == "https") {
                        // Si el dominio es vivogoal.space u otro mirror no listado en CSP frame-ancestors,
                        // normalizar transparentemente a golygoals.com para permitir carga del iframe
                        if (urlStr.contains("vivogoal.space")) {
                            val fixedUrl = urlStr.replace("vivogoal.space", "golygoals.com")
                            Log.i(TAG, "Normalizando dominio a golygoals.com: ${sanitizeUrl(fixedUrl)}")
                            view?.loadUrl(fixedUrl)
                            return true
                        }
                        return false // permitir navegación interna dentro del WebView
                    }

                    Log.w(TAG, "Esquema no permitido rechazado: $scheme")
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progress.visibility = View.VISIBLE
                    val sanitized = sanitizeUrl(url)
                    Log.i(TAG, "onPageStarted: $sanitized")
                    statusBar.text = "Conectando: $sanitized"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.visibility = View.GONE
                    val sanitized = sanitizeUrl(url)
                    Log.i(TAG, "onPageFinished [$currentMode]: $sanitized")
                    handlePageFinished(view, url.orEmpty())
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    val isMain = request?.isForMainFrame == true
                    val source = if (isMain) "Página Principal" else "Recurso/iFrame"
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString() ?: "Error de red"
                    } else "Error de red"
                    val sanitizedUrl = sanitizeUrl(request?.url?.toString())

                    Log.e(TAG, "[$source] Error: $desc en $sanitizedUrl")
                    if (isMain) {
                        progress.visibility = View.GONE
                        statusBar.text = "Error al conectar: $desc"
                        appendDiag("Error principal: $desc")
                    } else if (diagVisible) {
                        appendDiag("[$source] $desc")
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    val status = errorResponse?.statusCode ?: 0
                    val isMain = request?.isForMainFrame == true
                    val source = if (isMain) "Página Principal" else "Recurso/iFrame"
                    val sanitizedUrl = sanitizeUrl(request?.url?.toString())

                    Log.w(TAG, "[$source] HTTP $status en $sanitizedUrl")
                    if (status == 429) {
                        runOnUiThread {
                            statusBar.text = "⚠️ Servidor temporalmente limitado (HTTP 429). Selecciona Live 2 arriba."
                            appendDiag("HTTP 429: Servidor saturado. Sugiriendo Live 2.")
                            if (activeChannelIndex == 0 && channelButtons.size > 1) {
                                channelButtons.getOrNull(1)?.requestFocus()
                            }
                        }
                    } else if (isMain) {
                        statusBar.text = "Error del servidor (HTTP $status)"
                        appendDiag("HTTP $status en página principal")
                    }
                }
            }

            wv.isFocusable = true
            wv.isFocusableInTouchMode = true
        }
        mediaArea.addView(webView, FrameLayout.LayoutParams(-1, -1))

        // 5b. ExoPlayer para flujos HLS/directos
        playerView = PlayerView(this).apply {
            useController = true
            isFocusable = true
            visibility = View.GONE
        }
        mediaArea.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        // 5c. Contenedor para pantalla completa HTML5
        customViewContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        mediaArea.addView(customViewContainer, FrameLayout.LayoutParams(-1, -1))

        // 5d. Contenedor para ventanas emergentes secundarias
        popupContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        mediaArea.addView(popupContainer, FrameLayout.LayoutParams(-1, -1))

        // 5e. Indicador de carga
        progress = ProgressBar(this).apply { visibility = View.GONE }
        mediaArea.addView(progress, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))

        root.addView(mediaArea, LinearLayout.LayoutParams(-1, 0, 1f))

        // 6. Barra de estado inferior
        statusBar = TextView(this).apply {
            text = "Listo"
            textSize = 12f
            setTextColor(Color.rgb(160, 190, 215))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(3), dp(10), dp(3))
        }
        root.addView(statusBar, LinearLayout.LayoutParams(-1, dp(26)))

        // 7. Consola de diagnóstico (oculta por defecto)
        diagBar = TextView(this).apply {
            text = ""
            textSize = 10f
            setTextColor(Color.rgb(255, 215, 120))
            setBackgroundColor(Color.argb(230, 15, 20, 25))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
            maxLines = 4
        }
        root.addView(diagBar, LinearLayout.LayoutParams(-1, -2))
    }

    // ======================================================================
    // Gestión de ventanas emergentes secundarias (onCreateWindow)
    // ======================================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun createChildWebView(): WebView {
        val child = WebView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(Color.BLACK)

            val ws = settings
            ws.javaScriptEnabled = true
            ws.domStorageEnabled = true
            ws.databaseEnabled = true
            ws.mediaPlaybackRequiresUserGesture = false
            ws.userAgentString = DESKTOP_UA
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val urlStr = request?.url?.toString() ?: return false
                    val sanitized = sanitizeUrl(urlStr)
                    Log.i(TAG, "ChildWebView -> Redirigiendo URL: $sanitized")

                    if (isAllowedSportsUrl(urlStr)) {
                        // Navegación válida hacia el evento o reproductor: cargar en el WebView principal
                        val finalUrl = if (urlStr.contains("vivogoal.space")) {
                            urlStr.replace("vivogoal.space", "golygoals.com")
                        } else urlStr

                        this@PitWallTvActivity.webView.loadUrl(finalUrl)
                        closeChildWebView(this@apply)
                        return true
                    }

                    // Si es una redirección externa o publicidad intrusiva, descartar
                    Log.w(TAG, "Popup publicitario bloqueado: $sanitized")
                    closeChildWebView(this@apply)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val urlStr = url.orEmpty()
                    if (urlStr.isNotBlank() && urlStr != "about:blank") {
                        val sanitized = sanitizeUrl(urlStr)
                        Log.i(TAG, "ChildWebView onPageStarted: $sanitized")
                        if (isAllowedSportsUrl(urlStr)) {
                            val finalUrl = if (urlStr.contains("vivogoal.space")) {
                                urlStr.replace("vivogoal.space", "golygoals.com")
                            } else urlStr

                            this@PitWallTvActivity.webView.loadUrl(finalUrl)
                            closeChildWebView(this@apply)
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView?) {
                    closeChildWebView(this@apply)
                }
            }
        }

        activePopupWebViews.add(child)
        popupContainer.addView(child, FrameLayout.LayoutParams(-1, -1))
        return child
    }

    private fun closeChildWebView(wv: WebView?) {
        if (wv == null) return
        try {
            popupContainer.removeView(wv)
            activePopupWebViews.remove(wv)
            wv.stopLoading()
            wv.clearHistory()
            wv.destroy()
            if (activePopupWebViews.isEmpty()) {
                popupContainer.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando child WebView: ${e.message}")
        }
    }

    private fun closeAllPopups() {
        activePopupWebViews.toList().forEach { closeChildWebView(it) }
        activePopupWebViews.clear()
        popupContainer.removeAllViews()
        popupContainer.visibility = View.GONE
    }

    private fun isAllowedSportsUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Permitir páginas de eventos y proveedores de stream reconocidos
        if (lower.contains("futbolvivo.tv") ||
            lower.contains("golygoals.com") ||
            lower.contains("vivogoal.space") ||
            lower.contains("soccer.html") ||
            lower.contains("us.html") ||
            lower.contains("latin.html") ||
            lower.contains("kora") ||
            lower.contains("streame") ||
            lower.contains("clappr") ||
            lower.contains("sportycos")
        ) {
            return true
        }

        // Rechazar dominios comunes de publicidad o apuestas externas
        val isAd = lower.contains("bet365") ||
                lower.contains("1xbet") ||
                lower.contains("onclick") ||
                lower.contains("popads") ||
                lower.contains("adcash") ||
                lower.contains("doubleclick") ||
                lower.contains("googleads")

        return !isAd
    }

    // ======================================================================
    // Descubrimiento automático de F1 y cambio de canal
    // ======================================================================

    private fun triggerF1AutoDiscovery() {
        autoOpenFirstF1 = true
        val provider = selectedProvider ?: providers.firstOrNull { it.type.uppercase() == "WEB" } ?: return
        currentMode = PageMode.DIRECTORY
        channelScroll.visibility = View.GONE
        eventScroll.visibility = View.GONE
        sessionsButtonEnabled(false)

        playerView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        statusBar.text = "🔍 Buscando carreras y sesiones de F1…"
        progress.visibility = View.VISIBLE

        Log.i(TAG, "Iniciando Auto F1 en ${provider.streamUrl}")
        webView.loadUrl(provider.streamUrl)
    }

    private fun handlePageFinished(view: WebView?, url: String) {
        val lowerUrl = url.lowercase()
        val isEventPage = lowerUrl.contains("soccer.html") || lowerUrl.contains("us.html") || lowerUrl.contains("latin.html")

        // Neutralizar target="_blank" en el DOM para que los enlaces no intenten abrir ventanas nuevas
        view?.evaluateJavascript(
            """
            (function() {
                document.querySelectorAll('a[target="_blank"]').forEach(function(a) {
                    a.removeAttribute('target');
                });
            })();
            """.trimIndent(), null
        )

        if (isEventPage || currentMode == PageMode.EVENT_PLAYER) {
            scanEventPageChannels(view)
        } else if (currentMode == PageMode.DIRECTORY) {
            scanDirectoryForF1(view)
        } else {
            statusBar.text = selectedProvider?.name.orEmpty()
        }
    }

    private fun scanDirectoryForF1(view: WebView?) {
        statusBar.text = "🔍 Analizando agenda deportiva: buscando Fórmula 1…"
        view?.evaluateJavascript(F1ContentDiscovery.buildDirectoryScannerJs(), null)
    }

    private fun handleF1EventsReceived(json: String) {
        val events = F1ContentDiscovery.parseF1Events(json)
        Log.i(TAG, "F1Bridge: ${events.size} eventos F1 detectados")
        discoveredEvents = events

        if (events.isEmpty()) {
            statusBar.text = "No se encontraron transmisiones de F1 activas en este momento."
            appendDiag("Directorio: 0 eventos F1 encontrados")
        } else {
            sessionsButtonEnabled(true, events.size)
            buildSessionButtons(events)

            if (autoOpenFirstF1) {
                // Seleccionar el evento prioritario (Práctica 1 / Gran Premio)
                val target = events.first()
                currentEvent = target
                currentMode = PageMode.EVENT_PLAYER
                statusBar.text = "✓ Encontrado: ${target.title} · Abriendo…"
                appendDiag("F1 target: ${target.title} (${target.session})")

                Log.i(TAG, "Navegando a evento F1: ${target.url}")
                webView.loadUrl(target.url)
            } else {
                eventScroll.visibility = View.VISIBLE
                statusBar.text = "Directorio: ${events.size} sesiones de F1 disponibles. Selecciona una arriba."
                eventContainer.getChildAt(0)?.requestFocus()
            }
        }
    }

    private fun scanEventPageChannels(view: WebView?) {
        currentMode = PageMode.EVENT_PLAYER
        statusBar.text = "🔍 Extrayendo canales disponibles…"
        view?.evaluateJavascript(F1ContentDiscovery.buildEventPageScannerJs(), null)
    }

    private fun handleChannelsReceived(json: String) {
        val (title, channels) = F1ContentDiscovery.parseEventPageChannels(json)
        Log.i(TAG, "F1Bridge: ${channels.size} canales recibidos para $title")
        discoveredChannels = channels

        if (channels.isNotEmpty()) {
            statusBar.text = "▶ $title · ${channels.size} canales listos"
            buildChannelButtons(channels)
            activeChannelIndex = 0
            highlightActiveChannel(0)
        } else {
            statusBar.text = "Transmisión en preparación. Pulsa Actualizar en unos segundos."
            appendDiag("Página de evento: 0 canales listos")
        }
    }

    private fun switchToChannel(index: Int) {
        if (index !in discoveredChannels.indices) return
        activeChannelIndex = index
        val ch = discoveredChannels[index]
        statusBar.text = "⟳ Cambiando a ${ch.name}…"
        highlightActiveChannel(index)

        val switchJs = F1ContentDiscovery.buildSwitchChannelJs(index)
        webView.evaluateJavascript(switchJs) { result ->
            Log.d(TAG, "Cambio a canal $index: $result")
            runOnUiThread {
                statusBar.text = "▶ ${ch.name} · ${currentEvent?.title ?: "Fórmula 1"}"
            }
        }
    }

    // ======================================================================
    // Controles de Audio y Pantalla Completa
    // ======================================================================

    private fun toggleMute() {
        isMuted = !isMuted
        val muteJs = """
            (function() {
                var videos = document.querySelectorAll('video');
                videos.forEach(function(v) { 
                    v.muted = $isMuted; 
                    if (!$isMuted) { v.volume = 1.0; }
                });
                var unmuteBtn = document.getElementById('unmute') || document.querySelector('.unmute-button');
                if (unmuteBtn) unmuteBtn.click();
                return 'muted_' + $isMuted;
            })();
        """.trimIndent()

        webView.evaluateJavascript(muteJs) { res ->
            Log.d(TAG, "toggleMute JS: $res")
        }
        btnMuteToggle.text = if (isMuted) "🔇 Silenciado" else "🔊 Sonido"
        appendDiag(if (isMuted) "Audio: Silenciado" else "Audio: Activado")
    }

    private fun hideCustomViewInternal() {
        Log.i(TAG, "hideCustomViewInternal: saliendo de pantalla completa HTML5")
        appendDiag("HTML5 Fullscreen desactivado")
        val view = customView ?: return

        customViewContainer.removeView(view)
        customViewContainer.visibility = View.GONE

        headerLayout.visibility = View.VISIBLE
        providerScroll.visibility = View.VISIBLE
        if (discoveredChannels.isNotEmpty()) channelScroll.visibility = View.VISIBLE
        statusBar.visibility = View.VISIBLE
        if (diagVisible) diagBar.visibility = View.VISIBLE
        webView.visibility = View.VISIBLE

        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        setImmersiveMode(false)
        webView.requestFocus()
    }

    private fun toggleFullscreen() {
        if (customView != null) {
            hideCustomViewInternal()
        } else {
            // Intentar entrar a pantalla completa HTML5 vía elemento de video o contenedor
            val fsJs = """
                (function() {
                    var fsBtn = document.getElementById('fsBtn');
                    if (fsBtn) { fsBtn.click(); return 'fsBtn_clicked'; }
                    var el = document.getElementById('liveIframe') || 
                             document.getElementById('playerContainer') || 
                             document.querySelector('video');
                    if (el && el.requestFullscreen) { 
                        el.requestFullscreen(); 
                        return 'requestFullscreen_invoked'; 
                    }
                    return 'fullscreen_target_not_found';
                })();
            """.trimIndent()

            webView.evaluateJavascript(fsJs) { res ->
                Log.d(TAG, "toggleFullscreen JS: $res")
            }
        }
    }

    private fun setImmersiveMode(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                if (enable) {
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (enable) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // ======================================================================
    // Renderizado de botones interactivos para Android TV
    // ======================================================================

    private fun buildChannelButtons(channels: List<DiscoveredChannel>) {
        channelContainer.removeAllViews()
        channelButtons.clear()

        channels.forEachIndexed { i, ch ->
            val btn = Button(this).apply {
                text = ch.name
                isFocusable = true
                isFocusableInTouchMode = true
                textSize = 13f
                setPadding(dp(16), dp(4), dp(16), dp(4))
                styleAsTvPill(this, Color.rgb(20, 30, 45), Color.rgb(0, 160, 255))
                setOnClickListener {
                    switchToChannel(i)
                }
            }
            channelButtons.add(btn)
            channelContainer.addView(
                btn,
                LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(8) }
            )
        }

        channelScroll.visibility = View.VISIBLE
        channelButtons.firstOrNull()?.requestFocus()
    }

    private fun highlightActiveChannel(index: Int) {
        channelButtons.forEachIndexed { i, btn ->
            if (i == index) {
                styleAsTvPill(btn, Color.rgb(225, 6, 0), Color.WHITE)
            } else {
                styleAsTvPill(btn, Color.rgb(20, 30, 45), Color.rgb(180, 200, 220))
            }
        }
    }

    private fun buildSessionButtons(events: List<DiscoveredEvent>) {
        eventContainer.removeAllViews()
        events.forEach { ev ->
            val label = if (ev.session.isNotBlank()) "${ev.session} · ${ev.title}" else ev.title
            val btn = Button(this).apply {
                text = label
                isFocusable = true
                isFocusableInTouchMode = true
                textSize = 12f
                setPadding(dp(12), dp(2), dp(12), dp(2))
                styleAsTvPill(this, Color.rgb(25, 35, 50), Color.WHITE)
                setOnClickListener {
                    currentEvent = ev
                    currentMode = PageMode.EVENT_PLAYER
                    eventScroll.visibility = View.GONE
                    statusBar.text = "Cargando ${ev.title}…"
                    webView.loadUrl(ev.url)
                }
            }
            eventContainer.addView(
                btn,
                LinearLayout.LayoutParams(-2, dp(36)).apply { marginEnd = dp(6) }
            )
        }
    }

    private fun sessionsButtonEnabled(enable: Boolean, count: Int = 0) {
        btnSessions.visibility = if (enable) View.VISIBLE else View.GONE
        if (enable) {
            btnSessions.text = "🏁 Sesiones F1 ($count)"
        }
    }

    private fun toggleSessionsList() {
        if (eventScroll.visibility == View.VISIBLE) {
            eventScroll.visibility = View.GONE
        } else {
            eventScroll.visibility = View.VISIBLE
            eventContainer.getChildAt(0)?.requestFocus()
        }
    }

    // ======================================================================
    // Proveedores preconfigurados (HLS, WEB)
    // ======================================================================

    private fun buildProviderButtons() {
        providerContainer.removeAllViews()
        providers.forEach { p ->
            val btn = Button(this).apply {
                text = p.name
                tag = p.id
                isFocusable = true
                isFocusableInTouchMode = true
                textSize = 12f
                setPadding(dp(12), dp(2), dp(12), dp(2))
                styleAsTvPill(this, Color.rgb(22, 28, 38), Color.rgb(180, 195, 210))
                setOnClickListener { selectProvider(p.id) }
            }
            providerContainer.addView(
                btn,
                LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) }
            )
        }
    }

    private fun selectProvider(id: String) {
        val p = providers.firstOrNull { it.id == id } ?: return
        selectedProvider = p

        providerContainer.childrenButtons().forEach { b ->
            val isCurrent = b.tag == id
            if (isCurrent) {
                styleAsTvPill(b, Color.rgb(0, 120, 215), Color.WHITE)
            } else {
                styleAsTvPill(b, Color.rgb(22, 28, 38), Color.rgb(180, 195, 210))
            }
        }

        channelScroll.visibility = View.GONE
        eventScroll.visibility = View.GONE
        sessionsButtonEnabled(false)

        when (p.type.uppercase()) {
            "WEB", "WEBVIEW", "HTML" -> {
                playerView.visibility = View.GONE
                webView.visibility = View.VISIBLE
                currentMode = PageMode.DIRECTORY
                statusBar.text = "Abriendo ${p.name}…"
                webView.loadUrl(p.streamUrl)
            }
            "HLS", "DASH", "MP4" -> {
                currentMode = PageMode.DIRECT_STREAM
                webView.stopLoading()
                webView.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                playDirectStream(p.streamUrl, p.name)
            }
        }
    }

    private fun playDirectStream(url: String, name: String) {
        player?.release()
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        progress.visibility = View.VISIBLE
                        statusBar.text = "Buffering · $name"
                    }
                    Player.STATE_READY -> {
                        progress.visibility = View.GONE
                        statusBar.text = "▶ $name"
                    }
                    Player.STATE_ENDED -> {
                        progress.visibility = View.GONE
                        statusBar.text = "Fin de la transmisión"
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                progress.visibility = View.GONE
                statusBar.text = "Error de reproducción: ${error.errorCodeName}"
                appendDiag("ExoPlayer error: ${error.errorCodeName}")
            }
        })

        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun refreshCurrent() {
        diagBar.text = ""
        when (currentMode) {
            PageMode.EVENT_PLAYER -> {
                progress.visibility = View.VISIBLE
                webView.reload()
            }
            PageMode.DIRECTORY -> {
                triggerF1AutoDiscovery()
            }
            PageMode.DIRECT_STREAM -> {
                selectedProvider?.let { playDirectStream(it.streamUrl, it.name) }
            }
        }
    }

    // ======================================================================
    // Navegación con control remoto (D-pad & Back)
    // ======================================================================

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (webView.visibility == View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    // Si el usuario presiona ARRIBA desde la vista web, llevar foco a los canales o controles
                    if (channelButtons.isNotEmpty() && !channelButtons.any { it.isFocused }) {
                        channelButtons.getOrNull(activeChannelIndex)?.requestFocus()
                        return true
                    } else if (!headerLayout.childrenButtons().any { it.isFocused }) {
                        btnAutoF1.requestFocus()
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            // 1. Salir de pantalla completa HTML5 si está activa
            customView != null -> {
                hideCustomViewInternal()
            }
            // 2. Cerrar ventanas emergentes si existen
            popupContainer.childCount > 0 -> {
                closeAllPopups()
            }
            // 3. Ocultar lista de sesiones si está desplegada
            eventScroll.visibility == View.VISIBLE -> {
                eventScroll.visibility = View.GONE
            }
            // 4. Si estamos en el reproductor de evento, volver al directorio
            currentMode == PageMode.EVENT_PLAYER -> {
                autoOpenFirstF1 = false
                currentMode = PageMode.DIRECTORY
                channelScroll.visibility = View.GONE
                statusBar.text = "Regresando al directorio…"
                val provider = selectedProvider ?: providers.firstOrNull()
                if (provider != null) {
                    webView.loadUrl(provider.streamUrl)
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    super.onBackPressed()
                }
            }
            // 5. Historial del WebView
            webView.visibility == View.VISIBLE && webView.canGoBack() -> {
                webView.goBack()
            }
            // 6. Salir de la actividad
            else -> {
                super.onBackPressed()
            }
        }
    }

    // ======================================================================
    // Ciclo de vida y helpers
    // ======================================================================

    override fun onPause() {
        super.onPause()
        player?.pause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        closeAllPopups()
        webView.stopLoading()
        webView.clearHistory()
        webView.destroy()
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun toggleDiag() {
        diagVisible = !diagVisible
        diagBar.visibility = if (diagVisible) View.VISIBLE else View.GONE
    }

    private fun appendDiag(msg: String) {
        val current = diagBar.text.toString()
        val lines = current.split("\n").takeLast(3)
        diagBar.text = (lines + msg).joinToString("\n")
        if (diagVisible) diagBar.visibility = View.VISIBLE
    }

    /**
     * Sanitiza las URLs eliminando parámetros de tokens, llaves o credenciales privadas
     * antes de emitir cualquier registro en logcat o la consola de pantalla.
     */
    private fun sanitizeUrl(rawUrl: String?): String {
        if (rawUrl.isNullOrBlank()) return ""
        return try {
            val uri = Uri.parse(rawUrl)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: ""
            val path = uri.path ?: ""
            "$scheme://$host$path"
        } catch (_: Exception) {
            rawUrl.substringBefore('?')
        }
    }

    private fun createTvButton(title: String, bgColor: Int, textColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = title
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 12f
            setPadding(dp(12), dp(2), dp(12), dp(2))
            styleAsTvPill(this, bgColor, textColor)
            setOnClickListener { onClick() }
        }
    }

    private fun styleAsTvPill(btn: Button, bgColor: Int, textColor: Int) {
        val normalState = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(bgColor)
            setStroke(dp(1), Color.argb(80, 255, 255, 255))
        }

        val focusedState = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(Color.rgb(0, 160, 255))
            setStroke(dp(2), Color.WHITE)
        }

        val stateList = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedState)
            addState(intArrayOf(), normalState)
        }

        btn.background = stateList
        btn.setTextColor(textColor)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun ViewGroup.childrenButtons(): List<Button> =
        (0 until childCount).mapNotNull { getChildAt(it) as? Button }
}
