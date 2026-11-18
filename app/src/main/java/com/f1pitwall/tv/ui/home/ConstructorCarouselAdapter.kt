package com.f1pitwall.tv.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.data.model.ConstructorCardItem

class ConstructorCarouselAdapter(
    private val items: List<ConstructorCardItem>,
    private val onTeamClick: (ConstructorCardItem) -> Unit
) : RecyclerView.Adapter<ConstructorCarouselAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_constructor_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewConstructorColor: View = itemView.findViewById(R.id.viewConstructorColor)
        private val tvConstructorName: TextView = itemView.findViewById(R.id.tvConstructorName)
        private val tvConstructorDrivers: TextView = itemView.findViewById(R.id.tvConstructorDrivers)

        fun bind(item: ConstructorCardItem) {
            tvConstructorName.text = item.teamName
            tvConstructorDrivers.text = item.drivers.joinToString(" • ")

            try {
                val hex = if (item.teamColor.startsWith("#")) item.teamColor else "#${item.teamColor}"
                viewConstructorColor.setBackgroundColor(Color.parseColor(hex))
            } catch (e: Exception) {
                viewConstructorColor.setBackgroundColor(Color.RED)
            }

            itemView.setOnClickListener {
                onTeamClick(item)
            }
        }
    }
}
