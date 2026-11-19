package com.f1pitwall.tv.ui.settings

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.VideoProviderItem
import com.f1pitwall.tv.core.config.VideoSourceValidator
import com.f1pitwall.tv.core.storage.PreferencesManager
import java.util.UUID

class SettingsViewManager(
    private val rootView: View,
    private val preferencesManager: PreferencesManager,
    private val onSourceActiveChanged: (VideoProviderItem) -> Unit = {},
    private val onSourceDeleted: (VideoProviderItem) -> Unit = {},
    private val onStartPlayback: (url: String, type: String, name: String) -> Unit = { _, _, _ -> },
    private val onBeforeEditing: () -> Unit = {}
) {
    private val tvDetectedSessionTitle: TextView = rootView.findViewById(R.id.tvDetectedSessionTitle)
    private val tvSearchChip1: TextView = rootView.findViewById(R.id.tvSearchChip1)
    private val tvSearchChip2: TextView = rootView.findViewById(R.id.tvSearchChip2)
    private val tvSearchChip3: TextView = rootView.findViewById(R.id.tvSearchChip3)
    private val tvSearchChip4: TextView = rootView.findViewById(R.id.tvSearchChip4)
    private val etStreamName: EditText = rootView.findViewById(R.id.etStreamName)
    private val etStreamUrl: EditText = rootView.findViewById(R.id.etStreamUrl)
    private val btnTypeWeb: Button = rootView.findViewById(R.id.btnTypeWeb)
    private val btnTypeHls: Button = rootView.findViewById(R.id.btnTypeHls)
    private val btnTypeDash: Button = rootView.findViewById(R.id.btnTypeDash)
    private val btnTypeMp4: Button = rootView.findViewById(R.id.btnTypeMp4)
    private val btnSaveStream: Button = rootView.findViewById(R.id.btnSaveStream)
    private val btnCancelEdit: Button = rootView.findViewById(R.id.btnCancelEdit)
    private val btnTestStream: Button = rootView.findViewById(R.id.btnTestStream)
    private val tvEmptySourcesNotice: TextView = rootView.findViewById(R.id.tvEmptySourcesNotice)
    private val rvConfiguredSources: RecyclerView = rootView.findViewById(R.id.rvConfiguredSources)

    private lateinit var videoSourcesAdapter: VideoSourcesAdapter
    private var selectedStreamType: String = "HLS"
    private var editingSourceId: String? = null

    init {
        setupSourcesView()
    }

    private fun setupSourcesView() {
        val context = rootView.context

        btnTypeWeb.setOnClickListener { updateStreamTypeSelection("WEB") }
        btnTypeHls.setOnClickListener { updateStreamTypeSelection("HLS") }
        btnTypeDash.setOnClickListener { updateStreamTypeSelection("DASH") }
        btnTypeMp4.setOnClickListener { updateStreamTypeSelection("MP4") }
        updateStreamTypeSelection("WEB")

        btnCancelEdit.setOnClickListener {
            cancelEditingSource()
        }

        videoSourcesAdapter = VideoSourcesAdapter(
            activeId = preferencesManager.activeVideoProvider,
            onSelectActive = { source ->
                preferencesManager.activeVideoProvider = source.id
                refreshConfiguredSourcesList()
                onSourceActiveChanged(source)
                Toast.makeText(context, "Fuente seleccionada: ${source.name}", Toast.LENGTH_SHORT).show()
            },
            onEdit = { source ->
                onBeforeEditing()
                editingSourceId = source.id
                etStreamName.setText(source.name)
                etStreamUrl.setText(source.streamUrl)
                updateStreamTypeSelection(source.type)
                btnSaveStream.text = "💾 Actualizar Fuente"
                btnCancelEdit.visibility = View.VISIBLE
                etStreamName.requestFocus()
            },
            onDelete = { source ->
                if (editingSourceId == source.id) {
                    cancelEditingSource()
                }
                onSourceDeleted(source)
                preferencesManager.deleteCustomSource(source.id)
                refreshConfiguredSourcesList()
                Toast.makeText(context, "Fuente eliminada: ${source.name}", Toast.LENGTH_SHORT).show()
            }
        )

        rvConfiguredSources.layoutManager = LinearLayoutManager(context)
        rvConfiguredSources.adapter = videoSourcesAdapter
        refreshConfiguredSourcesList()

        btnSaveStream.setOnClickListener {
            val name = etStreamName.text.toString().trim()
            val url = etStreamUrl.text.toString().trim()

            val nameValidation = VideoSourceValidator.validateName(name)
            if (nameValidation is VideoSourceValidator.ValidationResult.Invalid) {
                Toast.makeText(context, nameValidation.reason, Toast.LENGTH_SHORT).show()
                etStreamName.requestFocus()
                return@setOnClickListener
            }

            val urlValidation = VideoSourceValidator.validateUrl(url)
            if (urlValidation is VideoSourceValidator.ValidationResult.Invalid) {
                Toast.makeText(context, urlValidation.reason, Toast.LENGTH_SHORT).show()
                etStreamUrl.requestFocus()
                return@setOnClickListener
            }

            val currentEditId = editingSourceId
            if (currentEditId != null) {
                val updatedSource = VideoProviderItem(
                    id = currentEditId,
                    name = name,
                    streamUrl = url,
                    type = selectedStreamType,
                    isDefault = preferencesManager.getSelectedSource()?.id == currentEditId
                )
                preferencesManager.saveCustomSource(updatedSource)
                cancelEditingSource()
                refreshConfiguredSourcesList()
                Toast.makeText(context, "Fuente actualizada con éxito", Toast.LENGTH_SHORT).show()
            } else {
                val existing = preferencesManager.findByUrl(url)
                if (existing != null) {
                    Toast.makeText(context, "Ya existe una fuente guardada con esa URL (${existing.name})", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val isFirst = preferencesManager.getCustomSources().isEmpty()
                val newSource = VideoProviderItem(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    streamUrl = url,
                    type = selectedStreamType,
                    isDefault = isFirst
                )
                preferencesManager.saveCustomSource(newSource)
                if (preferencesManager.activeVideoProvider.isEmpty()) {
                    preferencesManager.activeVideoProvider = newSource.id
                }
                etStreamName.setText("")
                etStreamUrl.setText("")
                refreshConfiguredSourcesList()
                Toast.makeText(context, "Fuente agregada con éxito", Toast.LENGTH_SHORT).show()
            }
        }

        btnTestStream.setOnClickListener {
            val inputUrl = etStreamUrl.text.toString().trim()
            val inputName = etStreamName.text.toString().trim()

            val targetUrl: String?
            val targetName: String
            val targetType: String

            if (inputUrl.isNotEmpty()) {
                val validation = VideoSourceValidator.validateUrl(inputUrl)
                if (validation is VideoSourceValidator.ValidationResult.Invalid) {
                    Toast.makeText(context, validation.reason, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                targetUrl = inputUrl
                targetName = inputName.ifEmpty { "Fuente de Prueba" }
                targetType = selectedStreamType
            } else {
                val active = preferencesManager.getSelectedSource()
                if (active != null) {
                    targetUrl = active.streamUrl
                    targetName = active.name
                    targetType = active.type
                } else {
                    Toast.makeText(context, "Introduce una URL o selecciona una fuente guardada", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            onStartPlayback(targetUrl, targetType, targetName)
            Toast.makeText(context, "Iniciando prueba de señal: $targetName", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelEditingSource() {
        editingSourceId = null
        etStreamName.setText("")
        etStreamUrl.setText("")
        btnSaveStream.text = "💾 Guardar Fuente"
        btnCancelEdit.visibility = View.GONE
    }

    private fun updateStreamTypeSelection(type: String) {
        val normalized = VideoSourceValidator.normalizeType(type)
        selectedStreamType = normalized
        btnTypeWeb.isSelected = normalized == "WEB"
        btnTypeHls.isSelected = normalized == "HLS"
        btnTypeDash.isSelected = normalized == "DASH"
        btnTypeMp4.isSelected = normalized == "MP4"
    }

    fun refreshConfiguredSourcesList() {
        val sources = preferencesManager.getAllAvailableSources()
        if (sources.isEmpty()) {
            tvEmptySourcesNotice.visibility = View.VISIBLE
            rvConfiguredSources.visibility = View.GONE
        } else {
            tvEmptySourcesNotice.visibility = View.GONE
            rvConfiguredSources.visibility = View.VISIBLE
            val activeId = preferencesManager.getSelectedSource()?.id ?: preferencesManager.activeVideoProvider
            videoSourcesAdapter.updateData(sources, activeId)
        }
    }

    fun updateSmartSearchQueries(gpName: String, sessionType: String = "prácticas") {
        tvDetectedSessionTitle.text = "🏎️ Evento identificado: $gpName • ${sessionType.replaceFirstChar { it.uppercase() }}"
        val cleanGp = gpName.replace("Grand Prix", "GP", ignoreCase = true).replace("Gran Premio", "GP", ignoreCase = true).trim()

        tvSearchChip1.text = "$cleanGp F1"
        tvSearchChip2.text = "Gran Premio de $cleanGp Fórmula 1"
        tvSearchChip3.text = "Fórmula 1 $cleanGp en directo"
        tvSearchChip4.text = "F1 $cleanGp $sessionType"

        val copyAction: (String) -> Unit = { query ->
            etStreamName.setText(query)
            Toast.makeText(rootView.context, "Término asignado a la etiqueta: $query", Toast.LENGTH_SHORT).show()
            etStreamUrl.requestFocus()
        }

        tvSearchChip1.setOnClickListener { copyAction(tvSearchChip1.text.toString()) }
        tvSearchChip2.setOnClickListener { copyAction(tvSearchChip2.text.toString()) }
        tvSearchChip3.setOnClickListener { copyAction(tvSearchChip3.text.toString()) }
        tvSearchChip4.setOnClickListener { copyAction(tvSearchChip4.text.toString()) }
    }
}
