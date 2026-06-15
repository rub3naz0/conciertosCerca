package org.rubenazo.conciertosfront.core.domain.model

data class Concert(
    val id: String,
    val salaConcierto: SalaConcierto,
    val artists: List<Artist>,
    val date: String,
    val time: String?,
    val price: String?,
    val sourceUrl: String?,
    val updatedAt: String
)
