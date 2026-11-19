package com.f1pitwall.tv.ui.player

import android.content.Context
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.f1pitwall.tv.core.util.AppLogger
import com.f1pitwall.tv.data.repository.F1Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RadioManager(
    private val context: Context,
    private val repository: F1Repository,
    private val scope: CoroutineScope,
    private val tvRadioTitle: TextView,
    private val btnPlayRadio: Button,
    private val radioButtons: List<Button>,
    private val onPlaybackStarted: () -> Unit = {}
) {
    private var radioPlayer: ExoPlayer? = null
    private var activeRadioSearchJob: Job? = null

    var currentRadioDriverNumber: Int? = null
        private set
    var currentRadioDriverName: String? = null
        private set
    var latestRadioUrl: String? = null
        private set

    init {
        initRadioPlayer()
        setupPlayButton()
    }

    private fun initRadioPlayer() {
        radioPlayer = ExoPlayer.Builder(context).build()
        radioPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isPlaying = radioPlayer?.isPlaying == true
                tvRadioTitle.text = formatRadioPlaybackTitle(currentRadioDriverName, currentRadioDriverNumber, playbackState, isPlaying)
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        btnPlayRadio.text = "⏳ Cargando"
                        btnPlayRadio.isEnabled = false
                        btnPlayRadio.alpha = 0.7f
                    }
                    Player.STATE_READY -> {
                        btnPlayRadio.isEnabled = true
                        btnPlayRadio.alpha = 1.0f
                        btnPlayRadio.text = if (isPlaying) "⏸ Pausar" else "▶ Reanudar"
                    }
                    Player.STATE_ENDED -> {
                        btnPlayRadio.isEnabled = true
                        btnPlayRadio.alpha = 1.0f
                        btnPlayRadio.text = "▶ Reproducir"
                    }
                    Player.STATE_IDLE -> {
                        // En reposo
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val name = currentRadioDriverName ?: "Piloto"
                val num = currentRadioDriverNumber?.let { " #$it" } ?: ""
                if (isPlaying) {
                    onPlaybackStarted()
                    tvRadioTitle.text = "📻 Audio de Radio ($name$num): Reproduciendo grabación"
                    btnPlayRadio.text = "⏸ Pausar"
                } else if (radioPlayer?.playbackState == Player.STATE_READY) {
                    tvRadioTitle.text = "📻 Audio de Radio ($name$num): Pausado"
                    btnPlayRadio.text = "▶ Reanudar"
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLogger.w("RadioManager", "Error en radio player", error)
                val name = currentRadioDriverName ?: "Piloto"
                val num = currentRadioDriverNumber?.let { " #$it" } ?: ""
                tvRadioTitle.text = "📻 Error de reproducción en radio ($name$num): Audio no disponible"
                btnPlayRadio.isEnabled = true
                btnPlayRadio.alpha = 1.0f
                btnPlayRadio.text = "▶ Reintentar"
                Toast.makeText(context, "Error de red al reproducir audio de radio", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupPlayButton() {
        btnPlayRadio.setOnClickListener {
            val player = radioPlayer ?: return@setOnClickListener
            val driverName = currentRadioDriverName ?: "Piloto"
            val driverNum = currentRadioDriverNumber?.let { " #$it" } ?: ""

            when {
                player.isPlaying -> {
                    player.pause()
                    btnPlayRadio.text = "▶ Reanudar"
                    tvRadioTitle.text = "📻 Audio de Radio ($driverName$driverNum): Pausado"
                }
                player.playbackState == Player.STATE_READY -> {
                    onPlaybackStarted()
                    player.play()
                    btnPlayRadio.text = "⏸ Pausar"
                    tvRadioTitle.text = "📻 Audio de Radio ($driverName$driverNum): Reproduciendo grabación"
                }
                else -> {
                    val url = latestRadioUrl
                    if (!url.isNullOrEmpty()) {
                        onPlaybackStarted()
                        tvRadioTitle.text = "📻 Audio de Radio ($driverName$driverNum): Preparando reproducción..."
                        btnPlayRadio.text = "⏳ Cargando"
                        val mediaItem = MediaItem.fromUri(url)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    } else {
                        tvRadioTitle.text = "📻 Audio de Radio ($driverName$driverNum): Fuente no disponible"
                        btnPlayRadio.isEnabled = false
                        btnPlayRadio.alpha = 0.5f
                    }
                }
            }
        }
    }

    fun tuneRadioDriver(
        sessionKey: Long?,
        driverNumber: Int,
        driverName: String,
        selectedButton: Button? = null
    ) {
        currentRadioDriverNumber = driverNumber
        currentRadioDriverName = driverName

        radioButtons.forEach { it.isSelected = false }
        selectedButton?.isSelected = true

        activeRadioSearchJob?.cancel()

        if (sessionKey == null) {
            tvRadioTitle.text = "Radio: selecciona una sesión válida para consultar grabaciones"
            btnPlayRadio.isEnabled = false
            btnPlayRadio.alpha = 0.5f
            btnPlayRadio.text = "▶ Reproducir"
            latestRadioUrl = null
            radioPlayer?.stop()
            return
        }

        radioPlayer?.stop()
        latestRadioUrl = null
        btnPlayRadio.isEnabled = false
        btnPlayRadio.alpha = 0.5f
        btnPlayRadio.text = "⏳ Buscando..."
        tvRadioTitle.text = "📻 Radio #$driverNumber ($driverName): Buscando grabación oficial..."

        activeRadioSearchJob = scope.launch {
            try {
                val radio = repository.getDriverTeamRadio(sessionKey, driverNumber)

                if (currentRadioDriverNumber != driverNumber) return@launch

                val url = radio?.recordingUrl
                if (!url.isNullOrEmpty()) {
                    latestRadioUrl = url
                    tvRadioTitle.text = "📻 Radio #$driverNumber ($driverName): Grabación disponible"
                    btnPlayRadio.isEnabled = true
                    btnPlayRadio.alpha = 1.0f
                    btnPlayRadio.text = "▶ Reproducir"
                    btnPlayRadio.requestFocus()
                } else {
                    latestRadioUrl = null
                    tvRadioTitle.text = "📻 Radio #$driverNumber ($driverName): Sin grabaciones en esta sesión"
                    btnPlayRadio.isEnabled = false
                    btnPlayRadio.alpha = 0.5f
                    btnPlayRadio.text = "No disponible"
                }
            } catch (e: Exception) {
                AppLogger.w("RadioManager", "Error al buscar radio para piloto $driverNumber", e)
                if (currentRadioDriverNumber == driverNumber) {
                    tvRadioTitle.text = "📻 Error de conexión al buscar radio de #$driverNumber"
                    btnPlayRadio.isEnabled = false
                    btnPlayRadio.alpha = 0.5f
                    btnPlayRadio.text = "Error"
                }
            }
        }
    }

    fun pause() {
        radioPlayer?.pause()
    }

    fun stop() {
        radioPlayer?.stop()
    }

    fun isPlaying(): Boolean = radioPlayer?.isPlaying == true

    fun release() {
        activeRadioSearchJob?.cancel()
        radioPlayer?.release()
        radioPlayer = null
    }

    companion object {
        fun formatRadioPlaybackTitle(
            driverName: String?,
            driverNumber: Int?,
            playbackState: Int,
            isPlaying: Boolean
        ): String {
            val name = driverName ?: "Piloto"
            val num = driverNumber?.let { " #$it" } ?: ""
            return when (playbackState) {
                Player.STATE_BUFFERING -> "📻 Audio de Radio ($name$num): Preparando reproducción..."
                Player.STATE_READY -> if (isPlaying) {
                    "📻 Audio de Radio ($name$num): Reproduciendo grabación"
                } else {
                    "📻 Audio de Radio ($name$num): Pausado"
                }
                Player.STATE_ENDED -> "📻 Audio de Radio ($name$num): Grabación finalizada"
                else -> "📻 Audio de Radio ($name$num): Audio disponible"
            }
        }
    }
}
