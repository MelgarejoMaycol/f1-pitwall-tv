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
    private var itemsList: List<Meeting> = emptyList(),
    private var selectedMeetingKey: Long? = null,
    private val onGpClick: (Meeting) -> Unit
) : RecyclerView.Adapter<GpCarouselAdapter.ViewHolder>() {

    val items: List<Meeting> get() = itemsList
    private var roundLabels: Map<Long, String> = emptyMap()

    fun updateData(newItems: List<Meeting>, selectKey: Long? = null) {
        itemsList = newItems
        if (selectKey != null) {
            selectedMeetingKey = selectKey
        }
        var roundCount = 0
        roundLabels = itemsList.associate { m ->
            val isTest = m.meetingName?.contains("test", ignoreCase = true) == true
            val label = if (isTest) "TEST" else {
                roundCount++
                String.format("R%02d", roundCount)
            }
            m.meetingKey to label
        }
        notifyDataSetChanged()
    }

    fun setSelectedKey(key: Long) {
        val oldKey = selectedMeetingKey
        if (oldKey == key) return
        selectedMeetingKey = key
        val oldIndex = itemsList.indexOfFirst { it.meetingKey == oldKey }
        val newIndex = itemsList.indexOfFirst { it.meetingKey == key }
        if (oldIndex >= 0) notifyItemChanged(oldIndex)
        if (newIndex >= 0) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gp_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(itemsList[position])
    }

    override fun getItemCount(): Int = itemsList.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivGpFlag: ImageView = itemView.findViewById(R.id.ivGpFlag)
        private val tvGpRound: TextView = itemView.findViewById(R.id.tvGpRound)
        private val tvGpStatus: TextView = itemView.findViewById(R.id.tvGpStatus)
        private val tvGpName: TextView = itemView.findViewById(R.id.tvGpName)
        private val tvGpCircuit: TextView = itemView.findViewById(R.id.tvGpCircuit)
        private val tvGpDates: TextView = itemView.findViewById(R.id.tvGpDates)

        fun bind(meeting: Meeting) {
            val roundLabel = roundLabels[meeting.meetingKey] ?: "GP"
            tvGpRound.text = roundLabel
            if (roundLabel == "TEST") {
                tvGpRound.setTextColor(Color.parseColor("#9DA4B5"))
            } else {
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

            val isSelected = (selectedMeetingKey != null && meeting.meetingKey == selectedMeetingKey)

            // Status Badge
            if (TimeUtils.isCurrentlyLive(meeting.dateStart, meeting.dateEnd)) {
                tvGpStatus.text = "🔴 EN VIVO"
                tvGpStatus.setBackgroundColor(Color.parseColor("#E10600"))
            } else if (TimeUtils.isCompleted(meeting.dateEnd)) {
                tvGpStatus.text = "🏁 COMPLETADA"
                tvGpStatus.setBackgroundColor(Color.parseColor("#15803D"))
            } else {
                if (isSelected) {
                    tvGpStatus.text = "⭐ SIGUIENTE GP"
                    tvGpStatus.setBackgroundColor(Color.parseColor("#0284C7"))
                } else {
                    tvGpStatus.text = "PRÓXIMA"
                    tvGpStatus.setBackgroundColor(Color.parseColor("#2563EB"))
                }
            }

            itemView.isSelected = isSelected

            itemView.setOnClickListener {
                selectedMeetingKey = meeting.meetingKey
                notifyDataSetChanged()
                onGpClick(meeting)
            }
        }
    }
}
