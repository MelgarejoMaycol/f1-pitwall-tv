package com.f1pitwall.tv.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.VideoProviderItem

class VideoSourcesAdapter(
    private var activeId: String = "",
    private val onSelectActive: (VideoProviderItem) -> Unit,
    private val onEdit: (VideoProviderItem) -> Unit,
    private val onDelete: (VideoProviderItem) -> Unit
) : ListAdapter<VideoProviderItem, VideoSourcesAdapter.ViewHolder>(VideoSourceDiffCallback) {

    val sources: List<VideoProviderItem> get() = currentList

    fun updateData(newItems: List<VideoProviderItem>, currentActiveId: String = activeId) {
        val activeChanged = activeId != currentActiveId
        activeId = currentActiveId
        submitList(newItems) {
            if (activeChanged) {
                notifyDataSetChanged()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_source, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
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

        holder.btnEditSource.setOnClickListener {
            onEdit(item)
        }

        holder.btnDeleteSource.setOnClickListener {
            onDelete(item)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSourceName: TextView = view.findViewById(R.id.tvSourceName)
        val tvSourceType: TextView = view.findViewById(R.id.tvSourceType)
        val tvSourceActive: TextView = view.findViewById(R.id.tvSourceActive)
        val tvSourceUrl: TextView = view.findViewById(R.id.tvSourceUrl)
        val btnUseSource: Button = view.findViewById(R.id.btnUseSource)
        val btnEditSource: Button = view.findViewById(R.id.btnEditSource)
        val btnDeleteSource: Button = view.findViewById(R.id.btnDeleteSource)
    }

    object VideoSourceDiffCallback : DiffUtil.ItemCallback<VideoProviderItem>() {
        override fun areItemsTheSame(oldItem: VideoProviderItem, newItem: VideoProviderItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: VideoProviderItem, newItem: VideoProviderItem): Boolean {
            return oldItem == newItem
        }
    }
}
