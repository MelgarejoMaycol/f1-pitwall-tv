package com.f1pitwall.tv.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.data.model.CircularHubItem

class CircularHubAdapter(
    private val items: List<CircularHubItem>,
    private val onHubClick: (CircularHubItem) -> Unit
) : RecyclerView.Adapter<CircularHubAdapter.ViewHolder>() {

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

        fun bind(item: CircularHubItem) {
            tvHubIcon.text = item.iconEmoji
            tvHubTitle.text = item.title

            itemView.setOnClickListener {
                onHubClick(item)
            }
        }
    }
}
