package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.application.ports.in.GeocodingAdminInputPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/geocoding")
@Tag(name = "Geocoding Admin")
public class GeocodingAdminApi {

    private final GeocodingAdminInputPort port;

    public GeocodingAdminApi(GeocodingAdminInputPort port) {
        this.port = port;
    }

    @PostMapping("/backfill")
    @Operation(summary = "Backfill LocationIQ sala coords with Foursquare name-based results",
        description = "Runs each sala that was geocoded with LocationIQ through the Foursquare fallback chain. " +
            "Overwrites coordinates only when a confident Foursquare match is found (confidence >= threshold). " +
            "Idempotent: calling multiple times produces no additional writes if data has not changed.")
    @ApiResponse(responseCode = "200", description = "Backfill completed; returns processed/updated counts")
    public ResponseEntity<GeocodingAdminInputPort.BackfillResult> backfill() {
        return ResponseEntity.ok(port.backfillLocationIqSalas());
    }

    @PostMapping("/fill-missing")
    @Operation(summary = "Fill missing sala coords with safe Foursquare name-based results",
        description = "Scans salas missing lat/lng and evaluates Foursquare name-based candidates. " +
            "Dry-run is enabled by default. In write mode, coordinates are written only for confident " +
            "Foursquare matches (confidence >= threshold); address fields are never written.")
    @ApiResponse(responseCode = "200", description = "Fill-missing completed; returns counts and per-sala decisions")
    public ResponseEntity<GeocodingAdminInputPort.FillMissingResult> fillMissing(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(port.fillMissingSalaCoords(dryRun, limit));
    }

    @PostMapping("/fill-from-scraper")
    @Operation(summary = "Re-scrape conciertos.club venue pages and fill/reconcile sala coords",
        description = "Re-fetches each eligible conciertos.club sala's page and extracts the Google Maps " +
            "iframe pin. Dry-run is enabled by default. In write mode, the scraped pin overwrites NULL or " +
            "geocoding-provenance coordinates (page pin wins on agreement or disagreement); manually " +
            "corrected salas are left untouched (kept-manual). On every write, the corresponding " +
            "data_quality lat/lng rows are resolved to auto_approved with provider conciertos-club-page.")
    @ApiResponse(responseCode = "200", description = "Fill-from-scraper completed; returns counts and per-sala decisions")
    public ResponseEntity<GeocodingAdminInputPort.FillFromScraperResult> fillFromScraper(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(port.fillSalaCoordsFromScraper(dryRun, limit));
    }
}
