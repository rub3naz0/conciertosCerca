package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.application.ports.in.GeocodingAdminInputPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeocodingAdminApiTest {

    @Mock
    private GeocodingAdminInputPort port;

    private GeocodingAdminApi api;

    @BeforeEach
    void setUp() {
        api = new GeocodingAdminApi(port);
    }

    // --- POST /api/admin/geocoding/backfill → 200 ---

    @Test
    void backfill_returns200WithBackfillResult() {
        GeocodingAdminInputPort.BackfillResult result =
            new GeocodingAdminInputPort.BackfillResult(6, 2, 4, 0);
        when(port.backfillLocationIqSalas()).thenReturn(result);

        ResponseEntity<GeocodingAdminInputPort.BackfillResult> response = api.backfill();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().scanned()).isEqualTo(6);
        assertThat(response.getBody().overwritten()).isEqualTo(2);
        assertThat(response.getBody().kept()).isEqualTo(4);
        assertThat(response.getBody().notFound()).isEqualTo(0);
    }

    @Test
    void backfill_delegatesToPort() {
        when(port.backfillLocationIqSalas())
            .thenReturn(new GeocodingAdminInputPort.BackfillResult(0, 0, 0, 0));

        api.backfill();

        verify(port).backfillLocationIqSalas();
    }

    @Test
    void backfill_idempotent_sameMockReturnsSameCounts() {
        // Idempotency at the controller level: calling twice with same port state produces same counts
        GeocodingAdminInputPort.BackfillResult result =
            new GeocodingAdminInputPort.BackfillResult(6, 2, 4, 0);
        when(port.backfillLocationIqSalas()).thenReturn(result);

        ResponseEntity<GeocodingAdminInputPort.BackfillResult> first = api.backfill();
        ResponseEntity<GeocodingAdminInputPort.BackfillResult> second = api.backfill();

        assertThat(first.getBody().scanned()).isEqualTo(second.getBody().scanned());
        assertThat(first.getBody().overwritten()).isEqualTo(second.getBody().overwritten());
    }

    @Test
    void backfill_withZeroCounts_returns200WithAllZeros() {
        when(port.backfillLocationIqSalas())
            .thenReturn(new GeocodingAdminInputPort.BackfillResult(0, 0, 0, 0));

        ResponseEntity<GeocodingAdminInputPort.BackfillResult> response = api.backfill();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().scanned()).isEqualTo(0);
        assertThat(response.getBody().overwritten()).isEqualTo(0);
    }

    // --- POST /api/admin/geocoding/fill-missing → 200 ---

    @Test
    void fillMissing_returns200WithDetailedResult() {
        GeocodingAdminInputPort.FillMissingResult result = new GeocodingAdminInputPort.FillMissingResult(
            true, 25, 0.8, 1, 0, 1, 0, 1, 0, 0,
            List.of(new GeocodingAdminInputPort.FillMissingItem(
                "s1", "Sala Apolo", "Barcelona", "Cataluña", "Sala Apolo",
                "Carrer Nou 113, Barcelona", 41.375, 2.169, "foursquare", 0.92,
                "would_write", "would_write", "Dry run: confident Foursquare match would be written")));
        when(port.fillMissingSalaCoords(true, 25)).thenReturn(result);

        ResponseEntity<GeocodingAdminInputPort.FillMissingResult> response = api.fillMissing(true, 25);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().dryRun()).isTrue();
        assertThat(response.getBody().wouldWrite()).isEqualTo(1);
        assertThat(response.getBody().items()).hasSize(1);
    }

    @Test
    void fillMissing_delegatesDryRunAndLimitToPort() {
        when(port.fillMissingSalaCoords(false, 10))
            .thenReturn(new GeocodingAdminInputPort.FillMissingResult(
                false, 10, 0.8, 0, 0, 0, 0, 0, 0, 0, List.of()));

        api.fillMissing(false, 10);

        verify(port).fillMissingSalaCoords(false, 10);
    }

    // --- POST /api/admin/geocoding/fill-from-scraper → 200 ---

    @Test
    void fillFromScraper_returns200WithDetailedResult() {
        GeocodingAdminInputPort.FillFromScraperResult result = new GeocodingAdminInputPort.FillFromScraperResult(
            true, 10, 1, 0, 1, 0, 0, 0,
            List.of(new GeocodingAdminInputPort.FillFromScraperItem(
                "s1", "Sala Apolo", null, null, 41.3735, 2.1700, "wouldWrite")));
        when(port.fillSalaCoordsFromScraper(true, 10)).thenReturn(result);

        ResponseEntity<GeocodingAdminInputPort.FillFromScraperResult> response = api.fillFromScraper(true, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().dryRun()).isTrue();
        assertThat(response.getBody().limit()).isEqualTo(10);
        assertThat(response.getBody().scanned()).isEqualTo(1);
        assertThat(response.getBody().wouldWrite()).isEqualTo(1);
        assertThat(response.getBody().items()).hasSize(1);
    }

    @Test
    void fillFromScraper_delegatesDryRunAndLimitToPort() {
        when(port.fillSalaCoordsFromScraper(false, 10))
            .thenReturn(new GeocodingAdminInputPort.FillFromScraperResult(
                false, 10, 0, 0, 0, 0, 0, 0, List.of()));

        api.fillFromScraper(false, 10);

        verify(port).fillSalaCoordsFromScraper(false, 10);
    }

    @Test
    void fillFromScraper_writeMode_returns200WithWrittenCount() {
        GeocodingAdminInputPort.FillFromScraperResult result = new GeocodingAdminInputPort.FillFromScraperResult(
            false, 10, 1, 1, 0, 0, 0, 0,
            List.of(new GeocodingAdminInputPort.FillFromScraperItem(
                "s1", "Sala Apolo", null, null, 41.3735, 2.1700, "written")));
        when(port.fillSalaCoordsFromScraper(false, 10)).thenReturn(result);

        ResponseEntity<GeocodingAdminInputPort.FillFromScraperResult> response = api.fillFromScraper(false, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().written()).isEqualTo(1);
        assertThat(response.getBody().items().get(0).decision()).isEqualTo("written");
    }
}
