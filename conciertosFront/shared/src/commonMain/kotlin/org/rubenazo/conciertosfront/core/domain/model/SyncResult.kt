package org.rubenazo.conciertosfront.core.domain.model

data class SyncedConcert(
    val id: String,
    val salaConciertoId: String,
    val artistIds: List<String>,
    val date: String,
    val time: String?,
    val price: String?
)

data class SyncResult(
    val salasCount: Int,
    val artistsCount: Int,
    val concertsCount: Int,
    val deletedConcertsCount: Int,
    val hadNetwork: Boolean,
    val errors: List<String>,
    val newSalas: List<SalaConcierto> = emptyList(),
    val newArtists: List<Artist> = emptyList(),
    val newConcerts: List<SyncedConcert> = emptyList(),
    val dbRecovered: Boolean = false,
)
