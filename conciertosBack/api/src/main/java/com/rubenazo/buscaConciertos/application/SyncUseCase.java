package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.SyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.FieldSeverity;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.scraper.application.SlugUtils;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.ExistingDataReaderPort;
import com.rubenazo.buscaConciertos.scraper.application.usecases.ArtistEnrichmentUseCase;
import com.rubenazo.buscaConciertos.scraper.application.usecases.ConcertScraperUseCase;
import com.rubenazo.buscaConciertos.scraper.application.usecases.VenueScraperUseCase;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedArtist;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedConcert;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue;
import com.rubenazo.buscaConciertos.scraper.domain.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates a full scrape-and-ingest run for the primary source — the heart of the backend.
 *
 * Runs asynchronously ({@code @Async}) under a {@code runId} whose lifecycle is tracked via
 * {@link com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort}. The pipeline (see the
 * numbered steps in {@code doExecute}): read existing ids → scrape concerts in the date window →
 * keep only new ones → scrape their venues → resolve each concert to a venue (creating a "partial"
 * sala when details are missing) → enrich artists via the scraper, stubbing the rest to satisfy
 * FKs → geocode new venues → then a single transaction does the FK-safe upsert
 * (salas → partial salas → artists → concerts), purges past concerts, bumps sync metadata, and
 * records data-quality issues. A post-commit {@link DataQualityCheckEvent} kicks off async
 * RAG enrichment in {@link DataQualityUseCase}.
 *
 * Failures are reported by marking the run failed rather than thrown, since {@code @Async} would
 * otherwise swallow them and leave the run stuck 'running'.
 */
@Service
public class SyncUseCase implements SyncInputPort {

    private static final Logger log = LoggerFactory.getLogger(SyncUseCase.class);

    private final ExistingDataReaderPort existingDataReaderPort;
    private final ConcertScraperUseCase concertScraperUseCase;
    private final VenueScraperUseCase venueScraperUseCase;
    private final ArtistEnrichmentUseCase artistEnrichmentUseCase;
    private final SalaConciertoWritePort salaWriter;
    private final ArtistWritePort artistWriter;
    private final ConcertWritePort concertWriter;
    private final SyncMetadataWritePort syncMetaWriter;
    private final DataQualityWritePort qualityWriter;
    private final SyncRunPort syncRunPort;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate txTemplate;
    private final Clock clock;
    private final VenueGeocodingUseCase venueGeocodingUseCase;
    private final int lookaheadMonths;

    public SyncUseCase(
        ExistingDataReaderPort existingDataReaderPort,
        ConcertScraperUseCase concertScraperUseCase,
        VenueScraperUseCase venueScraperUseCase,
        ArtistEnrichmentUseCase artistEnrichmentUseCase,
        SalaConciertoWritePort salaWriter,
        ArtistWritePort artistWriter,
        ConcertWritePort concertWriter,
        SyncMetadataWritePort syncMetaWriter,
        DataQualityWritePort qualityWriter,
        SyncRunPort syncRunPort,
        ApplicationEventPublisher eventPublisher,
        TransactionTemplate txTemplate,
        Clock clock,
        VenueGeocodingUseCase venueGeocodingUseCase,
        @Value("${scraper.lookahead-months:3}") int lookaheadMonths
    ) {
        this.existingDataReaderPort = existingDataReaderPort;
        this.concertScraperUseCase = concertScraperUseCase;
        this.venueScraperUseCase = venueScraperUseCase;
        this.artistEnrichmentUseCase = artistEnrichmentUseCase;
        this.salaWriter = salaWriter;
        this.artistWriter = artistWriter;
        this.concertWriter = concertWriter;
        this.syncMetaWriter = syncMetaWriter;
        this.qualityWriter = qualityWriter;
        this.syncRunPort = syncRunPort;
        this.eventPublisher = eventPublisher;
        this.txTemplate = txTemplate;
        this.clock = clock;
        this.venueGeocodingUseCase = venueGeocodingUseCase;
        this.lookaheadMonths = lookaheadMonths;
    }

    @Override
    @Async
    public void execute(String runId) {
        LocalDate today = LocalDate.now(clock);
        execute(runId, today, today.plusMonths(lookaheadMonths));
    }

    @Override
    @Async
    public void execute(String runId, LocalDate from, LocalDate to) {
        try {
            doExecute(runId, from, to);
        } catch (Exception e) {
            log.error("Sync run {} failed: {}", runId, e.getMessage(), e);
            try {
                syncRunPort.fail(runId, e.getMessage());
            } catch (Exception failEx) {
                // Marking the run failed itself failed (e.g. DB locked). Since execute() is @Async,
                // an escaping exception here would be swallowed by Spring's async handler and the run
                // would silently stay 'running', blocking every future sync until startup cleanup.
                // Log loudly and swallow the secondary failure so the original cause still propagates.
                log.error("Sync run {} could not be marked failed; run may remain 'running' "
                    + "until startup cleanup clears orphaned runs: {}", runId, failEx.getMessage(), failEx);
            }
            throw e;
        }
    }

