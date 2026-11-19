package com.f1pitwall.tv.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import com.f1pitwall.tv.core.storage.PreferencesManager

/** Dedicated TV screen for configured web pages and direct media URLs. */
class PitWallTvActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var channelContainer: LinearLayout
    private lateinit var refreshButton: Button
    private var player: ExoPlayer? = null
    private var providers: List<VideoProviderItem> = emptyList()
    private var selected: VideoProviderItem? = null
    private var selectedButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        buildScreen()
        providers = VideoSourcesConfig.loadProviders(this)
        val previewUrl = intent.getStringExtra("preview_url")
        if (!previewUrl.isNullOrBlank()) {
            providers = providers + VideoProviderItem(
                id = "preview_web",
                name = intent.getStringExtra("preview_name") ?: "Vista previa WEB",
                streamUrl = previewUrl,
                type = "WEB",
                isDefault = false
            )
        }
        if (providers.isEmpty()) {
            status.text = "No hay canales configurados. Añade fuentes en Ajustes."
            refreshButton.isEnabled = false
        } else {
            buildChannelButtons()
            val saved = PreferencesManager(this).getSelectedSource()
            selectSource(saved?.id ?: providers.first().id)
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 12, 18))
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        setContentView(root)
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "PIT WALL  /  TV"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        refreshButton = Button(this).apply {
            text = "⟳ Actualizar"
            isFocusable = true
            setOnClickListener { refreshCurrent() }
        }
        header.addView(refreshButton)
        root.addView(header)

        val channelScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        channelContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        channelScroll.addView(channelContainer)
        root.addView(channelScroll, LinearLayout.LayoutParams(-1, dp(48)))

        val mediaArea = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return true
                    return url.scheme == "https" || url.scheme == "http"
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    progress.visibility = View.VISIBLE
                    status.text = "Cargando página…"
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.visibility = View.GONE
                    status.text = selected?.name.orEmpty()
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        progress.visibility = View.GONE
                        status.text = "No se pudo cargar el canal. Pulsa Actualizar para reintentar."
                    }
                }
            }
            isFocusable = true
            isFocusableInTouchMode = true
        }
        mediaArea.addView(webView, FrameLayout.LayoutParams(-1, -1))
        playerView = PlayerView(this).apply { useController = true; isFocusable = true; visibility = View.GONE }
        mediaArea.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        progress = ProgressBar(this).apply { visibility = View.GONE }
        mediaArea.addView(progress, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        root.addView(mediaArea, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            text = "Selecciona un canal"
            textSize = 13f
            setTextColor(Color.rgb(160, 180, 200))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(36)))
    }

    private fun buildChannelButtons() {
        channelContainer.removeAllViews()
        providers.forEach { source ->
            val button = Button(this).apply {
                text = source.name
                tag = source.id
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { selectSource(source.id) }
            }
            channelContainer.addView(button, LinearLayout.LayoutParams(-2, dp(42)).apply { marginEnd = dp(6) })
        }
    }

    private fun selectSource(id: String) {
        val source = providers.firstOrNull { it.id == id } ?: return
        selected = source
        selectedButton?.isSelected = false
        selectedButton = channelContainer.childrenButtons().firstOrNull { it.tag == id }
        selectedButton?.isSelected = true
        selectedButton?.requestFocus()
        loadSelected()
    }

    private fun loadSelected() {
        val source = selected ?: return
        progress.visibility = View.VISIBLE
        status.text = "Abriendo ${source.name}…"
        player?.release()
        player = null
        playerView.player = null
        when (source.type.uppercase()) {
            "WEB", "WEBVIEW", "HTML" -> {
                playerView.visibility = View.GONE
                webView.visibility = View.VISIBLE
                webView.loadUrl(source.streamUrl)
            }
            "HLS", "DASH", "MP4" -> {
                webView.stopLoading()
                webView.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                val exo = ExoPlayer.Builder(this).build()
                player = exo
                playerView.player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> { progress.visibility = View.VISIBLE; status.text = "Buffering · ${source.name}" }
                            Player.STATE_READY -> { progress.visibility = View.GONE; status.text = source.name }
                            Player.STATE_ENDED -> { progress.visibility = View.GONE; status.text = "Vídeo finalizado" }
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        progress.visibility = View.GONE
                        status.text = "Error de reproducción: ${error.errorCodeName}"
                    }
                })
                exo.setMediaItem(MediaItem.fromUri(source.streamUrl))
                exo.prepare()
                exo.playWhenReady = true
            }
            else -> { progress.visibility = View.GONE; status.text = "Tipo de fuente no compatible: ${source.type}" }
        }
    }

    private fun refreshCurrent() {
        val source = selected ?: return
        when (source.type.uppercase()) {
            "WEB", "WEBVIEW", "HTML" -> if (webView.visibility == View.VISIBLE) {
                progress.visibility = View.VISIBLE
                webView.reload()
            } else loadSelected()
            else -> loadSelected()
        }
    }

    override fun onBackPressed() {
        if (webView.visibility == View.VISIBLE && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
    override fun onPause() { super.onPause(); player?.pause(); webView.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun LinearLayout.childrenButtons(): List<Button> = (0 until childCount).mapNotNull { getChildAt(it) as? Button }
}
