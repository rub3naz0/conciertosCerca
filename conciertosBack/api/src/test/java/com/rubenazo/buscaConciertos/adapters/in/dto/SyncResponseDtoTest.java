package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.application.SyncResponse;
import com.rubenazo.buscaConciertos.domain.Artist;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.rubenazo.buscaConciertos.TestFixtures.anArtist;
import static org.assertj.core.api.Assertions.assertThat;

class SyncResponseDtoTest {

    private final Instant timestamp = Instant.parse("2026-05-20T15:30:00Z");

    @Test
    void fromShouldMapTimestampAndTransformData() {
        SyncResponse<Artist> response = new SyncResponse<>(timestamp, List.of(anArtist()));

        SyncResponseDto<ArtistDto> dto = SyncResponseDto.from(response, ArtistDto::from);

        assertThat(dto.timestamp()).isEqualTo("2026-05-20T15:30:00Z");
        assertThat(dto.data()).hasSize(1);
        assertThat(dto.data().get(0).id()).isEqualTo("a1");
    }

    @Test
    void fromShouldHandleEmptyDataList() {
        SyncResponse<Artist> response = new SyncResponse<>(timestamp, List.of());

        SyncResponseDto<ArtistDto> dto = SyncResponseDto.from(response, ArtistDto::from);

        assertThat(dto.data()).isEmpty();
    }

    @Test
    void fromShouldConvertTimestampToString() {
        SyncResponse<Artist> response = new SyncResponse<>(timestamp, List.of());

        SyncResponseDto<ArtistDto> dto = SyncResponseDto.from(response, ArtistDto::from);

        assertThat(dto.timestamp()).isEqualTo(timestamp.toString());
    }
}
