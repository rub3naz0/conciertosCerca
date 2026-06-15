package org.rubenazo.conciertosfront.core.domain.model

data class Artist(
    val id: String,
    val name: String,
    val genre: String?,
    val imageUrl: String?,
    val website: String?,
    val description: String?,
    val sourceUrl: String?
)
