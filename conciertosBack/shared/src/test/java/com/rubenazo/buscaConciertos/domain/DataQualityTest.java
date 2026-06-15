package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DataQualityTest {

    private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

    @Test
    void shouldCreateDataQualityWithAllFields() {
        DataQuality dq = new DataQuality(
            1L, "artist", "a1", "genre", "missing",
            "non_severe", null, null, null, NOW
        );

        assertThat(dq.id()).isEqualTo(1L);
        assertThat(dq.entityType()).isEqualTo("artist");
        assertThat(dq.entityId()).isEqualTo("a1");
        assertThat(dq.field()).isEqualTo("genre");
        assertThat(dq.status()).isEqualTo("missing");
        assertThat(dq.severity()).isEqualTo("non_severe");
        assertThat(dq.suggested()).isNull();
        assertThat(dq.source()).isNull();
        assertThat(dq.score()).isNull();
        assertThat(dq.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldSupportNullIdForNewRecords() {
        DataQuality dq = new DataQuality(
            null, "sala", "s1", "phone", "missing",
            "non_severe", null, null, null, NOW
        );

        assertThat(dq.id()).isNull();
        assertThat(dq.entityType()).isEqualTo("sala");
    }

    @Test
    void shouldSupportSuggestedAndSource() {
        DataQuality dq = new DataQuality(
            2L, "artist", "a1", "website", "auto_found",
            "non_severe", "https://artist.com", "https://tavily.com/result", 0.88, NOW
        );

        assertThat(dq.suggested()).isEqualTo("https://artist.com");
        assertThat(dq.source()).isEqualTo("https://tavily.com/result");
        assertThat(dq.status()).isEqualTo("auto_found");
        assertThat(dq.score()).isEqualTo(0.88);
    }

    @Test
    void shouldBeEqualForIdenticalValues() {
        DataQuality a = new DataQuality(1L, "artist", "a1", "genre", "missing", "non_severe", null, null, null, NOW);
        DataQuality b = new DataQuality(1L, "artist", "a1", "genre", "missing", "non_severe", null, null, null, NOW);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenFieldDiffers() {
        DataQuality a = new DataQuality(1L, "artist", "a1", "genre", "missing", "non_severe", null, null, null, NOW);
        DataQuality b = new DataQuality(1L, "artist", "a1", "website", "missing", "non_severe", null, null, null, NOW);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldCarrySeverityField() {
        DataQuality severe = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, NOW);
        DataQuality nonSevere = new DataQuality(2L, "sala", "s1", "image_url", "missing", "non_severe", null, null, null, NOW);

        assertThat(severe.severity()).isEqualTo("severe");
        assertThat(nonSevere.severity()).isEqualTo("non_severe");
    }

    @Test
    void shouldHaveNullSeverityWhenNotSet() {
        DataQuality dq = new DataQuality(1L, "artist", "a1", "genre", "missing", null, null, null, null, NOW);
        assertThat(dq.severity()).isNull();
    }

    @Test
    void shouldCarryNonNullScore() {
        DataQuality dq = new DataQuality(1L, "artist", "a1", "genre", "auto_found", "non_severe", "Rock", "https://src.com", 0.92, NOW);
        assertThat(dq.score()).isEqualTo(0.92);
    }

    @Test
    void shouldCarryNullScore() {
        DataQuality dq = new DataQuality(1L, "artist", "a1", "genre", "missing", "severe", null, null, null, NOW);
        assertThat(dq.score()).isNull();
    }
}
