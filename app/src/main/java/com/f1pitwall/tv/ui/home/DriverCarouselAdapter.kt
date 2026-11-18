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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDriverNumber: TextView = itemView.findViewById(R.id.tvDriverNumber)
        private val ivDriverPhoto: ImageView = itemView.findViewById(R.id.ivDriverPhoto)
        private val viewDriverTeamColor: View = itemView.findViewById(R.id.viewDriverTeamColor)
        private val tvDriverName: TextView = itemView.findViewById(R.id.tvDriverName)
        private val tvDriverTeam: TextView = itemView.findViewById(R.id.tvDriverTeam)

        fun bind(driver: DriverCardItem) {
            tvDriverNumber.text = driver.driverNumber.toString()
            tvDriverName.text = driver.fullName
            tvDriverTeam.text = driver.teamName

            try {
                val hex = if (driver.teamColor.startsWith("#")) driver.teamColor else "#${driver.teamColor}"
                viewDriverTeamColor.setBackgroundColor(Color.parseColor(hex))
            } catch (e: Exception) {
                viewDriverTeamColor.setBackgroundColor(Color.RED)
            }

            if (!driver.headshotUrl.isNullOrEmpty()) {
                ivDriverPhoto.load(driver.headshotUrl) {
                    crossfade(true)
                }
            }

            itemView.setOnClickListener {
                onDriverClick(driver)
            }
        }
    }
}
