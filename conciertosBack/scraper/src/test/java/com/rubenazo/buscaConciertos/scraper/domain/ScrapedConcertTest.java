package com.rubenazo.buscaConciertos.scraper.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapedConcertTest {

    @Test
    void shouldCreateScrapedConcertWithoutTicketUrl() {
        // ScrapedConcert must NOT have a ticketUrl field — constructor accepts
        // exactly 13 parameters (no ticketUrl slot).
        ScrapedConcert concert = new ScrapedConcert(
            "c1", "sala1", List.of("artist-1"),
            LocalDate.of(2026, 6, 15), "21:00", "25€",
            "http://source", "Sala Apolo", "Barcelona",
            "Artist Name", "Rock", "http://img.jpg", "/barcelona/locales/sala-apolo"
        );

        assertThat(concert.id()).isEqualTo("c1");
        assertThat(concert.price()).isEqualTo("25€");
        assertThat(concert.sourceUrl()).isEqualTo("http://source");
        assertThat(concert.venueName()).isEqualTo("Sala Apolo");
    }

    @Test
    void shouldSupportNullOptionalFields() {
        ScrapedConcert concert = new ScrapedConcert(
            "c1", null, List.of(),
            LocalDate.of(2026, 6, 15), null, null,
            null, "Venue", "Province",
            "Artist", null, null, "/prov/locales/venue"
        );

        assertThat(concert.price()).isNull();
        assertThat(concert.sourceUrl()).isNull();
        assertThat(concert.imageUrl()).isNull();
    }
}
