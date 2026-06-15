package org.rubenazo.conciertosfront.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConcertDto(
    @SerialName("id") val id: String,
    @SerialName("salaConcierto_id") val salaConciertoId: String,
    @SerialName("artist_ids") val artistIds: List<String>,
    @SerialName("date") val date: String,
    @SerialName("time") val time: String?,
    @SerialName("price") val price: String?,
    @SerialName("source_url") val sourceUrl: String?,
    @SerialName("updated_at") val updatedAt: String
)
