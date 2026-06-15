package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConcertTest {

    @Test
    void shouldCreateConcertWithAllFields() {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");
        List<String> artistIds = List.of("a1", "a2");
        Concert concert = new Concert("c1", "sala1", artistIds, LocalDate.of(2026, 6, 15),
                "21:00", "25€", "http://source", now);

        assertThat(concert.id()).isEqualTo("c1");
        assertThat(concert.salaConciertoId()).isEqualTo("sala1");
        assertThat(concert.artistIds()).containsExactly("a1", "a2");
        assertThat(concert.date()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(concert.time()).isEqualTo("21:00");
        assertThat(concert.price()).isEqualTo("25€");
        assertThat(concert.sourceUrl()).isEqualTo("http://source");
        assertThat(concert.updatedAt()).isEqualTo(now);
    }

    @Test
    void shouldNotHaveTicketUrl() {
        // Concert record must NOT have a ticketUrl field — this test verifies
        // the constructor accepts exactly 8 parameters (no ticketUrl slot).
        Concert concert = new Concert("c1", "sala1", List.of("a1"), LocalDate.of(2026, 6, 15),
                "21:00", "25€", "http://source", Instant.now());

        assertThat(concert.id()).isEqualTo("c1");
        assertThat(concert.price()).isEqualTo("25€");
        assertThat(concert.sourceUrl()).isEqualTo("http://source");
    }

    @Test
    void shouldSupportNullOptionalFields() {
        Concert concert = new Concert("c1", "sala1", List.of(), LocalDate.of(2026, 6, 15),
                "21:00", null, null, Instant.now());

        assertThat(concert.price()).isNull();
        assertThat(concert.sourceUrl()).isNull();
    }

    @Test
    void shouldPreserveArtistIdsList() {
        List<String> artistIds = List.of("a1", "a2", "a3");
        Concert concert = new Concert("c1", "sala1", artistIds, LocalDate.of(2026, 6, 15),
                "21:00", "25€", "http://source", Instant.now());

        assertThat(concert.artistIds()).isSameAs(artistIds);
    }
}
