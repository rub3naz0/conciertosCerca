package org.rubenazo.conciertosfront.core.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("data") val data: List<T>
)

@Serializable
data class ConcertApiResponse(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("data") val data: List<ConcertDto>,
    @SerialName("deleted_ids") val deletedIds: List<String> = emptyList()
)
