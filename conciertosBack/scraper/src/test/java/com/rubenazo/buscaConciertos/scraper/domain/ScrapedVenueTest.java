package com.rubenazo.buscaConciertos.scraper.domain;

import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapedVenueTest {

    @Test
    void toDomain_mapsAllFields() {
        // ScrapedVenue must NOT have phone/website; must have description.
        // Constructor: id, name, address, city, province, lat, lng, imageUrl, description, sourceUrl
        ScrapedVenue venue = new ScrapedVenue(
            "barcelona-sala-apolo", "Sala Apolo", "C/ Nou de la Rambla 113",
            "Barcelona", "Barcelona", 41.3735, 2.1700,
            "http://img.jpg", "Sala de conciertos en Barcelona", "https://conciertos.club/barcelona/locales/sala-apolo"
        );
        Instant now = Instant.parse("2026-05-27T12:00:00Z");

        SalaConcierto domain = venue.toDomain(now);

        assertThat(domain.id()).isEqualTo("barcelona-sala-apolo");
        assertThat(domain.name()).isEqualTo("Sala Apolo");
        assertThat(domain.city()).isEqualTo("Barcelona");
        assertThat(domain.province()).isEqualTo("Barcelona");
        assertThat(domain.description()).isEqualTo("Sala de conciertos en Barcelona");
        assertThat(domain.updatedAt()).isEqualTo(now);
    }

    @Test
    void toDomain_propagatesDescription() {
        ScrapedVenue venue = new ScrapedVenue(
            "madrid-remo", "Sala Remo", null, "Madrid", "Madrid",
            null, null, null, "Descripción de la sala", "https://conciertos.club/madrid/locales/remo"
        );
        SalaConcierto domain = venue.toDomain(Instant.now());

        assertThat(domain.description()).isEqualTo("Descripción de la sala");
    }

    @Test
    void toDomain_withNullOptionals_mapsWithNulls() {
        ScrapedVenue venue = new ScrapedVenue(
            "madrid-venue", "Venue", null, "Madrid", "Madrid",
            null, null, null, null, "https://conciertos.club/madrid/locales/venue"
        );
        SalaConcierto domain = venue.toDomain(Instant.now());

        assertThat(domain.address()).isNull();
        assertThat(domain.lat()).isNull();
        assertThat(domain.lng()).isNull();
        assertThat(domain.description()).isNull();
    }

    @Test
    void shouldNotHavePhoneOrWebsite() {
        // ScrapedVenue constructor accepts exactly 10 parameters (no phone/website).
        ScrapedVenue venue = new ScrapedVenue(
            "id", "Name", "Address", "City", "Province",
            1.0, 2.0, "http://img.jpg", "A description", "http://source"
        );

        assertThat(venue.imageUrl()).isEqualTo("http://img.jpg");
        assertThat(venue.description()).isEqualTo("A description");
        assertThat(venue.sourceUrl()).isEqualTo("http://source");
    }
}
