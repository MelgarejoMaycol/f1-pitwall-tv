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
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.model.Meeting

class GpCarouselAdapter(
    private var items: List<Meeting> = emptyList(),
    private val onGpClick: (Meeting) -> Unit
) : RecyclerView.Adapter<GpCarouselAdapter.ViewHolder>() {

    fun updateData(newItems: List<Meeting>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gp_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivGpFlag: ImageView = itemView.findViewById(R.id.ivGpFlag)
        private val tvGpRound: TextView = itemView.findViewById(R.id.tvGpRound)
        private val tvGpStatus: TextView = itemView.findViewById(R.id.tvGpStatus)
        private val tvGpName: TextView = itemView.findViewById(R.id.tvGpName)
        private val tvGpCircuit: TextView = itemView.findViewById(R.id.tvGpCircuit)
        private val tvGpDates: TextView = itemView.findViewById(R.id.tvGpDates)

        fun bind(meeting: Meeting, position: Int) {
            val isTesting = meeting.meetingName?.contains("Testing", true) == true
            if (isTesting) {
                tvGpRound.text = "TEST"
                tvGpRound.setTextColor(Color.parseColor("#9DA4B5"))
            } else {
                tvGpRound.text = String.format("R%02d", position + 1)
                tvGpRound.setTextColor(Color.parseColor("#E10600"))
            }

            tvGpName.text = meeting.meetingName ?: "Gran Premio"
            tvGpCircuit.text = "${meeting.location ?: ""} • ${meeting.circuitShortName ?: ""}"
            tvGpDates.text = TimeUtils.formatColombiaDateRange(meeting.dateStart, meeting.dateEnd)

            if (!meeting.countryFlag.isNullOrEmpty()) {
                ivGpFlag.visibility = View.VISIBLE
                ivGpFlag.load(meeting.countryFlag) { crossfade(true) }
            } else {
                ivGpFlag.visibility = View.GONE
            }

            // Status Badge
            if (TimeUtils.isCurrentlyLive(meeting.dateStart, meeting.dateEnd)) {
                tvGpStatus.text = "🔴 EN VIVO"
                tvGpStatus.setBackgroundColor(Color.parseColor("#E10600"))
            } else if (TimeUtils.isCompleted(meeting.dateEnd)) {
                tvGpStatus.text = "COMPLETADA"
                tvGpStatus.setBackgroundColor(Color.parseColor("#15803D"))
            } else {
                tvGpStatus.text = "PRÓXIMA"
                tvGpStatus.setBackgroundColor(Color.parseColor("#2563EB"))
            }

            itemView.setOnClickListener {
                onGpClick(meeting)
            }
        }
    }
}
