package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.domain.DataQuality;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DataQualityIssueDtoTest {

    private static final Instant FIXED = Instant.parse("2026-05-28T10:00:00Z");

    @Test
    void from_mapsAllFieldsFromDomainRecord() {
        DataQuality dq = new DataQuality(42L, "sala", "s1", "phone", "auto_found",
                "non_severe", "+34 91 123 4567", "https://source.com", 0.91, FIXED);

        DataQualityIssueDto dto = DataQualityIssueDto.from(dq);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.entityType()).isEqualTo("sala");
        assertThat(dto.entityId()).isEqualTo("s1");
        assertThat(dto.field()).isEqualTo("phone");
        assertThat(dto.status()).isEqualTo("auto_found");
        assertThat(dto.suggested()).isEqualTo("+34 91 123 4567");
        assertThat(dto.source()).isEqualTo("https://source.com");
        assertThat(dto.updatedAt()).isEqualTo(FIXED);
        assertThat(dto.score()).isEqualTo(0.91);
    }

    @Test
    void from_handlesNullOptionalFields() {
        DataQuality dq = new DataQuality(1L, "artist", "a1", "genre", "missing",
                "severe", null, null, null, FIXED);

        DataQualityIssueDto dto = DataQualityIssueDto.from(dq);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.suggested()).isNull();
        assertThat(dto.source()).isNull();
        assertThat(dto.score()).isNull();
    }

    @Test
    void from_mapsNullScoreFromDomainRecord() {
        DataQuality dq = new DataQuality(5L, "artist", "a1", "genre", "missing",
                "severe", null, null, null, FIXED);

        DataQualityIssueDto dto = DataQualityIssueDto.from(dq);

        assertThat(dto.score()).isNull();
    }

    @Test
    void from_mapsNonNullScoreFromDomainRecord() {
        DataQuality dq = new DataQuality(6L, "sala", "s1", "description", "auto_found",
                "non_severe", "Nice venue", "https://src.com", 0.85, FIXED);

        DataQualityIssueDto dto = DataQualityIssueDto.from(dq);

        assertThat(dto.score()).isEqualTo(0.85);
    }

    @Test
    void from_recordComponentNamesAreCamelCase() {
        // Verify that the DTO uses camelCase component names (no snake_case like entity_type)
        var components = DataQualityIssueDto.class.getRecordComponents();
        assertThat(components).extracting("name")
                .contains("entityType", "entityId", "updatedAt")
                .doesNotContain("entity_type", "entity_id", "updated_at");
    }
}
