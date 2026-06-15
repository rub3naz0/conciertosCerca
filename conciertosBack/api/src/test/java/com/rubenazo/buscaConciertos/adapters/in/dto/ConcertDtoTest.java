package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.domain.Concert;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConcertDtoTest {

    private final Instant updatedAt = Instant.parse("2026-05-20T10:00:00Z");
    private final LocalDate date = LocalDate.of(2026, 6, 15);

    @Test
    void fromShouldMapAllFieldsFromDomain() {
        Concert concert = new Concert("c1", "sala1", List.of("a1", "a2"), date,
                "21:00", "25€", "http://source", updatedAt);

        ConcertDto dto = ConcertDto.from(concert);

        assertThat(dto.id()).isEqualTo("c1");
        assertThat(dto.salaConciertoId()).isEqualTo("sala1");
        assertThat(dto.artistIds()).containsExactly("a1", "a2");
        assertThat(dto.time()).isEqualTo("21:00");
        assertThat(dto.price()).isEqualTo("25€");
        assertThat(dto.sourceUrl()).isEqualTo("http://source");
    }

    @Test
    void fromShouldNotIncludeTicketUrl() {
        Concert concert = new Concert("c1", "sala1", List.of(), date,
                "21:00", "25€", "http://source", updatedAt);

        ConcertDto dto = ConcertDto.from(concert);

        assertThat(dto.getClass().getRecordComponents())
                .extracting("name")
                .doesNotContain("ticketUrl");
    }

    @Test
    void fromShouldConvertDateToString() {
        Concert concert = new Concert("c1", "sala1", List.of(), date,
                "21:00", "25€", null, updatedAt);

        ConcertDto dto = ConcertDto.from(concert);

        assertThat(dto.date()).isEqualTo("2026-06-15");
    }

    @Test
    void fromShouldConvertUpdatedAtToString() {
        Concert concert = new Concert("c1", "sala1", List.of(), date,
                "21:00", "25€", null, updatedAt);

        ConcertDto dto = ConcertDto.from(concert);

        assertThat(dto.updatedAt()).isEqualTo("2026-05-20T10:00:00Z");
    }

    @Test
    void fromShouldPreserveArtistIdsList() {
        List<String> artistIds = List.of("a1", "a2", "a3");
        Concert concert = new Concert("c1", "sala1", artistIds, date,
                "21:00", "25€", null, updatedAt);

        ConcertDto dto = ConcertDto.from(concert);

        assertThat(dto.artistIds()).containsExactly("a1", "a2", "a3");
    }

    @Test
    void fromShouldHandleNullPrice() {
        Concert concert = new Concert("c1", "sala1", List.of(), date,
                "21:00", null, null, updatedAt);

        ConcertDto dto = ConcertDto.from(concert);

        assertThat(dto.price()).isNull();
    }
}
