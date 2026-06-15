package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistTest {

    @Test
    void shouldCreateArtistWithAllFields() {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");
        Artist artist = new Artist("a1", "Vetusta Morla", "Indie Rock", "http://img.jpg", "http://web.com", null, null, now);

        assertThat(artist.id()).isEqualTo("a1");
        assertThat(artist.name()).isEqualTo("Vetusta Morla");
        assertThat(artist.genre()).isEqualTo("Indie Rock");
        assertThat(artist.imageUrl()).isEqualTo("http://img.jpg");
        assertThat(artist.website()).isEqualTo("http://web.com");
        assertThat(artist.updatedAt()).isEqualTo(now);
    }

    @Test
    void shouldSupportNullOptionalFields() {
        Artist artist = new Artist("a1", "Vetusta Morla", "Indie Rock", null, null, null, null, Instant.now());

        assertThat(artist.imageUrl()).isNull();
        assertThat(artist.website()).isNull();
    }

    @Test
    void shouldBeEqualForIdenticalValues() {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");
        Artist a = new Artist("a1", "Vetusta Morla", "Indie Rock", "http://img.jpg", "http://web.com", null, null, now);
        Artist b = new Artist("a1", "Vetusta Morla", "Indie Rock", "http://img.jpg", "http://web.com", null, null, now);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenIdDiffers() {
        Instant now = Instant.parse("2026-05-20T10:00:00Z");
        Artist a = new Artist("a1", "Vetusta Morla", "Indie Rock", "http://img.jpg", "http://web.com", null, null, now);
        Artist b = new Artist("a2", "Vetusta Morla", "Indie Rock", "http://img.jpg", "http://web.com", null, null, now);

        assertThat(a).isNotEqualTo(b);
    }
}
