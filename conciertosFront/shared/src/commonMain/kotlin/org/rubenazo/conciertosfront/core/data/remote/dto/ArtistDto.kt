package org.rubenazo.conciertosfront.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("genre") val genre: String?,
    @SerialName("image_url") val imageUrl: String?,
    @SerialName("website") val website: String?,
    @SerialName("description") val description: String?,
    @SerialName("source_url") val sourceUrl: String?
)
