package com.f1pitwall.tv.model

import com.google.gson.annotations.SerializedName

data class Meeting(
    @SerializedName("meeting_key") val meetingKey: Long,
    @SerializedName("meeting_name") val meetingName: String?,
    @SerializedName("meeting_official_name") val officialName: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("country_name") val countryName: String?,
    @SerializedName("country_code") val countryCode: String?,
    @SerializedName("country_flag") val countryFlag: String?,
    @SerializedName("circuit_key") val circuitKey: Int?,
    @SerializedName("circuit_short_name") val circuitShortName: String?,
    @SerializedName("circuit_image") val circuitImage: String?,
    @SerializedName("date_start") val dateStart: String?,
    @SerializedName("date_end") val dateEnd: String?,
    @SerializedName("gmt_offset") val gmtOffset: String?,
    @SerializedName("year") val year: Int?,
    @SerializedName("is_cancelled") val isCancelled: Boolean?
)

data class Session(
    @SerializedName("session_key") val sessionKey: Long,
    @SerializedName("session_name") val sessionName: String?,
    @SerializedName("session_type") val sessionType: String?,
    @SerializedName("date_start") val dateStart: String?,
    @SerializedName("date_end") val dateEnd: String?,
    @SerializedName("meeting_key") val meetingKey: Long,
    @SerializedName("circuit_key") val circuitKey: Long? = null,
    @SerializedName("circuit_short_name") val circuitShortName: String?,
    @SerializedName("country_name") val countryName: String?,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("is_cancelled") val isCancelled: Boolean? = false
)

data class Driver(
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("broadcast_name") val broadcastName: String?,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("name_acronym") val acronym: String?,
    @SerializedName("team_name") val teamName: String?,
    @SerializedName("team_colour") val teamColour: String?,
    @SerializedName("headshot_url") val headshotUrl: String?
)

data class Position(
    @SerializedName("date") val date: String?,
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("position") val position: Int,
    @SerializedName("session_key") val sessionKey: Long
)

data class Interval(
    @SerializedName("date") val date: String?,
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("gap_to_leader") val gapToLeader: Any?,
    @SerializedName("interval") val interval: Any?
)

data class Lap(
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("lap_number") val lapNumber: Int,
    @SerializedName("lap_duration") val lapDuration: Double?,
    @SerializedName("duration_sector_1") val s1: Double?,
    @SerializedName("duration_sector_2") val s2: Double?,
    @SerializedName("duration_sector_3") val s3: Double?,
    @SerializedName("i1_speed") val i1Speed: Int? = null,
    @SerializedName("i2_speed") val i2Speed: Int? = null,
    @SerializedName("st_speed") val stSpeed: Int? = null,
    @SerializedName("is_pit_out_lap") val isPitOutLap: Boolean? = null
)

data class Stint(
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("stint_number") val stintNumber: Int,
    @SerializedName("compound") val compound: String?,
    @SerializedName("lap_start") val lapStart: Int?,
    @SerializedName("lap_end") val lapEnd: Int?,
    @SerializedName("tyre_age_at_start") val tyreAgeAtStart: Int?
)

data class TeamRadio(
    @SerializedName("date") val date: String?,
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("recording_url") val recordingUrl: String?
)

data class RaceControl(
    @SerializedName("date") val date: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("flag") val flag: String?,
    @SerializedName("scope") val scope: String?,
    @SerializedName("sector") val sector: Int? = null,
    @SerializedName("driver_number") val driverNumber: Int? = null,
    @SerializedName("lap_number") val lapNumber: Int? = null
)

data class Weather(
    @SerializedName("air_temperature") val airTemp: Double?,
    @SerializedName("track_temperature") val trackTemp: Double?,
    @SerializedName("humidity") val humidity: Double?,
    @SerializedName("rainfall") val rainfall: Int?,
    @SerializedName("wind_speed") val windSpeed: Double?,
    @SerializedName("wind_direction") val windDirection: Int? = null,
    @SerializedName("pressure") val pressure: Double? = null
)

data class CarLocation(
    @SerializedName("date") val date: String?,
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("x") val x: Float,
    @SerializedName("y") val y: Float,
    @SerializedName("z") val z: Float? = null
)

data class PitStop(
    @SerializedName("date") val date: String?,
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("lap_number") val lapNumber: Int?,
    @SerializedName("pit_duration") val pitDuration: Double?,
    @SerializedName("stop_duration") val stopDuration: Double?,
    @SerializedName("lane_duration") val laneDuration: Double?
)

data class DriverTyreStrategyItem(
    val driverNumber: Int,
    val position: Int,
    val acronym: String,
    val fullName: String,
    val teamName: String,
    val teamColor: String,
    val currentCompound: String,
    val tyreAge: Int,
    val totalStints: Int,
    val pitStopsCount: Int,
    val lastPitStopDuration: String,
    val stintsHistory: String
)

data class DriverLeaderboardItem(
    val driverNumber: Int,
    val position: Int,
    val acronym: String,
    val fullName: String,
    val teamName: String,
    val teamColor: String,
    val gapToLeader: String,
    val interval: String,
    val lastLap: String,
    val s1: String,
    val s2: String,
    val s3: String,
    val tyreCompound: String,
    val tyreAge: Int
)

data class CarTelemetry(
    @SerializedName("speed") val speed: Int?,
    @SerializedName("throttle") val throttle: Int?,
    @SerializedName("brake") val brake: Int?,
    @SerializedName("rpm") val rpm: Int?,
    @SerializedName("n_gear") val gear: Int?,
    @SerializedName("drs") val drs: Int?,
    @SerializedName("driver_number") val driverNumber: Int?,
    @SerializedName("date") val date: String?
)
