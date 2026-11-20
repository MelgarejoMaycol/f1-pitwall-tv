package com.f1pitwall.tv.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.data.model.CircularHubItem

class CircularHubAdapter(
    private var items: List<CircularHubItem> = emptyList(),
    private val onHubClick: (CircularHubItem) -> Unit
) : RecyclerView.Adapter<CircularHubAdapter.ViewHolder>() {

    fun updateData(newItems: List<CircularHubItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_circular_hub, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvHubIcon: TextView = itemView.findViewById(R.id.tvHubIcon)
        private val tvHubTitle: TextView = itemView.findViewById(R.id.tvHubTitle)
        private val tvHubSubtitle: TextView = itemView.findViewById(R.id.tvHubSubtitle)
        private val tvHubBadge: TextView = itemView.findViewById(R.id.tvHubBadge)

        init {
            itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.06f).scaleY(1.06f).translationZ(8f).setDuration(160).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(160).start()
                }
            }
        }

        fun bind(item: CircularHubItem) {
            tvHubIcon.text = item.iconEmoji
            tvHubTitle.text = item.title
            tvHubSubtitle.text = item.subtitle
            tvHubBadge.text = item.badgeText ?: "OFICIAL"

            itemView.setOnClickListener {
                onHubClick(item)
            }
        }
    }
}
