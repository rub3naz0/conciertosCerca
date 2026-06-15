package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.domain.Artist;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistDtoTest {

    @Test
    void fromShouldMapAllFieldsFromDomain() {
        Artist artist = new Artist("a1", "Vetusta Morla", "Indie Rock",
                "http://img.jpg", "http://web.com", null, null, Instant.parse("2026-05-20T10:00:00Z"));

        ArtistDto dto = ArtistDto.from(artist);

        assertThat(dto.id()).isEqualTo("a1");
        assertThat(dto.name()).isEqualTo("Vetusta Morla");
        assertThat(dto.genre()).isEqualTo("Indie Rock");
        assertThat(dto.imageUrl()).isEqualTo("http://img.jpg");
        assertThat(dto.website()).isEqualTo("http://web.com");
    }

    @Test
    void fromShouldHandleNullImageUrl() {
        Artist artist = new Artist("a1", "Vetusta Morla", "Indie Rock",
                null, "http://web.com", null, null, Instant.now());

        ArtistDto dto = ArtistDto.from(artist);

        assertThat(dto.imageUrl()).isNull();
    }

    @Test
    void fromShouldNotIncludeUpdatedAt() {
        Artist artist = new Artist("a1", "Vetusta Morla", "Indie Rock",
                "http://img.jpg", "http://web.com", null, null, Instant.parse("2026-05-20T10:00:00Z"));

        ArtistDto dto = ArtistDto.from(artist);

        assertThat(dto.getClass().getRecordComponents())
                .extracting("name")
                .doesNotContain("updatedAt");
    }
}
