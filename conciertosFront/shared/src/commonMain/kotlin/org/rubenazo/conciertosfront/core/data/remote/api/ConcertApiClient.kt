package org.rubenazo.conciertosfront.core.data.remote.api

import org.rubenazo.conciertosfront.core.data.remote.dto.ApiResponse
import org.rubenazo.conciertosfront.core.data.remote.dto.ArtistDto
import org.rubenazo.conciertosfront.core.data.remote.dto.ConcertApiResponse
import org.rubenazo.conciertosfront.core.data.remote.dto.SalaConciertoDto

interface ConcertApiClient {
    suspend fun checkSalasUpdates(since: String?): Boolean
    suspend fun checkArtistsUpdates(since: String?): Boolean
    suspend fun checkConcertsUpdates(since: String?): Boolean
    suspend fun getSalas(since: String?): ApiResponse<SalaConciertoDto>
    suspend fun getArtists(since: String?): ApiResponse<ArtistDto>
    suspend fun getConcerts(since: String?): ConcertApiResponse
}
