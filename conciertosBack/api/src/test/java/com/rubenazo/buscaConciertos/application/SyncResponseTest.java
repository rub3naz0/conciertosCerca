package com.rubenazo.buscaConciertos.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyncResponseTest {

    @Test
    void shouldCreateWithTimestampAndData() {
        Instant timestamp = Instant.parse("2026-05-20T15:30:00Z");
        List<String> data = List.of("a", "b");

        SyncResponse<String> response = new SyncResponse<>(timestamp, data);

        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.data()).containsExactly("a", "b");
    }

    @Test
    void shouldSupportEmptyDataList() {
        SyncResponse<String> response = new SyncResponse<>(Instant.now(), List.of());

        assertThat(response.data()).isEmpty();
    }
}
