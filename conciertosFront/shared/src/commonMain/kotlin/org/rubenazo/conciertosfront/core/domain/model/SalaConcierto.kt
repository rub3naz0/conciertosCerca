package org.rubenazo.conciertosfront.core.domain.model

data class SalaConcierto(
    val id: String,
    val name: String,
    val address: String?,
    val city: String,
    val province: String,
    val lat: Double?,
    val lng: Double?,
    val imageUrl: String?,
    val description: String?,
    val sourceUrl: String?
)
