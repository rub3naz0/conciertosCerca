package org.rubenazo.conciertosfront.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertWithDetails
import org.rubenazo.conciertosfront.core.data.local.entity.ArtistEntity
import org.rubenazo.conciertosfront.core.data.local.entity.SalaConciertoEntity
import org.rubenazo.conciertosfront.core.data.remote.dto.ArtistDto
import org.rubenazo.conciertosfront.core.data.remote.dto.ConcertDto
import org.rubenazo.conciertosfront.core.data.remote.dto.SalaConciertoDto

class MappersTest {

    @Test
    fun salaConciertoDto_toEntity_mapsAllFields() {
        val dto = SalaConciertoDto(
            id = "s1", name = "Sala Apolo", address = "C/Nou 113",
            city = "Barcelona", province = "Barcelona",
            lat = 41.3, lng = 2.1,
            imageUrl = "https://img.com/apolo.jpg",
            description = "Historic concert venue in Barcelona",
            sourceUrl = "https://source.com/apolo"
        )
        val entity = dto.toEntity()
        assertEquals("s1", entity.id)
        assertEquals("Sala Apolo", entity.name)
        assertEquals("C/Nou 113", entity.address)
        assertEquals("Barcelona", entity.city)
        assertEquals("Barcelona", entity.province)
        assertEquals(41.3, entity.lat)
        assertEquals(2.1, entity.lng)
        assertEquals("https://img.com/apolo.jpg", entity.imageUrl)
        assertEquals("Historic concert venue in Barcelona", entity.description)
        assertEquals("https://source.com/apolo", entity.sourceUrl)
    }

    @Test
    fun salaConciertoDto_toEntity_mapsNullFields() {
        val dto = SalaConciertoDto(
            id = "s1", name = "Sala", address = null,
            city = "Madrid", province = "Madrid",
            lat = null, lng = null, imageUrl = null, description = null, sourceUrl = null
        )
        val entity = dto.toEntity()
        assertNull(entity.address)
        assertNull(entity.lat)
        assertNull(entity.lng)
        assertNull(entity.imageUrl)
        assertNull(entity.description)
        assertNull(entity.sourceUrl)
    }

    @Test
    fun artistDto_toEntity_mapsAllFields() {
        val dto = ArtistDto(
            id = "a1", name = "Rosalía",
            genre = "Flamenco Pop", imageUrl = "https://img.com/rosalia.jpg",
            website = "https://rosalia.com",
            description = "Spanish flamenco-pop artist",
            sourceUrl = "https://source.com/rosalia"
        )
        val entity = dto.toEntity()
        assertEquals("a1", entity.id)
        assertEquals("Rosalía", entity.name)
        assertEquals("Flamenco Pop", entity.genre)
        assertEquals("https://img.com/rosalia.jpg", entity.imageUrl)
        assertEquals("https://rosalia.com", entity.website)
        assertEquals("Spanish flamenco-pop artist", entity.description)
        assertEquals("https://source.com/rosalia", entity.sourceUrl)
    }

    @Test
    fun artistDto_toEntity_mapsNullFields() {
        val dto = ArtistDto(id = "a1", name = "Unknown", genre = null, imageUrl = null, website = null, description = null, sourceUrl = null)
        val entity = dto.toEntity()
        assertNull(entity.genre)
        assertNull(entity.imageUrl)
        assertNull(entity.website)
        assertNull(entity.description)
        assertNull(entity.sourceUrl)
    }

    @Test
    fun concertDto_toEntity_mapsAllFields() {
        val dto = ConcertDto(
            id = "c1", salaConciertoId = "s1",
            artistIds = listOf("a1", "a2"),
            date = "2026-06-15", time = "21:00",
            price = "25€",
            sourceUrl = "https://source.com/1",
            updatedAt = "2026-05-24T10:00:00Z"
        )
        val entity = dto.toEntity()
        assertEquals("c1", entity.id)
        assertEquals("s1", entity.salaConciertoId)
        assertEquals("2026-06-15", entity.date)
        assertEquals("21:00", entity.time)
        assertEquals("25€", entity.price)
        assertEquals("https://source.com/1", entity.sourceUrl)
        assertEquals("2026-05-24T10:00:00Z", entity.updatedAt)
    }

