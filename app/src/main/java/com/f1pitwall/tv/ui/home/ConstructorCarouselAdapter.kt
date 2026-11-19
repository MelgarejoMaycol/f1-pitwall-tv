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
    private var items: List<ConstructorCardItem> = emptyList(),
    private val onTeamClick: (ConstructorCardItem) -> Unit
) : RecyclerView.Adapter<ConstructorCarouselAdapter.ViewHolder>() {

    fun updateData(newItems: List<ConstructorCardItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_constructor_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val viewConstructorColor: View = itemView.findViewById(R.id.viewConstructorColor)
        private val tvConstructorRank: TextView = itemView.findViewById(R.id.tvConstructorRank)
        private val tvConstructorWins: TextView = itemView.findViewById(R.id.tvConstructorWins)
        private val tvConstructorName: TextView = itemView.findViewById(R.id.tvConstructorName)
        private val tvConstructorDrivers: TextView = itemView.findViewById(R.id.tvConstructorDrivers)
        private val tvConstructorPoints: TextView = itemView.findViewById(R.id.tvConstructorPoints)
        private val tvConstructorGap: TextView = itemView.findViewById(R.id.tvConstructorGap)

        fun bind(item: ConstructorCardItem, position: Int) {
            val rankPos = if (item.position > 0) item.position else (position + 1)
            tvConstructorRank.text = "${rankPos}º"

            when (rankPos) {
                1 -> tvConstructorRank.setTextColor(Color.parseColor("#FFB800"))
                2 -> tvConstructorRank.setTextColor(Color.parseColor("#E2E8F0"))
                3 -> tvConstructorRank.setTextColor(Color.parseColor("#CD7F32"))
                else -> tvConstructorRank.setTextColor(Color.parseColor("#9DA4B5"))
            }

            tvConstructorWins.text = if (item.wins > 0) "🏆 ${item.wins} Vict." else "0 Vict."
            tvConstructorName.text = item.teamName
            tvConstructorDrivers.text = item.drivers.joinToString(" • ")
            tvConstructorPoints.text = "${item.points} pts"
            tvConstructorGap.text = item.gapToLeader ?: if (rankPos == 1) "LÍDER" else ""

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
