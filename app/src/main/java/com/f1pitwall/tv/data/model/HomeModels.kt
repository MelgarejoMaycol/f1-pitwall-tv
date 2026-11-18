package com.f1pitwall.tv.data.model

enum class HeroStateType {
    LIVE,
    UPCOMING,
    COMPLETED
}

data class HeroBannerData(
    val type: HeroStateType,
    val grandPrixName: String,
    val officialName: String,
    val circuitName: String,
    val location: String,
    val countryName: String,
    val dateDisplay: String,
    val timingDisplay: String,
    val countdownText: String,
    val flagUrl: String?,
    val circuitImageUrl: String?,
    val meetingKey: Long,
    val sessionKey: Long?
)

data class CircularHubItem(
    val id: String,
    val title: String,
    val iconEmoji: String,
    val badgeText: String? = null
)

data class DriverCardItem(
    val driverNumber: Int,
    val acronym: String,
    val fullName: String,
    val teamName: String,
    val teamColor: String,
    val headshotUrl: String?
)

data class ConstructorCardItem(
    val teamName: String,
    val teamColor: String,
    val drivers: List<String>,
    val pointsText: String? = null
)

data class CircuitCardItem(
    val circuitKey: Int,
    val circuitName: String,
    val location: String,
    val country: String,
    val circuitImageUrl: String?
)
