package com.f1pitwall.tv.ui.pitwall

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.model.DriverTyreStrategyItem

class TyreStrategyAdapter(
    private var items: List<DriverTyreStrategyItem> = emptyList(),
    private val onItemClick: (DriverTyreStrategyItem) -> Unit = {}
) : RecyclerView.Adapter<TyreStrategyAdapter.ViewHolder>() {

    fun updateData(newItems: List<DriverTyreStrategyItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tyre_strategy, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTyrePos: TextView = itemView.findViewById(R.id.tvTyrePos)
        private val viewTyreTeamColor: View = itemView.findViewById(R.id.viewTyreTeamColor)
        private val tvTyreDriverName: TextView = itemView.findViewById(R.id.tvTyreDriverName)
        private val tvTyreStintsHistory: TextView = itemView.findViewById(R.id.tvTyreStintsHistory)
        private val tvTyrePitCount: TextView = itemView.findViewById(R.id.tvTyrePitCount)
        private val tvTyrePitDuration: TextView = itemView.findViewById(R.id.tvTyrePitDuration)
        private val tvTyreBadge: TextView = itemView.findViewById(R.id.tvTyreBadge)

        fun bind(item: DriverTyreStrategyItem) {
            tvTyrePos.text = if (item.position < 99) item.position.toString() else "-"
            tvTyreDriverName.text = "${item.acronym} • ${item.fullName}"
            tvTyreStintsHistory.text = item.stintsHistory
            tvTyrePitCount.text = "${item.pitStopsCount} ${if (item.pitStopsCount == 1) "PARADA" else "PARADAS"}"
            tvTyrePitDuration.text = "Box: ${item.lastPitStopDuration}"

            try {
                val hex = if (item.teamColor.startsWith("#")) item.teamColor else "#${item.teamColor}"
                viewTyreTeamColor.setBackgroundColor(Color.parseColor(hex))
            } catch (e: Exception) {
                viewTyreTeamColor.setBackgroundColor(Color.RED)
            }

            val compoundUpper = item.currentCompound.uppercase()
            val (shortCode, badgeColor, textColor) = when {
                compoundUpper.contains("SOFT") -> Triple("S", "#EF4444", Color.WHITE)
                compoundUpper.contains("MEDIUM") -> Triple("M", "#FACC15", Color.BLACK)
                compoundUpper.contains("HARD") -> Triple("H", "#FFFFFF", Color.BLACK)
                compoundUpper.contains("INTER") -> Triple("I", "#10B981", Color.WHITE)
                compoundUpper.contains("WET") -> Triple("W", "#0EA5E9", Color.WHITE)
                else -> Triple("?", "#6B7280", Color.WHITE)
            }

            tvTyreBadge.text = "$shortCode ${item.tyreAge}v"
            tvTyreBadge.setBackgroundColor(Color.parseColor(badgeColor))
            tvTyreBadge.setTextColor(textColor)

            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
