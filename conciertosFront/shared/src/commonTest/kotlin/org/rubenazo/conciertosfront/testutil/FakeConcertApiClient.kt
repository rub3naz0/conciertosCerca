package org.rubenazo.conciertosfront.testutil

import org.rubenazo.conciertosfront.core.data.remote.api.ConcertApiClient
import org.rubenazo.conciertosfront.core.data.remote.dto.ApiResponse
import org.rubenazo.conciertosfront.core.data.remote.dto.ArtistDto
import org.rubenazo.conciertosfront.core.data.remote.dto.ConcertApiResponse
import org.rubenazo.conciertosfront.core.data.remote.dto.SalaConciertoDto

class FakeConcertApiClient : ConcertApiClient {
    var salasHasUpdates = false
    var artistsHasUpdates = false
    var concertsHasUpdates = false

    var salasResponse = ApiResponse("", emptyList<SalaConciertoDto>())
    var artistsResponse = ApiResponse("", emptyList<ArtistDto>())
    var concertsResponse = ConcertApiResponse("", emptyList(), emptyList())

    var shouldThrow: Exception? = null

    override suspend fun checkSalasUpdates(since: String?): Boolean {
        shouldThrow?.let { throw it }
        return salasHasUpdates
    }

    override suspend fun checkArtistsUpdates(since: String?): Boolean {
        shouldThrow?.let { throw it }
        return artistsHasUpdates
    }

    override suspend fun checkConcertsUpdates(since: String?): Boolean {
        shouldThrow?.let { throw it }
        return concertsHasUpdates
    }

    override suspend fun getSalas(since: String?): ApiResponse<SalaConciertoDto> {
        shouldThrow?.let { throw it }
        return salasResponse
    }

    override suspend fun getArtists(since: String?): ApiResponse<ArtistDto> {
        shouldThrow?.let { throw it }
        return artistsResponse
    }

    override suspend fun getConcerts(since: String?): ConcertApiResponse {
        shouldThrow?.let { throw it }
        return concertsResponse
    }
}
