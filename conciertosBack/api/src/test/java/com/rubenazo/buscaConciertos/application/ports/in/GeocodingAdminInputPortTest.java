package com.rubenazo.buscaConciertos.application.ports.in;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compile-time contract test: verifies GeocodingAdminInputPort and BackfillResult shape.
 */
class GeocodingAdminInputPortTest {

    @Test
    void geocodingAdminInputPort_hasBackfillLocationIqSalasMethod() {
        // Verify the interface exists with correct methods and return records.
        GeocodingAdminInputPort port = new GeocodingAdminInputPort() {
            @Override
            public BackfillResult backfillLocationIqSalas() {
                return new BackfillResult(0, 0, 0, 0);
            }

            @Override
            public FillMissingResult fillMissingSalaCoords(boolean dryRun, int limit) {
                return new FillMissingResult(dryRun, limit, 0.8, 0, 0, 0, 0, 0, 0, 0, List.of());
            }

            @Override
            public FillFromScraperResult fillSalaCoordsFromScraper(boolean dryRun, int limit) {
                return new FillFromScraperResult(dryRun, limit, 0, 0, 0, 0, 0, 0, List.of());
            }
        };

        GeocodingAdminInputPort.BackfillResult result = port.backfillLocationIqSalas();
        assertThat(result).isNotNull();
        assertThat(port.fillMissingSalaCoords(true, 25).dryRun()).isTrue();
        assertThat(port.fillSalaCoordsFromScraper(true, 25).dryRun()).isTrue();
    }

    @Test
    void backfillResult_hasAllRequiredFields() {
        GeocodingAdminInputPort.BackfillResult result =
            new GeocodingAdminInputPort.BackfillResult(6, 2, 4, 0);
        assertThat(result.scanned()).isEqualTo(6);
        assertThat(result.overwritten()).isEqualTo(2);
        assertThat(result.kept()).isEqualTo(4);
        assertThat(result.notFound()).isEqualTo(0);
    }

    @Test
    void fillMissingResult_hasAllRequiredFields() {
        GeocodingAdminInputPort.FillMissingItem item = new GeocodingAdminInputPort.FillMissingItem(
            "s1", "Sala Apolo", "Barcelona", "Cataluña", "Sala Apolo",
            "Carrer Nou 113, Barcelona", 41.375, 2.169, "foursquare", 0.92,
            "would_write", "would_write", "dry run");
        GeocodingAdminInputPort.FillMissingResult result = new GeocodingAdminInputPort.FillMissingResult(
            true, 25, 0.8, 1, 0, 1, 0, 1, 0, 0, List.of(item));

        assertThat(result.dryRun()).isTrue();
        assertThat(result.limit()).isEqualTo(25);
        assertThat(result.threshold()).isEqualTo(0.8);
        assertThat(result.items()).singleElement().isEqualTo(item);
    }

    @Test
    void fillFromScraperResult_hasAllRequiredFields() {
        GeocodingAdminInputPort.FillFromScraperItem item = new GeocodingAdminInputPort.FillFromScraperItem(
            "s1", "Sala Apolo", null, null, 41.3735, 2.1700, "written");
        GeocodingAdminInputPort.FillFromScraperResult result = new GeocodingAdminInputPort.FillFromScraperResult(
            false, 25, 1, 1, 0, 0, 0, 0, List.of(item));

        assertThat(result.dryRun()).isFalse();
        assertThat(result.limit()).isEqualTo(25);
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.written()).isEqualTo(1);
        assertThat(result.items()).singleElement().isEqualTo(item);
        assertThat(item.decision()).isEqualTo("written");
    }
}
