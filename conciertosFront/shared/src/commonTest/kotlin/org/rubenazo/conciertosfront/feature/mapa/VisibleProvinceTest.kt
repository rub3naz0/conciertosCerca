package org.rubenazo.conciertosfront.feature.mapa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto

class VisibleProvinceTest {

    @Test
    fun emptyList_returnsNull() {
        assertNull(firstVisibleProvince(emptyList()))
    }

    @Test
    fun firstConcertWithCoords_returnsItsProvince() {
        val concerts = listOf(
            sampleConcert("c1", province = "Madrid", lat = 40.4168, lng = -3.7038),
            sampleConcert("c2", province = "Barcelona", lat = 41.3851, lng = 2.1734),
        )

        assertEquals("Madrid", firstVisibleProvince(concerts))
    }

    @Test
    fun earlierConcertWithoutCoords_isSkipped() {
        val concerts = listOf(
            sampleConcert("c1", province = "Madrid", lat = null, lng = null),
            sampleConcert("c2", province = "Barcelona", lat = 41.3851, lng = 2.1734),
        )

        assertEquals("Barcelona", firstVisibleProvince(concerts))
    }

    @Test
    fun noConcertHasCoords_returnsNull() {
        val concerts = listOf(
            sampleConcert("c1", province = "Madrid", lat = null, lng = null),
            sampleConcert("c2", province = "Barcelona", lat = null, lng = null),
        )

        assertNull(firstVisibleProvince(concerts))
    }

    @Test
    fun concertWithOnlyLat_isSkipped() {
        val concerts = listOf(
            sampleConcert("c1", province = "Madrid", lat = 40.4168, lng = null),
            sampleConcert("c2", province = "Barcelona", lat = 41.3851, lng = 2.1734),
        )

        assertEquals("Barcelona", firstVisibleProvince(concerts))
    }

    private fun sampleConcert(id: String, province: String, lat: Double?, lng: Double?) = Concert(
        id = id,
        salaConcierto = SalaConcierto("s-$id", "Sala $id", null, "City $id", province, lat, lng, null, null, null),
        artists = listOf(Artist("a1", "Artist", "Pop", null, null, null, null)),
        date = "2026-06-15", time = "21:00", price = "25€",
        sourceUrl = null, updatedAt = "2026-05-24T10:00:00Z"
    )
}
