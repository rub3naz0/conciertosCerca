package org.rubenazo.conciertosfront.core.domain.model

data class SalaWithConcerts(
    val sala: SalaConcierto,
    val upcomingConcerts: List<VenueConcert>,
)

data class VenueConcert(
    val id: String,
    val date: String,
    val time: String?,
    val artistNames: List<String>,
)
