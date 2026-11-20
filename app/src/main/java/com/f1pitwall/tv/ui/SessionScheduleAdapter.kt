package com.f1pitwall.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.f1pitwall.tv.R
import com.f1pitwall.tv.model.Session
import com.f1pitwall.tv.core.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.*

class SessionScheduleAdapter(
    private var items: List<Session> = emptyList(),
    private val onSessionClick: (Session) -> Unit
) : RecyclerView.Adapter<SessionScheduleAdapter.ViewHolder>() {

    fun updateData(newItems: List<Session>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSessionTypeTag: TextView = itemView.findViewById(R.id.tvSessionTypeTag)
        private val tvSessionName: TextView = itemView.findViewById(R.id.tvSessionName)
        private val tvSessionTime: TextView = itemView.findViewById(R.id.tvSessionTime)
        private val btnViewSessionData: Button = itemView.findViewById(R.id.btnViewSessionData)

        fun bind(session: Session) {
            val name = session.sessionName ?: "Sesión"
            tvSessionName.text = name

            val shortTag = when {
                name.contains("Practice 1", true) -> "FP1"
                name.contains("Practice 2", true) -> "FP2"
                name.contains("Practice 3", true) -> "FP3"
                name.contains("Qualifying", true) -> "Q"
                name.contains("Sprint Shootout", true) -> "SS"
                name.contains("Sprint", true) -> "SPR"
                name.contains("Race", true) -> "RAC"
                else -> "SES"
            }
            tvSessionTypeTag.text = shortTag

            tvSessionTime.text = formatSessionDateTime(session.dateStart)

            btnViewSessionData.setOnClickListener {
                onSessionClick(session)
            }
            itemView.setOnClickListener {
                onSessionClick(session)
            }
        }

        private fun formatSessionDateTime(dateIso: String?): String {
            return TimeUtils.formatToColombiaTime(dateIso)
        }
    }
}
