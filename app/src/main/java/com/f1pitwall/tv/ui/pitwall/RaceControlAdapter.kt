package com.f1pitwall.tv.ui.pitwall

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.model.RaceControl

class RaceControlAdapter(
    private var items: List<RaceControl> = emptyList()
) : RecyclerView.Adapter<RaceControlAdapter.ViewHolder>() {

    fun updateData(newItems: List<RaceControl>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_race_control_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRcFlagBadge: TextView = itemView.findViewById(R.id.tvRcFlagBadge)
        private val tvRcMessage: TextView = itemView.findViewById(R.id.tvRcMessage)
        private val tvRcCategoryTime: TextView = itemView.findViewById(R.id.tvRcCategoryTime)

        fun bind(item: RaceControl) {
            tvRcMessage.text = item.message ?: "Mensaje de Dirección de Carrera"

            val timeStr = item.date?.let {
                if (it.length >= 19) it.substring(11, 19) else it
            } ?: "--:--:--"

            val category = item.category ?: "FIA"
            val sectorInfo = item.sector?.let { " • Sector $it" } ?: ""
            tvRcCategoryTime.text = "$category$sectorInfo • $timeStr UTC"

            val flag = item.flag?.uppercase() ?: ""
            val msgUpper = item.message?.uppercase() ?: ""

            when {
                flag.contains("RED") || msgUpper.contains("RED FLAG") -> {
                    tvRcFlagBadge.text = "🔴 ROJA"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                    tvRcFlagBadge.setTextColor(Color.WHITE)
                }
                flag.contains("DOUBLE") || msgUpper.contains("DOUBLE YELLOW") -> {
                    tvRcFlagBadge.text = "⚠️ DOBLE AMARILLA"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#F59E0B"))
                    tvRcFlagBadge.setTextColor(Color.BLACK)
                }
                flag.contains("YELLOW") || msgUpper.contains("YELLOW") -> {
                    tvRcFlagBadge.text = "🟡 AMARILLA"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#EAB308"))
                    tvRcFlagBadge.setTextColor(Color.BLACK)
                }
                flag.contains("CLEAR") || flag.contains("GREEN") || msgUpper.contains("CLEAR") -> {
                    tvRcFlagBadge.text = "🟢 VERDE"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#22C55E"))
                    tvRcFlagBadge.setTextColor(Color.WHITE)
                }
                msgUpper.contains("SAFETY CAR") -> {
                    tvRcFlagBadge.text = "⚠️ SAFETY CAR"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#F97316"))
                    tvRcFlagBadge.setTextColor(Color.WHITE)
                }
                msgUpper.contains("VIRTUAL") || msgUpper.contains("VSC") -> {
                    tvRcFlagBadge.text = "⏱️ VSC"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#F59E0B"))
                    tvRcFlagBadge.setTextColor(Color.BLACK)
                }
                msgUpper.contains("DRS") -> {
                    tvRcFlagBadge.text = "⚡ DRS"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#38BDF8"))
                    tvRcFlagBadge.setTextColor(Color.BLACK)
                }
                msgUpper.contains("INVESTIGAT") || msgUpper.contains("PENALTY") -> {
                    tvRcFlagBadge.text = "⚖️ SANCIÓN"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#EC4899"))
                    tvRcFlagBadge.setTextColor(Color.WHITE)
                }
                else -> {
                    tvRcFlagBadge.text = "ℹ️ AVISO"
                    tvRcFlagBadge.setBackgroundColor(Color.parseColor("#475569"))
                    tvRcFlagBadge.setTextColor(Color.WHITE)
                }
            }
        }
    }
}
