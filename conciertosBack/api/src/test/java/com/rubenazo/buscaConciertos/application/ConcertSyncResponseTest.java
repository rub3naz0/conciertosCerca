package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.domain.Concert;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.rubenazo.buscaConciertos.TestFixtures.aConcert;
import static org.assertj.core.api.Assertions.assertThat;

class ConcertSyncResponseTest {

    @Test
    void shouldCreateWithTimestampDataAndDeletedIds() {
        Instant timestamp = Instant.parse("2026-05-20T15:30:00Z");
        List<Concert> data = List.of(aConcert());
        List<String> deletedIds = List.of("c99");

        ConcertSyncResponse response = new ConcertSyncResponse(timestamp, data, deletedIds);

        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.data()).hasSize(1);
        assertThat(response.deletedIds()).containsExactly("c99");
    }

    @Test
    void shouldSupportEmptyDeletedIds() {
        ConcertSyncResponse response = new ConcertSyncResponse(Instant.now(), List.of(aConcert()), List.of());

        assertThat(response.deletedIds()).isEmpty();
    }

    @Test
    void shouldSupportEmptyDataAndDeletedIds() {
        ConcertSyncResponse response = new ConcertSyncResponse(Instant.now(), List.of(), List.of());

        assertThat(response.data()).isEmpty();
        assertThat(response.deletedIds()).isEmpty();
    }
}
