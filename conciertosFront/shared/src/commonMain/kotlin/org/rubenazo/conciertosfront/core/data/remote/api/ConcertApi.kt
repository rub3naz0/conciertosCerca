package org.rubenazo.conciertosfront.core.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import org.rubenazo.conciertosfront.core.data.remote.dto.ApiResponse
import org.rubenazo.conciertosfront.core.data.remote.dto.ArtistDto
import org.rubenazo.conciertosfront.core.data.remote.dto.ConcertApiResponse
import org.rubenazo.conciertosfront.core.data.remote.dto.SalaConciertoDto

class ConcertApi(private val baseUrl: String, private val httpClient: HttpClient) : ConcertApiClient {

    override suspend fun checkSalasUpdates(since: String?): Boolean {
        val response = httpClient.head("$baseUrl/api/v1/salas-concierto") {
            since?.let { parameter("since", it) }
        }
        return response.status == HttpStatusCode.OK
    }

    override suspend fun checkArtistsUpdates(since: String?): Boolean {
        val response = httpClient.head("$baseUrl/api/v1/artists") {
            since?.let { parameter("since", it) }
        }
        return response.status == HttpStatusCode.OK
    }

    override suspend fun checkConcertsUpdates(since: String?): Boolean {
        val response = httpClient.head("$baseUrl/api/v1/concerts") {
            since?.let { parameter("since", it) }
        }
        return response.status == HttpStatusCode.OK
    }

    override suspend fun getSalas(since: String?): ApiResponse<SalaConciertoDto> {
        return httpClient.get("$baseUrl/api/v1/salas-concierto") {
            since?.let { parameter("since", it) }
        }.body()
    }

    override suspend fun getArtists(since: String?): ApiResponse<ArtistDto> {
        return httpClient.get("$baseUrl/api/v1/artists") {
            since?.let { parameter("since", it) }
        }.body()
    }

    override suspend fun getConcerts(since: String?): ConcertApiResponse {
        return httpClient.get("$baseUrl/api/v1/concerts") {
            since?.let { parameter("since", it) }
        }.body()
    }
}
