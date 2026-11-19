package com.f1pitwall.tv.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.data.model.DriverCardItem

class DriverCarouselAdapter(
    private var items: List<DriverCardItem> = emptyList(),
    private val onDriverClick: (DriverCardItem) -> Unit
) : RecyclerView.Adapter<DriverCarouselAdapter.ViewHolder>() {

    fun updateData(newItems: List<DriverCardItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_driver_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDriverRank: TextView = itemView.findViewById(R.id.tvDriverRank)
        private val tvDriverPoints: TextView = itemView.findViewById(R.id.tvDriverPoints)
        private val tvDriverGap: TextView = itemView.findViewById(R.id.tvDriverGap)
        private val ivDriverPhoto: ImageView = itemView.findViewById(R.id.ivDriverPhoto)
        private val viewDriverTeamColor: View = itemView.findViewById(R.id.viewDriverTeamColor)
        private val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        private val tvDriverTeam: TextView = itemView.findViewById(R.id.tvDriverTeam)

        fun bind(driver: DriverCardItem, position: Int) {
            val rankPos = if (driver.position > 0) driver.position else (position + 1)
            tvDriverRank.text = "${rankPos}º"

            when (rankPos) {
                1 -> {
                    tvDriverRank.setTextColor(Color.parseColor("#FFB800")) // Gold
                    tvDriverGap.setTextColor(Color.parseColor("#22C55E"))
                }
                2 -> {
                    tvDriverRank.setTextColor(Color.parseColor("#E2E8F0")) // Silver
                    tvDriverGap.setTextColor(Color.parseColor("#9DA4B5"))
                }
                3 -> {
                    tvDriverRank.setTextColor(Color.parseColor("#CD7F32")) // Bronze
                    tvDriverGap.setTextColor(Color.parseColor("#9DA4B5"))
                }
                else -> {
                    tvDriverRank.setTextColor(Color.parseColor("#9DA4B5"))
                    tvDriverGap.setTextColor(Color.parseColor("#687082"))
                }
            }

            tvDriverPoints.text = "${driver.points} pts"
            tvDriverGap.text = driver.gapToLeader ?: if (rankPos == 1) "LÍDER" else ""
            tvDriverName.text = driver.fullName
            tvDriverTeam.text = "${driver.teamName} • #${driver.driverNumber}"

            try {
                val hex = if (driver.teamColor.startsWith("#")) driver.teamColor else "#${driver.teamColor}"
                viewDriverTeamColor.setBackgroundColor(Color.parseColor(hex))
            } catch (e: Exception) {
                viewDriverTeamColor.setBackgroundColor(Color.RED)
            }

            if (!driver.headshotUrl.isNullOrEmpty()) {
                ivDriverPhoto.load(driver.headshotUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_driver_helmet)
                    error(R.drawable.ic_driver_helmet)
                }
            } else {
                ivDriverPhoto.setImageResource(R.drawable.ic_driver_helmet)
            }

            itemView.setOnClickListener {
                onDriverClick(driver)
            }
        }
    }
}