    private void doExecute(String runId, LocalDate from, LocalDate to) {
        // Capture the wall clock ONCE for the whole run. Deriving `today` from the same instant
        // used for timestamps keeps the run internally consistent even if it crosses midnight —
        // otherwise deleteBeforeDate could re-read the clock and purge concerts just ingested for `from`.
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        List<Discrepancy> discrepancies = new ArrayList<>();

        // 1. Read existing IDs from DB
        Set<String> existingConcertIds = existingDataReaderPort.existingConcertIds();
        Set<String> existingVenueIds = existingDataReaderPort.existingVenueIds();
        Set<String> existingArtistIds = existingDataReaderPort.existingArtistIds();
        Set<String> enrichedArtistIds = existingDataReaderPort.enrichedArtistIds();

        // 2. Scrape concerts
        log.info("[{}] Scraping concerts from {} to {}", runId, from, to);
        List<ScrapedConcert> allScrapedConcerts = concertScraperUseCase.scrape(from, to, discrepancies);

        // 3. Filter new concerts only
        List<ScrapedConcert> newScrapedConcerts = allScrapedConcerts.stream()
            .filter(sc -> !existingConcertIds.contains(sc.id()))
            .toList();
        log.info("[{}] {} new concerts (of {})", runId, newScrapedConcerts.size(), allScrapedConcerts.size());

        // 4. Extract new venue URLs
        List<String> newVenueUrls = newScrapedConcerts.stream()
            .map(ScrapedConcert::venueHref)
            .filter(href -> href != null && !href.isBlank())
            .distinct()
            .filter(href -> {
                String pathPart = href.startsWith("http") ? href.replaceFirst("https?://[^/]+", "") : href;
                String[] parts = pathPart.split("/");
                if (parts.length < 4) return true;
                String venueId = SlugUtils.venueId(parts[1], parts[3]);
                return !existingVenueIds.contains(venueId);
            })
            .toList();

        // 5. Scrape new venue details
        List<ScrapedVenue> newScrapedVenues = venueScraperUseCase.scrapeByUrls(newVenueUrls, discrepancies);
        log.info("[{}] {} new venues scraped", runId, newScrapedVenues.size());

        // 6. Build all known venue IDs (existing + successfully scraped)
        Set<String> allVenueIds = new HashSet<>(existingVenueIds);
        newScrapedVenues.forEach(v -> allVenueIds.add(v.id()));

        // 7. Resolve concert→venue mapping — persist-and-mark for missing venues
        List<Concert> newConcerts = new ArrayList<>();
        List<ScrapedConcert> matchedConcerts = new ArrayList<>();
        List<SalaConcierto> partialSalas = new ArrayList<>();

        for (ScrapedConcert sc : newScrapedConcerts) {
            if (sc.date() == null) {
                log.warn("[{}] Skipping concert id={} — null date, cannot persist", runId, sc.id());
                continue;
            }
            String venueId = resolveVenueId(sc);
            if (venueId != null && allVenueIds.contains(venueId)) {
                matchedConcerts.add(sc);
                newConcerts.add(new Concert(sc.id(), venueId, sc.artistSlugs(),
                    sc.date(), sc.time(), sc.price(), sc.sourceUrl(), now));
            } else {
                // Venue detail not available — attempt partial sala creation
                if (sc.venueName() == null || sc.venueName().isBlank()) {
                    String fallbackId = venueId != null ? venueId : "unknown";
                    discrepancies.add(new Discrepancy(
                        DiscrepancyType.UNKNOWN_VENUE, Severity.WARNING,
                        "concert", sc.id(), "salaConciertoId",
                        "known venue", fallbackId,
                        sc.venueName() + " / " + sc.venueProvince(), now
                    ));
                    continue;
                }
                // The name-based id may already belong to a known venue (e.g. a concert
                // listed with an external/unresolvable href) — link to it instead of
                // creating a partial whose upsert would blank out the real sala.
                String nameBasedId = SlugUtils.venueId(
                    sc.venueProvince() != null ? sc.venueProvince() : "", sc.venueName());
                if (allVenueIds.contains(nameBasedId)) {
                    matchedConcerts.add(sc);
                    newConcerts.add(new Concert(sc.id(), nameBasedId, sc.artistSlugs(),
                        sc.date(), sc.time(), sc.price(), sc.sourceUrl(), now));
                    continue;
                }
                // Create partial sala from listing data
                SalaConcierto partialSala = createPartialSala(sc, now);
                partialSalas.add(partialSala);
                allVenueIds.add(partialSala.id());
                matchedConcerts.add(sc);
                newConcerts.add(new Concert(sc.id(), partialSala.id(), sc.artistSlugs(),
                    sc.date(), sc.time(), sc.price(), sc.sourceUrl(), now));
            }
        }
        if (!partialSalas.isEmpty()) {
            log.info("[{}] {} partial salas created for venues with missing details", runId, partialSalas.size());
        }

        // 8. Collect all artist slugs referenced by new concerts
        Set<String> referencedArtistSlugs = newConcerts.stream()
            .flatMap(c -> c.artistIds().stream())
            .collect(Collectors.toSet());

        // 9. Enrich artists not yet in DB or without description (errors swallowed)
        List<Artist> newArtists = new ArrayList<>();
        try {
            List<ScrapedArtist> scraped = artistEnrichmentUseCase.enrich(
                matchedConcerts, enrichedArtistIds, discrepancies
            );
            newArtists = scraped.stream().map(a -> a.toDomain(now)).toList();
            log.info("[{}] {} artists enriched", runId, newArtists.size());
        } catch (Exception e) {
            log.warn("[{}] Artist enrichment failed (sync continues): {}", runId, e.getMessage());
        }

        Set<String> enrichedNow = newArtists.stream().map(Artist::id).collect(Collectors.toSet());

        // 10. Build stub artists for slugs that don't exist in DB and weren't enriched
        List<Artist> stubArtists = new ArrayList<>();
        for (String slug : referencedArtistSlugs) {
            if (!existingArtistIds.contains(slug) && !enrichedNow.contains(slug)) {
                String displayName = matchedConcerts.stream()
                    .filter(sc -> sc.artistSlugs().contains(slug))
                    .map(ScrapedConcert::artistName)
                    .findFirst().orElse(slug);
                stubArtists.add(new Artist(slug, displayName, null, null, null, null, null, now));
            }
        }
        if (!stubArtists.isEmpty()) {
            log.info("[{}] {} stub artists created to satisfy FK", runId, stubArtists.size());
        }

        // Sequential geocoding (one external lookup per new venue) is intentional: Foursquare and
        // LocationIQ enforce strict per-second rate limits, so parallelizing here would trade a
        // non-issue (few new venues per sync) for 429s. Revisit only if new-venues-per-run grows large.
        List<SalaConcierto> newSalas = newScrapedVenues.stream()
            .map(v -> venueGeocodingUseCase.geocodeIfNeeded(v.toDomain(now), now))
            .toList();

        final List<Artist> enrichedArtists = newArtists;
        int discrepancyCount = discrepancies.size();

        txTemplate.executeWithoutResult(status -> {
            // 11. Delete past concerts
            int pastDeleted = concertWriter.deleteBeforeDate(today);
            log.info("[{}] Deleted {} past concerts", runId, pastDeleted);

            // 12. FK-safe upsert order: salas (full) → partial salas → artists → concerts
            for (SalaConcierto sala : newSalas) {
                salaWriter.upsert(sala);
            }
            for (SalaConcierto partialSala : partialSalas) {
                // Defense in depth: a partial (all-null details) must never overwrite
                // an existing sala, even if target selection above regresses.
                salaWriter.insertIfAbsent(partialSala);
            }
            for (Artist artist : enrichedArtists) {
                artistWriter.upsert(artist);
            }
            for (Artist stub : stubArtists) {
                artistWriter.upsert(stub);
            }

            // 13. Bump updated_at for existing artists with new concerts
            Set<String> alreadyUpserted = new HashSet<>(enrichedNow);
            stubArtists.forEach(s -> alreadyUpserted.add(s.id()));
            for (String slug : referencedArtistSlugs) {
                if (existingArtistIds.contains(slug) && !alreadyUpserted.contains(slug)) {
                    artistWriter.touchUpdatedAt(slug, now);
                }
            }

            for (Concert concert : newConcerts) {
                concertWriter.upsert(concert);
            }

            // 14. Update sync metadata
            boolean artistsChanged = !enrichedArtists.isEmpty() || !stubArtists.isEmpty()
                || referencedArtistSlugs.stream().anyMatch(existingArtistIds::contains);
            List<SalaConcierto> allNewSalas = new ArrayList<>(newSalas);
            allNewSalas.addAll(partialSalas);
            if (!allNewSalas.isEmpty()) syncMetaWriter.updateLastModified("salas-concierto", now);
            if (artistsChanged) syncMetaWriter.updateLastModified("artists", now);
            if (!newConcerts.isEmpty()) syncMetaWriter.updateLastModified("concerts", now);

            // 15. Data quality detection (errors swallowed)
            try {
                List<DataQuality> issues = buildQualityIssues(allNewSalas, enrichedArtists, stubArtists, newConcerts, now);
                qualityWriter.saveAll(issues);
            } catch (Exception e) {
                log.warn("[{}] Data quality detection failed (sync continues): {}", runId, e.getMessage());
            }

            // 16. Complete the run — concerts count includes partial sala concerts
            syncRunPort.complete(runId,
                allNewSalas.size(), enrichedArtists.size(), newConcerts.size(),
                0, discrepancyCount);
        });

        // 17. Post-commit events (outside transaction)
        List<SalaConcierto> allNewSalasForEvent = new ArrayList<>(newSalas);
        allNewSalasForEvent.addAll(partialSalas);
        List<String> salaIds = allNewSalasForEvent.stream().map(SalaConcierto::id).toList();
        List<String> artistIds = enrichedArtists.stream().map(Artist::id).toList();
        if (!salaIds.isEmpty() || !artistIds.isEmpty()) {
            eventPublisher.publishEvent(new DataQualityCheckEvent(salaIds, artistIds));
        }

        log.info("[{}] Sync completed — salas={} artists={} concerts={} discrepancies={}",
            runId, newSalas.size() + partialSalas.size(), enrichedArtists.size(), newConcerts.size(), discrepancyCount);
    }