    @Test
    fun concertDto_toEntity_mapsNullFields() {
        val dto = ConcertDto(
            id = "c1", salaConciertoId = "s1", artistIds = emptyList(),
            date = "2026-06-15", time = null, price = null,
            sourceUrl = null, updatedAt = "2026-05-24T10:00:00Z"
        )
        val entity = dto.toEntity()
        assertNull(entity.time)
        assertNull(entity.price)
        assertNull(entity.sourceUrl)
    }

    @Test
    fun concertDto_toEntity_hasNoTicketUrl() {
        // ConcertEntity compiles without ticketUrl — shape is enforced at compile time.
        // A successful toEntity() call here proves the field is absent from ConcertDto and ConcertEntity.
        val dto = ConcertDto(
            id = "c1", salaConciertoId = "s1", artistIds = emptyList(),
            date = "2026-06-15", time = "21:00", price = "25€",
            sourceUrl = "https://source.com", updatedAt = "2026-05-24T10:00:00Z"
        )
        val entity = dto.toEntity()
        assertEquals("c1", entity.id)
        assertEquals("https://source.com", entity.sourceUrl)
    }

    @Test
    fun salaConciertoEntity_toDomain_mapsAllFields() {
        val entity = SalaConciertoEntity(
            id = "s1", name = "Sala Apolo", address = "C/Nou 113",
            city = "Barcelona", province = "Barcelona",
            lat = 41.3, lng = 2.1,
            imageUrl = "https://img.com/apolo.jpg",
            description = "A historic concert hall",
            sourceUrl = "https://source.com/apolo"
        )
        val domain = entity.toDomain()
        assertEquals("s1", domain.id)
        assertEquals("Sala Apolo", domain.name)
        assertEquals("C/Nou 113", domain.address)
        assertEquals("Barcelona", domain.city)
        assertEquals("Barcelona", domain.province)
        assertEquals(41.3, domain.lat)
        assertEquals(2.1, domain.lng)
        assertEquals("https://img.com/apolo.jpg", domain.imageUrl)
        assertEquals("A historic concert hall", domain.description)
        assertEquals("https://source.com/apolo", domain.sourceUrl)
    }

    @Test
    fun salaConciertoEntity_toDomain_hasNoPhoneOrWebsite() {
        val entity = SalaConciertoEntity(
            id = "s1", name = "Sala Apolo", address = null,
            city = "Barcelona", province = "Barcelona",
            lat = null, lng = null, imageUrl = null, description = null, sourceUrl = null
        )
        val domain = entity.toDomain()
        assertEquals("s1", domain.id)
        assertNull(domain.description)
        assertNull(domain.sourceUrl)
    }

    @Test
    fun artistEntity_toDomain_mapsAllFields() {
        val entity = ArtistEntity(
            id = "a1", name = "Rosalía", genre = "Flamenco Pop",
            imageUrl = "https://img.com/rosalia.jpg", website = "https://rosalia.com",
            description = "Spanish flamenco-pop artist", sourceUrl = "https://source.com/rosalia"
        )
        val domain = entity.toDomain()
        assertEquals("a1", domain.id)
        assertEquals("Rosalía", domain.name)
        assertEquals("Flamenco Pop", domain.genre)
        assertEquals("https://img.com/rosalia.jpg", domain.imageUrl)
        assertEquals("https://rosalia.com", domain.website)
        assertEquals("Spanish flamenco-pop artist", domain.description)
        assertEquals("https://source.com/rosalia", domain.sourceUrl)
    }

    @Test
    fun toConcertList_emptyList() {
        val result = emptyList<ConcertWithDetails>().toConcertList()
        assertEquals(0, result.size)
    }

