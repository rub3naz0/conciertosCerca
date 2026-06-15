package com.rubenazo.buscaConciertos.application.ports.in;

import java.util.List;

public interface GeocodingAdminInputPort {

    BackfillResult backfillLocationIqSalas();

    FillMissingResult fillMissingSalaCoords(boolean dryRun, int limit);

    FillFromScraperResult fillSalaCoordsFromScraper(boolean dryRun, int limit);

    record BackfillResult(int scanned, int overwritten, int kept, int notFound) {}

    record FillMissingResult(
        boolean dryRun,
        int limit,
        double threshold,
        int scanned,
        int written,
        int wouldWrite,
        int addressesWritten,
        int addressesWouldWrite,
        int needsReview,
        int noMatch,
        List<FillMissingItem> items
    ) {}

    record FillMissingItem(
        String salaId,
        String salaName,
        String city,
        String province,
        String candidateName,
        String candidateAddress,
        Double lat,
        Double lng,
        String provider,
        Double confidence,
        String decision,
        String addressDecision,
        String reason
    ) {}

    record FillFromScraperResult(
        boolean dryRun,
        int limit,
        int scanned,
        int written,
        int wouldWrite,
        int keptManual,
        int keptNoChange,
        int noCoords,
        List<FillFromScraperItem> items
    ) {}

    record FillFromScraperItem(
        String salaId,
        String salaName,
        Double previousLat,
        Double previousLng,
        Double scrapedLat,
        Double scrapedLng,
        String decision
    ) {}
}
