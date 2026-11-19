package com.f1pitwall.tv.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.model.Meeting
import java.text.SimpleDateFormat
import java.util.*

class MeetingAdapter(
    items: List<Meeting> = emptyList(),
    private val onMeetingClick: (Meeting) -> Unit
) : ListAdapter<Meeting, MeetingAdapter.ViewHolder>(MeetingDiffCallback) {

    init {
        if (items.isNotEmpty()) {
            submitList(items)
        }
    }

    val meetings: List<Meeting> get() = currentList

    fun updateData(newItems: List<Meeting>) {
        submitList(newItems)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meeting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

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
                val roundNum = if (currentList.firstOrNull()?.meetingName?.contains("Testing", true) == true) position else position + 1
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
            val (datesStr, statusStr, statusColor) = formatDatesAndStatus(meeting.dateStart, meeting.dateEnd)
            tvDates.text = datesStr
            tvStatusBadge.text = statusStr
            tvStatusBadge.setBackgroundColor(statusColor)

            itemView.setOnClickListener {
                onMeetingClick(meeting)
            }
        }

        private fun formatDatesAndStatus(startIso: String?, endIso: String?): Triple<String, String, Int> {
            if (startIso == null) return Triple("--", "PROGRAMADA", Color.parseColor("#3B82F6"))
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")

                val startDate = inputFormat.parse(startIso.substring(0, 19))
                val endDate = endIso?.let { inputFormat.parse(it.substring(0, 19)) }

                val outFormat = SimpleDateFormat("d MMM", Locale("es", "ES"))
                outFormat.timeZone = TimeZone.getDefault()

                val now = Date()
                val dateStr = if (startDate != null && endDate != null) {
                    "${outFormat.format(startDate)} - ${outFormat.format(endDate)}"
                } else if (startDate != null) {
                    outFormat.format(startDate)
                } else "--"

                if (endDate != null && now.after(endDate)) {
                    Triple(dateStr, "FINALIZADA", Color.parseColor("#15803D")) // Green
                } else if (startDate != null && endDate != null && now.after(startDate) && now.before(endDate)) {
                    Triple(dateStr, "🔴 EN VIVO", Color.parseColor("#E10600")) // Red Live
                } else {
                    Triple(dateStr, "PRÓXIMA", Color.parseColor("#2563EB")) // Blue
                }
            } catch (e: Exception) {
                Triple(startIso.take(10), "PROGRAMADA", Color.parseColor("#3B82F6"))
            }
        }
    }

    object MeetingDiffCallback : DiffUtil.ItemCallback<Meeting>() {
        override fun areItemsTheSame(oldItem: Meeting, newItem: Meeting): Boolean {
            return oldItem.meetingKey == newItem.meetingKey
        }

        override fun areContentsTheSame(oldItem: Meeting, newItem: Meeting): Boolean {
            return oldItem == newItem
        }
    }
}