    @Test
    fun toConcertList_singleConcertSingleArtist() {
        val rows = listOf(
            concertWithDetailsRow("c1", artistId = "a1", artistName = "Rosalía", artistPosition = 0)
        )
        val result = rows.toConcertList()
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
        assertEquals("Sala Apolo", result[0].salaConcierto.name)
        assertEquals(1, result[0].artists.size)
        assertEquals("Rosalía", result[0].artists[0].name)
    }

    @Test
    fun toConcertList_singleConcertMultipleArtistsSortedByPosition() {
        val rows = listOf(
            concertWithDetailsRow("c1", artistId = "a2", artistName = "Bad Gyal", artistPosition = 1),
            concertWithDetailsRow("c1", artistId = "a1", artistName = "Rosalía", artistPosition = 0),
        )
        val result = rows.toConcertList()
        assertEquals(1, result.size)
        assertEquals(2, result[0].artists.size)
        assertEquals("Rosalía", result[0].artists[0].name)
        assertEquals("Bad Gyal", result[0].artists[1].name)
    }

    @Test
    fun toConcertList_multipleConcerts() {
        val rows = listOf(
            concertWithDetailsRow("c1", artistId = "a1", artistName = "Rosalía", artistPosition = 0),
            concertWithDetailsRow("c2", artistId = "a2", artistName = "Bad Gyal", artistPosition = 0),
        )
        val result = rows.toConcertList()
        assertEquals(2, result.size)
        assertEquals("c1", result[0].id)
        assertEquals("c2", result[1].id)
    }

    @Test
    fun toConcertList_mapsNullableFields() {
        val rows = listOf(
            ConcertWithDetails(
                concertId = "c1", date = "2026-06-15", time = null,
                price = null, sourceUrl = null, updatedAt = "2026-05-24T10:00:00Z",
                salaId = "s1", salaName = "Sala", salaAddress = null,
                salaCity = "BCN", salaProvince = "BCN",
                salaLat = null, salaLng = null,
                salaImageUrl = null, salaDescription = null, salaSourceUrl = null,
                artistId = "a1", artistName = "Artist", artistGenre = null,
                artistImageUrl = null, artistWebsite = null,
                artistDescription = null, artistSourceUrl = null, artistPosition = 0
            )
        )
        val result = rows.toConcertList()
        assertEquals(1, result.size)
        assertNull(result[0].time)
        assertNull(result[0].price)
        assertNull(result[0].salaConcierto.address)
        assertNull(result[0].artists[0].genre)
    }

    @Test
    fun toConcertList_concertWithNoArtists() {
        val rows = listOf(
            ConcertWithDetails(
                concertId = "c1", date = "2026-06-15", time = "21:00",
                price = "25€", sourceUrl = null, updatedAt = "2026-05-24T10:00:00Z",
                salaId = "s1", salaName = "Sala Apolo", salaAddress = null,
                salaCity = "Barcelona", salaProvince = "Barcelona",
                salaLat = null, salaLng = null,
                salaImageUrl = null, salaDescription = null, salaSourceUrl = null,
                artistId = null, artistName = null, artistGenre = null,
                artistImageUrl = null, artistWebsite = null,
                artistDescription = null, artistSourceUrl = null, artistPosition = null
            )
        )
        val result = rows.toConcertList()
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
        assertEquals(0, result[0].artists.size)
    }

    private fun concertWithDetailsRow(
        concertId: String,
        artistId: String = "a1",
        artistName: String = "Artist",
        artistPosition: Int = 0,
    ) = ConcertWithDetails(
        concertId = concertId, date = "2026-06-15", time = "21:00",
        price = "25€", sourceUrl = null, updatedAt = "2026-05-24T10:00:00Z",
        salaId = "s1", salaName = "Sala Apolo", salaAddress = null,
        salaCity = "Barcelona", salaProvince = "Barcelona",
        salaLat = null, salaLng = null,
        salaImageUrl = null, salaDescription = null, salaSourceUrl = null,
        artistId = artistId, artistName = artistName, artistGenre = null,
        artistImageUrl = null, artistWebsite = null,
        artistDescription = null, artistSourceUrl = null, artistPosition = artistPosition
    )
}
