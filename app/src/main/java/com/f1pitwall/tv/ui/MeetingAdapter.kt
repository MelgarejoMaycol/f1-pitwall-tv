package com.f1pitwall.tv.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.model.Meeting
import com.f1pitwall.tv.core.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.*

class MeetingAdapter(
    private var items: List<Meeting> = emptyList(),
    private val onMeetingClick: (Meeting) -> Unit
) : RecyclerView.Adapter<MeetingAdapter.ViewHolder>() {

    fun updateData(newItems: List<Meeting>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meeting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRoundBadge: TextView = itemView.findViewById(R.id.tvRoundBadge)
        private val ivFlag: ImageView = itemView.findViewById(R.id.ivFlag)
        private val tvMeetingName: TextView = itemView.findViewById(R.id.tvMeetingName)
        private val tvCircuitAndLocation: TextView = itemView.findViewById(R.id.tvCircuitAndLocation)
        private val tvDates: TextView = itemView.findViewById(R.id.tvDates)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)

        fun bind(meeting: Meeting, position: Int) {
            val isTesting = meeting.meetingName?.contains("Testing", true) == true
            if (isTesting) {
                tvRoundBadge.text = "TEST"
                tvRoundBadge.setTextColor(Color.parseColor("#9DA4B5"))
            } else {
                val roundNum = if (items.firstOrNull()?.meetingName?.contains("Testing", true) == true) position else position + 1
                tvRoundBadge.text = String.format("R%02d", roundNum)
                tvRoundBadge.setTextColor(Color.parseColor("#E10600"))
            }

            tvMeetingName.text = meeting.meetingName ?: "Gran Premio"
            tvCircuitAndLocation.text = "${meeting.location ?: ""} • ${meeting.circuitShortName ?: "Circuito"}"

            // Load Flag with Coil
            if (!meeting.countryFlag.isNullOrEmpty()) {
                ivFlag.visibility = View.VISIBLE
                ivFlag.load(meeting.countryFlag) {
                    crossfade(true)
                }
            } else {
                ivFlag.visibility = View.GONE
            }

            // Dates & Status
            val (datesStr, statusStr, statusColor) = formatDatesAndStatus(meeting)
            tvDates.text = datesStr
            tvStatusBadge.text = statusStr
            tvStatusBadge.setBackgroundColor(statusColor)

            itemView.setOnClickListener {
                onMeetingClick(meeting)
            }
        }

        private fun formatDatesAndStatus(meeting: Meeting): Triple<String, String, Int> {
            if (meeting.isCancelled == true) {
                return Triple("Evento cancelado", "CANCELADO", Color.parseColor("#6B7280"))
            }

            val startDate = TimeUtils.parseUtc(meeting.dateStart)
                ?: return Triple("Horario por confirmar", "FECHA PENDIENTE", Color.parseColor("#6B7280"))
            val endDate = TimeUtils.parseUtc(meeting.dateEnd)

            val outFormat = SimpleDateFormat("d MMM", Locale("es", "CO"))
            outFormat.timeZone = TimeZone.getTimeZone("America/Bogota")
            val dateStr = if (endDate != null) {
                "${outFormat.format(startDate)} - ${outFormat.format(endDate)}"
            } else {
                outFormat.format(startDate)
            }

            val now = Date()
            return when {
                endDate != null && now.after(endDate) ->
                    Triple(dateStr, "FINALIZADA", Color.parseColor("#15803D"))
                !now.before(startDate) && (endDate == null || !now.after(endDate)) ->
                    Triple(dateStr, "🔴 EN VIVO", Color.parseColor("#E10600"))
                else ->
                    Triple(dateStr, "PRÓXIMA", Color.parseColor("#2563EB"))
            }
        }
    }
}
