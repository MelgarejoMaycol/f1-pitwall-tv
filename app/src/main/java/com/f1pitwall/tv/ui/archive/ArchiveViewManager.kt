package com.f1pitwall.tv.ui.archive

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.f1pitwall.tv.R
import com.f1pitwall.tv.core.config.AppConfig
import com.f1pitwall.tv.core.util.AppLogger
import com.f1pitwall.tv.core.util.TimeUtils
import com.f1pitwall.tv.data.repository.F1Repository
import com.f1pitwall.tv.model.Meeting
import com.f1pitwall.tv.model.Session
import com.f1pitwall.tv.network.OpenF1ApiClient
import com.f1pitwall.tv.ui.LeaderboardAdapter
import com.f1pitwall.tv.ui.MeetingAdapter
import com.f1pitwall.tv.ui.SessionScheduleAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ArchiveViewManager(
    private val rootView: View,
    private val repository: F1Repository,
    private val api: OpenF1ApiClient,
    private val scope: CoroutineScope,
    private val onMeetingClicked: (Meeting) -> Unit = {}
) {
    val btnYear2024: Button = rootView.findViewById(R.id.btnYear2024)
    val btnYear2025: Button = rootView.findViewById(R.id.btnYear2025)
    val btnYear2026: Button = rootView.findViewById(R.id.btnYear2026)
    val rvMeetings: RecyclerView = rootView.findViewById(R.id.rvMeetings)
    val tvSelectedGpName: TextView = rootView.findViewById(R.id.tvSelectedGpName)
    val tvSelectedGpCircuit: TextView = rootView.findViewById(R.id.tvSelectedGpCircuit)
    val tvSelectedGpDates: TextView = rootView.findViewById(R.id.tvSelectedGpDates)
    val ivCircuitTrackMap: ImageView = rootView.findViewById(R.id.ivCircuitTrackMap)
    val btnViewSchedule: Button = rootView.findViewById(R.id.btnViewSchedule)
    val btnViewResults: Button = rootView.findViewById(R.id.btnViewResults)
    val rvSessionsSchedule: RecyclerView = rootView.findViewById(R.id.rvSessionsSchedule)
    val rvArchiveResults: RecyclerView = rootView.findViewById(R.id.rvArchiveResults)
    val layoutEmptyArchiveResults: LinearLayout = rootView.findViewById(R.id.layoutEmptyArchiveResults)
    val tvEmptyArchiveResults: TextView = rootView.findViewById(R.id.tvEmptyArchiveResults)

    lateinit var meetingAdapter: MeetingAdapter
    lateinit var sessionScheduleAdapter: SessionScheduleAdapter
    lateinit var archiveResultsAdapter: LeaderboardAdapter

    var selectedYear: Int = AppConfig.DEFAULT_SEASON
        private set
    var selectedMeeting: Meeting? = null
        private set

    private var activeCalendarJob: Job? = null
    private var activeSessionsJob: Job? = null
    private var activeResultsJob: Job? = null

    init {
        setupRecyclerViews()
        setupListeners()
    }

    private fun setupRecyclerViews() {
        val context = rootView.context

        meetingAdapter = MeetingAdapter { meeting ->
            selectedMeeting = meeting
            displayMeetingDetails(meeting)
            onMeetingClicked(meeting)
        }
        rvMeetings.layoutManager = LinearLayoutManager(context)
        rvMeetings.adapter = meetingAdapter

        sessionScheduleAdapter = SessionScheduleAdapter { session ->
            loadHistoricalSession(session)
            showResultsTab()
        }
        rvSessionsSchedule.layoutManager = LinearLayoutManager(context)
        rvSessionsSchedule.adapter = sessionScheduleAdapter

        archiveResultsAdapter = LeaderboardAdapter { item ->
            // Click en piloto de resultados
        }
        rvArchiveResults.layoutManager = LinearLayoutManager(context)
        rvArchiveResults.adapter = archiveResultsAdapter
    }

    private fun setupListeners() {
        btnYear2024.setOnClickListener { selectYear(2024) }
        btnYear2025.setOnClickListener { selectYear(2025) }
        btnYear2026.setOnClickListener { selectYear(2026) }

        btnViewSchedule.setOnClickListener {
            showScheduleTab()
        }

        btnViewResults.setOnClickListener {
            showResultsTab()
        }

        updateYearButtonHighlights()
        showScheduleTab()
    }

    fun selectYear(year: Int) {
        selectedYear = year
        updateYearButtonHighlights()
        loadCalendar(year)
    }

    private fun updateYearButtonHighlights() {
        btnYear2024.isSelected = selectedYear == 2024
        btnYear2025.isSelected = selectedYear == 2025
        btnYear2026.isSelected = selectedYear == 2026
    }

    fun showScheduleTab() {
        btnViewSchedule.isSelected = true
        btnViewResults.isSelected = false
        rvSessionsSchedule.visibility = View.VISIBLE
        rvArchiveResults.visibility = View.GONE
        layoutEmptyArchiveResults.visibility = View.GONE
    }

    fun showResultsTab() {
        btnViewSchedule.isSelected = false
        btnViewResults.isSelected = true
        rvSessionsSchedule.visibility = View.GONE
        if (archiveResultsAdapter.itemsList.isEmpty()) {
            rvArchiveResults.visibility = View.GONE
            layoutEmptyArchiveResults.visibility = View.VISIBLE
        } else {
            rvArchiveResults.visibility = View.VISIBLE
            layoutEmptyArchiveResults.visibility = View.GONE
        }
    }

    fun loadCalendar(year: Int = selectedYear) {
        selectedYear = year
        updateYearButtonHighlights()
        activeCalendarJob?.cancel()
        activeCalendarJob = scope.launch {
            try {
                val meetings = repository.getMeetings(year)
                meetingAdapter.updateData(meetings)

                if (meetings.isNotEmpty()) {
                    val targetMeeting = if (selectedMeeting != null && selectedMeeting!!.year == year) {
                        selectedMeeting!!
                    } else {
                        repository.findSmartSelectedMeeting(year) ?: meetings.first()
                    }

                    displayMeetingDetails(targetMeeting)

                    val index = meetings.indexOfFirst { it.meetingKey == targetMeeting.meetingKey }
                    if (index >= 0) {
                        rvMeetings.scrollToPosition(index)
                    }
                } else {
                    meetingAdapter.updateData(emptyList())
                    sessionScheduleAdapter.updateData(emptyList())
                    archiveResultsAdapter.updateData(emptyList())
                    tvSelectedGpName.text = "Sin eventos registrados para $year"
                    tvSelectedGpCircuit.text = ""
                    tvSelectedGpDates.text = ""
                    ivCircuitTrackMap.visibility = View.GONE
                }
            } catch (e: Exception) {
                AppLogger.w("ArchiveViewManager", "Error al cargar calendario de año $year", e)
                meetingAdapter.updateData(emptyList())
                sessionScheduleAdapter.updateData(emptyList())
                archiveResultsAdapter.updateData(emptyList())
                tvSelectedGpName.text = "Error de conexión al cargar calendario $year"
                tvSelectedGpCircuit.text = "Comprueba tu conexión a internet"
                tvSelectedGpDates.text = ""
                ivCircuitTrackMap.visibility = View.GONE
            }
        }
    }

    fun displayMeetingDetails(meeting: Meeting) {
        selectedMeeting = meeting
        tvSelectedGpName.text = meeting.meetingName ?: "Gran Premio"
        tvSelectedGpCircuit.text = "${meeting.circuitShortName ?: "Circuito"} • ${meeting.countryName ?: ""}"
        tvSelectedGpDates.text = "Fechas: ${TimeUtils.formatColombiaDateRange(meeting.dateStart, meeting.dateEnd)}"

        if (!meeting.circuitImage.isNullOrEmpty()) {
            ivCircuitTrackMap.visibility = View.VISIBLE
            ivCircuitTrackMap.load(meeting.circuitImage) {
                crossfade(true)
            }
        } else {
            ivCircuitTrackMap.visibility = View.GONE
        }

        loadSessionsForMeeting(meeting.meetingKey)
    }

    fun clearMeetingDetails() {
        tvSelectedGpName.text = "Selecciona un Gran Premio"
        tvSelectedGpCircuit.text = "Circuito e información oficial"
        tvSelectedGpDates.text = "Fechas: --"
        ivCircuitTrackMap.visibility = View.GONE
        sessionScheduleAdapter.updateData(emptyList())
        archiveResultsAdapter.updateData(emptyList())
    }

    fun loadSessionsForMeeting(meetingKey: Long) {
        activeSessionsJob?.cancel()
        activeSessionsJob = scope.launch {
            try {
                val sessions = api.getSessions(meetingKey)
                sessionScheduleAdapter.updateData(sessions)

                val raceSession = sessions.find { it.sessionType.equals("Race", true) } ?: sessions.lastOrNull()
                raceSession?.let { s ->
                    loadHistoricalSession(s)
                } ?: run {
                    archiveResultsAdapter.updateData(emptyList())
                    if (btnViewResults.isSelected) {
                        rvArchiveResults.visibility = View.GONE
                        layoutEmptyArchiveResults.visibility = View.VISIBLE
                        tvEmptyArchiveResults.text = "Sin sesiones registradas para este Gran Premio"
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("ArchiveViewManager", "Error al obtener sesiones para meeting $meetingKey", e)
                sessionScheduleAdapter.updateData(emptyList())
                archiveResultsAdapter.updateData(emptyList())
            }
        }
    }

    fun loadHistoricalSession(session: Session) {
        activeResultsJob?.cancel()
        val sName = session.sessionName ?: "Sesión"
        activeResultsJob = scope.launch {
            try {
                val currentMeeting = selectedMeeting
                val items = if (currentMeeting != null) {
                    repository.getHistoricalResults(selectedYear, currentMeeting, session)
                } else {
                    emptyList()
                }

                archiveResultsAdapter.updateData(items)

                if (btnViewResults.isSelected) {
                    if (items.isEmpty()) {
                        rvArchiveResults.visibility = View.GONE
                        layoutEmptyArchiveResults.visibility = View.VISIBLE
                        tvEmptyArchiveResults.text = "Sin tiempos o clasificación oficial disponible para $sName"
                    } else {
                        rvArchiveResults.visibility = View.VISIBLE
                        layoutEmptyArchiveResults.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("ArchiveViewManager", "Error al cargar clasificación histórica para $sName", e)
                archiveResultsAdapter.updateData(emptyList())
                if (btnViewResults.isSelected) {
                    rvArchiveResults.visibility = View.GONE
                    layoutEmptyArchiveResults.visibility = View.VISIBLE
                    tvEmptyArchiveResults.text = "No se pudieron obtener los datos de $sName"
                }
            }
        }
    }

    fun clear() {
        activeCalendarJob?.cancel()
        activeSessionsJob?.cancel()
        activeResultsJob?.cancel()
    }
}
