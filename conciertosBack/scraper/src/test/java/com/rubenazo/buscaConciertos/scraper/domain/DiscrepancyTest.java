package com.rubenazo.buscaConciertos.scraper.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DiscrepancyTest {

    @Test
    void discrepancy_storesAllFields() {
        Instant ts = Instant.parse("2026-05-27T12:00:00Z");
        Discrepancy d = new Discrepancy(
            DiscrepancyType.PARSE_ERROR,
            Severity.ERROR,
            "venue",
            "barcelona-sala-apolo",
            "name",
            "Sala Apolo",
            "",
            "<div></div>",
            ts
        );

        assertThat(d.type()).isEqualTo(DiscrepancyType.PARSE_ERROR);
        assertThat(d.severity()).isEqualTo(Severity.ERROR);
        assertThat(d.entityType()).isEqualTo("venue");
        assertThat(d.entityId()).isEqualTo("barcelona-sala-apolo");
        assertThat(d.field()).isEqualTo("name");
        assertThat(d.expected()).isEqualTo("Sala Apolo");
        assertThat(d.actual()).isEqualTo("");
        assertThat(d.rawSnippet()).isEqualTo("<div></div>");
        assertThat(d.timestamp()).isEqualTo(ts);
    }

    @Test
    void discrepancyType_hasAllRequiredValues() {
        assertThat(DiscrepancyType.values()).contains(
            DiscrepancyType.FETCH_ERROR,
            DiscrepancyType.PARSE_ERROR,
            DiscrepancyType.SCHEMA_CHANGE,
            DiscrepancyType.UNKNOWN_VENUE,
            DiscrepancyType.ARTIST_NOT_FOUND,
            DiscrepancyType.ID_COLLISION,
            DiscrepancyType.EMPTY_FIELD,
            DiscrepancyType.UNEXPECTED_CHANGE
        );
    }

    @Test
    void severity_hasAllRequiredValues() {
        assertThat(Severity.values()).contains(
            Severity.INFO,
            Severity.WARNING,
            Severity.ERROR
        );
    }
}