    private List<DataQuality> buildQualityIssues(
        List<SalaConcierto> salas,
        List<Artist> artists,
        List<Artist> stubArtists,
        List<Concert> concerts,
        Instant now
    ) {
        List<DataQuality> issues = new ArrayList<>();

        // Covers full AND partial salas — a fully scraped venue without an extractable
        // map pin must surface lat/lng gaps too, or it serves broken while staying
        // invisible to the admin pipeline.
        for (SalaConcierto sala : salas) {
            checkAndAddIssue(issues, "sala", sala.id(), "address", sala.address(), now);
            checkAndAddIssue(issues, "sala", sala.id(), "lat",
                sala.lat() != null ? sala.lat().toString() : null, now);
            checkAndAddIssue(issues, "sala", sala.id(), "lng",
                sala.lng() != null ? sala.lng().toString() : null, now);
        }

        for (Artist artist : artists) {
            checkAndAddIssue(issues, "artist", artist.id(), "genre", artist.genre(), now);
            checkAndAddIssue(issues, "artist", artist.id(), "description", artist.description(), now);
        }

        // Stub artists always have null genre — that's a severe issue
        for (Artist stub : stubArtists) {
            checkAndAddIssue(issues, "artist", stub.id(), "genre", stub.genre(), now);
        }

        for (Concert concert : concerts) {
            checkAndAddIssue(issues, "concert", concert.id(), "time", concert.time(), now);
        }

        return issues;
    }

