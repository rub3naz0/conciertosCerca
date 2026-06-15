package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.domain.DataQuality;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DataQualityIssueDtoSeverityTest {

    private static final Instant FIXED = Instant.parse("2026-06-02T10:00:00Z");

    @Test
    void from_mapsSevereToSeverityField() {
        DataQuality dq = new DataQuality(1L, "artist", "a1", "genre", "missing",
                "severe", null, null, null, FIXED);

        DataQualityIssueDto dto = DataQualityIssueDto.from(dq);

        assertThat(dto.severity()).isEqualTo("severe");
    }

    @Test
    void from_mapsNonSevereToSeverityField() {
        DataQuality dq = new DataQuality(2L, "sala", "s1", "description", "auto_found",
                "non_severe", "Nice place", null, 0.85, FIXED);

        DataQualityIssueDto dto = DataQualityIssueDto.from(dq);

        assertThat(dto.severity()).isEqualTo("non_severe");
    }

    @Test
    void from_severityFieldIsPresent() {
        var components = DataQualityIssueDto.class.getRecordComponents();
        assertThat(components).extracting("name").contains("severity");
    }
}
