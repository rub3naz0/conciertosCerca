package org.rubenazo.conciertosfront.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SalaConciertoDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("address") val address: String?,
    @SerialName("city") val city: String,
    @SerialName("province") val province: String,
    @SerialName("lat") val lat: Double?,
    @SerialName("lng") val lng: Double?,
    @SerialName("image_url") val imageUrl: String?,
    @SerialName("description") val description: String?,
    @SerialName("source_url") val sourceUrl: String?
)
