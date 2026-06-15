package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.application.ConcertSyncResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.rubenazo.buscaConciertos.TestFixtures.aConcert;
import static org.assertj.core.api.Assertions.assertThat;

class ConcertSyncResponseDtoTest {

    private final Instant timestamp = Instant.parse("2026-05-20T15:30:00Z");

    @Test
    void fromShouldMapAllFields() {
        ConcertSyncResponse response = new ConcertSyncResponse(timestamp, List.of(aConcert()), List.of("c99"));

        ConcertSyncResponseDto dto = ConcertSyncResponseDto.from(response);

        assertThat(dto.timestamp()).isEqualTo("2026-05-20T15:30:00Z");
        assertThat(dto.data()).hasSize(1);
        assertThat(dto.data().get(0).id()).isEqualTo("c1");
        assertThat(dto.deletedIds()).containsExactly("c99");
    }

    @Test
    void fromShouldHandleEmptyDataAndDeletedIds() {
        ConcertSyncResponse response = new ConcertSyncResponse(timestamp, List.of(), List.of());

        ConcertSyncResponseDto dto = ConcertSyncResponseDto.from(response);

        assertThat(dto.data()).isEmpty();
        assertThat(dto.deletedIds()).isEmpty();
    }

    @Test
    void fromShouldPreserveDeletedIds() {
        List<String> deletedIds = List.of("c10", "c20", "c30");
        ConcertSyncResponse response = new ConcertSyncResponse(timestamp, List.of(), deletedIds);

        ConcertSyncResponseDto dto = ConcertSyncResponseDto.from(response);

        assertThat(dto.deletedIds()).containsExactly("c10", "c20", "c30");
    }
}
