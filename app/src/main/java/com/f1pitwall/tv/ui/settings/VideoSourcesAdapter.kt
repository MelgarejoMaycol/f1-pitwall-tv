package com.f1pitwall.tv.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.VideoProviderItem

class VideoSourcesAdapter(
    private var activeId: String = "",
    private val onSelectActive: (VideoProviderItem) -> Unit,
    private val onDelete: (VideoProviderItem) -> Unit
) : RecyclerView.Adapter<VideoSourcesAdapter.ViewHolder>() {

    private val items = mutableListOf<VideoProviderItem>()

    fun updateData(newItems: List<VideoProviderItem>, currentActiveId: String = activeId) {
        activeId = currentActiveId
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_source, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvSourceName.text = item.name
        holder.tvSourceType.text = item.type
        holder.tvSourceUrl.text = item.streamUrl

        val isActive = item.id == activeId
        holder.tvSourceActive.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.btnUseSource.text = if (isActive) "✓ Activa" else "Usar"
        holder.btnUseSource.isEnabled = !isActive

        holder.btnUseSource.setOnClickListener {
            onSelectActive(item)
        }

        holder.btnDeleteSource.setOnClickListener {
            onDelete(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSourceName: TextView = view.findViewById(R.id.tvSourceName)
        val tvSourceType: TextView = view.findViewById(R.id.tvSourceType)
        val tvSourceActive: TextView = view.findViewById(R.id.tvSourceActive)
        val tvSourceUrl: TextView = view.findViewById(R.id.tvSourceUrl)
        val btnUseSource: Button = view.findViewById(R.id.btnUseSource)
        val btnDeleteSource: Button = view.findViewById(R.id.btnDeleteSource)
    }
}