    private void checkAndAddIssue(List<DataQuality> issues, String entityType, String entityId,
                                   String field, String value, Instant now) {
        if (value == null || value.isBlank()) {
            String severity = FieldSeverity.of(entityType, field).toDbValue();
            issues.add(new DataQuality(null, entityType, entityId, field, "missing", severity, null, null, null, now));
        }
    }

    private SalaConcierto createPartialSala(ScrapedConcert sc, Instant now) {
        String province = sc.venueProvince() != null ? sc.venueProvince() : "";
        String city = extractCityFromHref(sc.venueHref());
        String id = SlugUtils.venueId(province, sc.venueName());
        return new SalaConcierto(
            id,
            sc.venueName(),
            null,   // address — unknown, will be data_quality severe
            city != null ? city : province,
            province,
            null,   // lat — unknown, will be data_quality severe
            null,   // lng — unknown, will be data_quality severe
            null,   // imageUrl
            null,   // description
            null,   // sourceUrl
            now
        );
    }

    private String extractCityFromHref(String href) {
        if (href == null || href.isBlank()) return null;
        String pathPart = href.startsWith("http") ? href.replaceFirst("https?://[^/]+", "") : href;
        String[] parts = pathPart.split("/");
        // href pattern: /province/locales/venue-slug — city not in href, use province
        return parts.length >= 2 ? parts[1] : null;
    }

    private String resolveVenueId(ScrapedConcert sc) {
        String href = sc.venueHref();
        if (href == null || href.isBlank()) return null;
        String pathPart = href.startsWith("http") ? href.replaceFirst("https?://[^/]+", "") : href;
        String[] parts = pathPart.split("/");
        if (parts.length < 4) return null;
        return SlugUtils.venueId(parts[1], parts[3]);
    }
}
