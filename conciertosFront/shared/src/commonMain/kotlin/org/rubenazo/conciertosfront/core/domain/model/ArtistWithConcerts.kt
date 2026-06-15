package org.rubenazo.conciertosfront.core.domain.model

data class ArtistWithConcerts(
    val artist: Artist,
    val upcomingConcerts: List<UpcomingConcert>,
)

data class UpcomingConcert(
    val id: String,
    val date: String,
    val time: String?,
    val salaName: String,
    val salaCity: String,
)
