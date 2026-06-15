package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.DataQualityInputPort;
import com.rubenazo.buscaConciertos.application.ports.in.GeocodingAdminInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.EntityEnrichmentPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.TavilySearchPort;
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupCandidate;
import com.rubenazo.buscaConciertos.application.ports.out.VenueMatch;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;
import com.rubenazo.buscaConciertos.domain.EnrichedFieldValue;
import com.rubenazo.buscaConciertos.domain.EntityEnrichmentRequest;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.domain.ScrapedCoordinateValidator;
import com.rubenazo.buscaConciertos.domain.SearchOptions;
import com.rubenazo.buscaConciertos.domain.TavilyResult;
import com.rubenazo.buscaConciertos.scraper.application.parsers.VenueDetailParser;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Auto-fills missing fields (the "data quality" pipeline) and exposes the admin review surface.
 *
 * Triggered async by {@link DataQualityCheckEvent} after a sync ({@link #onDataQualityCheck}): for
 * each missing enrichable field it runs RAG — a Tavily web search ({@link TavilySearchPort}) feeds an
 * LLM ({@link EntityEnrichmentPort}) which proposes values. Proposals scoring at or above
 * {@code SCORE_THRESHOLD} (0.7) become {@code auto_found}; below stays {@code missing}. Concerts are
 * never enriched; only the sala/artist fields in the {@code *_ENRICHABLE_FIELDS} sets are.
 *
 * Also implements {@link GeocodingAdminInputPort}: the backfill/fill-coords admin operations, plus
 * the manual review actions ({@link #approve}, {@link #reject}, {@link #fill}, {@link #approveAll}).
 */
@Service
public class DataQualityUseCase implements DataQualityInputPort, GeocodingAdminInputPort {

    private static final Logger log = LoggerFactory.getLogger(DataQualityUseCase.class);

    private static final List<String> VALID_STATUSES = List.of(
        "missing", "auto_found", "approved", "auto_approved", "rejected"
    );
    private static final double SCORE_THRESHOLD = 0.7;

    // Enrichable fields per entity type — concerts are never enriched
    private static final Set<String> SALA_ENRICHABLE_FIELDS = Set.of("address", "description");
    private static final Set<String> ARTIST_ENRICHABLE_FIELDS = Set.of("genre", "description");

    private static final String RESOURCE_ARTISTS = "artists";
    private static final String RESOURCE_SALAS = "salas-concierto";
    private static final String RESOURCE_CONCERTS = "concerts";
    private static final Set<String> FOURSQUARE_FILLABLE_SALA_FIELDS = Set.of("address", "lat", "lng");
    private static final int FILL_MISSING_DEFAULT_CANDIDATE_LIMIT = 3;
    private static final int FILL_MISSING_MAX_LIMIT = 100;

    // fillSalaCoordsFromScraper (ADR-5..9)
    // Higher cap than FILL_MISSING_MAX_LIMIT: scraper fills hit conciertos.club (already
    // rate-limited at scraper.rate-limit-ms), not a paid geocoding API. Target selection
    // has no pagination, so the cap must cover every conciertos.club sala (~570 in prod)
    // for a full backfill to be reachable in one call.
    private static final int FILL_FROM_SCRAPER_MAX_LIMIT = 600;
    private static final String CONCIERTOS_CLUB_BASE_URL = "https://conciertos.club";
    private static final String SCRAPER_PROVIDER = "conciertos-club-page";
    private static final double SCRAPER_CONFIDENCE = 1.0;
    private static final double COORDINATE_EPSILON = 1e-6;
    // No data_quality row currently carries source="manual" (manual fill via /fill only
    // updates status, not source). Kept per ADR-7 as a forward-compatible, zero-cost
    // belt-and-suspenders check; the manual-sourceUrl prefix exclusion in
    // findScraperBackfillTargets is the effective manual-detection mechanism today.
    private static final String MANUAL_RESOLUTION_SOURCE = "manual";

    private final DataQualityRepositoryPort repository;
    private final DataQualityWritePort writer;
    private final TavilySearchPort tavilySearch;
    private final ArtistWritePort artistWritePort;
    private final SalaConciertoWritePort salaConciertoWritePort;
    private final SyncMetadataWritePort syncMetadataWritePort;
    private final Clock clock;
    private final int maxCallsPerImport;
    private final double autoFillThreshold;
    private final ArtistRepositoryPort artistRepository;
    private final SalaConciertoRepositoryPort salaRepository;
    private final EntityEnrichmentPort entityEnrichmentPort;
    private final ConcertRepositoryPort concertRepository;
    private final String searchDepth;
    private final int maxResults;
    private final VenueGeocodingUseCase venueGeocodingUseCase;
    private final double autoAcceptThreshold;
    private final HtmlFetchPort htmlFetchPort;
    private final VenueDetailParser venueDetailParser;

    /**
     * Spring-managed constructor. Injects all ports and configuration values via @Value.
     */
    @Autowired
    public DataQualityUseCase(
        DataQualityRepositoryPort repository,
        DataQualityWritePort writer,
        TavilySearchPort tavilySearch,
        ArtistWritePort artistWritePort,
        SalaConciertoWritePort salaConciertoWritePort,
        SyncMetadataWritePort syncMetadataWritePort,
        Clock clock,
        @Value("${app.llm.max-calls-per-import:20}") int maxCallsPerImport,
        ArtistRepositoryPort artistRepository,
        SalaConciertoRepositoryPort salaRepository,
        @Value("${app.tavily.auto-fill-threshold:0.8}") double autoFillThreshold,
        EntityEnrichmentPort entityEnrichmentPort,
        ConcertRepositoryPort concertRepository,
        @Value("${app.tavily.search-depth:basic}") String searchDepth,
        @Value("${app.tavily.max-results:5}") int maxResults,
        VenueGeocodingUseCase venueGeocodingUseCase,
        @Value("${app.foursquare.auto-accept-threshold:0.8}") double autoAcceptThreshold,
        HtmlFetchPort htmlFetchPort,
        VenueDetailParser venueDetailParser
    ) {
        this.repository = repository;
        this.writer = writer;
        this.tavilySearch = tavilySearch;
        this.artistWritePort = artistWritePort;
        this.salaConciertoWritePort = salaConciertoWritePort;
        this.syncMetadataWritePort = syncMetadataWritePort;
        this.clock = clock;
        this.maxCallsPerImport = maxCallsPerImport;
        this.autoFillThreshold = autoFillThreshold;
        this.artistRepository = artistRepository;
        this.salaRepository = salaRepository;
        this.entityEnrichmentPort = entityEnrichmentPort;
        this.concertRepository = concertRepository;
        this.searchDepth = searchDepth;
        this.maxResults = maxResults;
        this.venueGeocodingUseCase = venueGeocodingUseCase;
        this.autoAcceptThreshold = autoAcceptThreshold;
        this.htmlFetchPort = htmlFetchPort;
        this.venueDetailParser = venueDetailParser;
    }

    /**
     * Test-friendly constructor that allows explicit injection of autoAcceptThreshold.
     * Used by unit tests that need to control the threshold without Spring context.
     *
     * <p>Delegates with {@code null} for {@code htmlFetchPort}/{@code venueDetailParser}:
     * existing tests using this constructor never call
     * {@link #fillSalaCoordsFromScraper(boolean, int)}, so the unused fields are safe.
     */
    public DataQualityUseCase(
        DataQualityRepositoryPort repository,
        DataQualityWritePort writer,
        TavilySearchPort tavilySearch,
        ArtistWritePort artistWritePort,
        SalaConciertoWritePort salaConciertoWritePort,
        SyncMetadataWritePort syncMetadataWritePort,
        Clock clock,
        int maxCallsPerImport,
        ArtistRepositoryPort artistRepository,
        SalaConciertoRepositoryPort salaRepository,
        double autoFillThreshold,
        EntityEnrichmentPort entityEnrichmentPort,
        ConcertRepositoryPort concertRepository,
        String searchDepth,
        int maxResults,
        VenueGeocodingUseCase venueGeocodingUseCase
    ) {
        this(repository, writer, tavilySearch, artistWritePort, salaConciertoWritePort,
            syncMetadataWritePort, clock, maxCallsPerImport, artistRepository, salaRepository,
            autoFillThreshold, entityEnrichmentPort, concertRepository, searchDepth, maxResults,
            venueGeocodingUseCase, 0.8, null, null);
    }

    /**
     * Test-friendly constructor that additionally allows explicit injection of
     * {@link HtmlFetchPort} and {@link VenueDetailParser} for tests covering
     * {@link #fillSalaCoordsFromScraper(boolean, int)}.
     */
    public DataQualityUseCase(
        DataQualityRepositoryPort repository,
        DataQualityWritePort writer,
        TavilySearchPort tavilySearch,
        ArtistWritePort artistWritePort,
        SalaConciertoWritePort salaConciertoWritePort,
        SyncMetadataWritePort syncMetadataWritePort,
        Clock clock,
        int maxCallsPerImport,
        ArtistRepositoryPort artistRepository,
        SalaConciertoRepositoryPort salaRepository,
        double autoFillThreshold,
        EntityEnrichmentPort entityEnrichmentPort,
        ConcertRepositoryPort concertRepository,
        String searchDepth,
        int maxResults,
        VenueGeocodingUseCase venueGeocodingUseCase,
        HtmlFetchPort htmlFetchPort,
        VenueDetailParser venueDetailParser
    ) {
        this(repository, writer, tavilySearch, artistWritePort, salaConciertoWritePort,
            syncMetadataWritePort, clock, maxCallsPerImport, artistRepository, salaRepository,
            autoFillThreshold, entityEnrichmentPort, concertRepository, searchDepth, maxResults,
            venueGeocodingUseCase, 0.8, htmlFetchPort, venueDetailParser);
    }

    @Async
    @EventListener
    public void onDataQualityCheck(DataQualityCheckEvent event) {
        try {
        List<DataQuality> missingRows = repository.findByStatus("missing");
        if (missingRows.isEmpty()) {
            return;
        }

        // Build lookups for entity metadata
        Map<String, SalaConcierto> salasById = new HashMap<>();
        salaRepository.findAllIncludingBlocked().forEach(s -> salasById.put(s.id(), s));

        Map<String, Artist> artistsById = new HashMap<>();
        artistRepository.findAll().forEach(a -> artistsById.put(a.id(), a));

        // Build sala lookup by id for concert → venue city derivation
        // (already in salasById above)

        // Build concerts list once, including currently blocked rows, for impact ordering and artist context hints.
        List<Concert> allConcerts = concertRepository.findAllIncludingBlocked();

        // Build sala-id → city/province index from concerts per artist
        // Computed lazily per artist entity in the loop below

        // Compute today once so the sort comparator is consistent even across a midnight boundary.
        LocalDate today = LocalDate.now(clock);

        List<DataQuality> prioritizedRows = missingRows.stream()
            .filter(this::isEnrichableRow)
            .sorted(Comparator
                .comparingInt((DataQuality row) -> blockedConcertCount(row, allConcerts, today))
                .reversed()
                .thenComparingInt(row -> entityTypePriority(row.entityType())))
            .toList();

        // Group by (entityType, entityId) preserving impact-prioritized order.
        // Use LinkedHashMap to preserve insertion order.
        Map<String, List<DataQuality>> buckets = new LinkedHashMap<>();
        for (DataQuality row : prioritizedRows) {
            String key = row.entityType() + ":" + row.entityId();
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        SearchOptions options = new SearchOptions(searchDepth, maxResults);
        Instant now = Instant.now(clock);
        int llmCalls = 0;

        for (Map.Entry<String, List<DataQuality>> entry : buckets.entrySet()) {
            if (llmCalls >= maxCallsPerImport) {
                break;
            }

            List<DataQuality> bucket = entry.getValue();
            DataQuality first = bucket.get(0);
            String entityType = first.entityType();
            String entityId = first.entityId();

            // Resolve entity metadata
            String name;
            String city = null;
            String province = null;
            Map<String, String> knownFields = new LinkedHashMap<>();
            Map<String, String> contextHints = new LinkedHashMap<>();

            if ("sala".equals(entityType)) {
                SalaConcierto sala = salasById.get(entityId);
                if (sala != null) {
                    name = sala.name();
                    city = sala.city();
                    province = sala.province();
                    // Build knownFields from all non-null sala fields
                    if (sala.name() != null) knownFields.put("name", sala.name());
                    if (sala.address() != null) knownFields.put("address", sala.address());
                    if (sala.city() != null) knownFields.put("city", sala.city());
                    if (sala.province() != null) knownFields.put("province", sala.province());
                    if (sala.imageUrl() != null) knownFields.put("imageUrl", sala.imageUrl());
                    if (sala.description() != null) knownFields.put("description", sala.description());
                    if (sala.sourceUrl() != null) knownFields.put("sourceUrl", sala.sourceUrl());
                } else {
                    name = entityId;
                }
                // Salas already have city/province as first-class fields — contextHints stays empty
            } else {
                // artist
                Artist artist = artistsById.get(entityId);
                if (artist != null) {
                    name = artist.name();
                    // Build knownFields from all non-null artist fields
                    if (artist.name() != null) knownFields.put("name", artist.name());
                    if (artist.genre() != null) knownFields.put("genre", artist.genre());
                    if (artist.imageUrl() != null) knownFields.put("imageUrl", artist.imageUrl());
                    if (artist.website() != null) knownFields.put("website", artist.website());
                    if (artist.sourceUrl() != null) knownFields.put("sourceUrl", artist.sourceUrl());
                    if (artist.description() != null) knownFields.put("description", artist.description());
                } else {
                    name = entityId;
                }
                // Derive venue cities from concerts where this artist plays
                String venueCities = allConcerts.stream()
                    .filter(c -> c.artistIds().contains(entityId))
                    .map(Concert::salaConciertoId)
                    .distinct()
                    .map(salasById::get)
                    .filter(s -> s != null && s.city() != null)
                    .map(SalaConcierto::city)
                    .distinct()
                    .collect(Collectors.joining(", "));
                if (!venueCities.isBlank()) {
                    contextHints.put("ciudades_donde_actua", venueCities);
                }
            }

            // Collect per-field Tavily evidence
            List<String> missingFields = bucket.stream().map(DataQuality::field).toList();
            Map<String, List<TavilyResult>> evidence = new LinkedHashMap<>();
            for (DataQuality row : bucket) {
                String field = row.field();
                String query = buildQuery(name, field, city, province, contextHints);
                try {
                    List<TavilyResult> results = tavilySearch.search(query, options);
                    evidence.put(field, results);
                } catch (Exception ex) {
                    log.error("Tavily search failed for entity_type={} entity_id={} field={}: {}",
                        entityType, entityId, field, ex.getMessage(), ex);
                    evidence.put(field, List.of());
                }
            }

            // Build enrichment request and call LLM once per entity
            EntityEnrichmentRequest request = new EntityEnrichmentRequest(
                entityType, entityId, name, city, province,
                knownFields, missingFields, contextHints, evidence
            );

            EnrichedEntityResult result;
            try {
                result = entityEnrichmentPort.enrich(request);
            } catch (Exception ex) {
                log.error("LLM enrichment failed for entity_type={} entity_id={}: {}",
                    entityType, entityId, ex.getMessage(), ex);
                result = EnrichedEntityResult.empty();
            }
            llmCalls++;

            // Route each field by confidence
            for (DataQuality row : bucket) {
                EnrichedFieldValue fv = result.fields().get(row.field());
                routeField(row, fv, now);
            }
        }

        log.info("RAG enrichment: {} LLM calls (cap {})", llmCalls, maxCallsPerImport);
        } catch (Exception ex) {
            log.error("Data quality enrichment failed: {}", ex.getMessage(), ex);
        }
    }

    private boolean isEnrichableRow(DataQuality row) {
        if ("sala".equals(row.entityType())) {
            return SALA_ENRICHABLE_FIELDS.contains(row.field());
        }
        if ("artist".equals(row.entityType())) {
            return ARTIST_ENRICHABLE_FIELDS.contains(row.field());
        }
        return false;
    }

    private int blockedConcertCount(DataQuality row, List<Concert> concerts, LocalDate today) {
        return (int) concerts.stream()
            .filter(concert -> !concert.date().isBefore(today))
            .filter(concert -> impactsConcert(row, concert))
            .count();
    }

    private boolean impactsConcert(DataQuality row, Concert concert) {
        if ("sala".equals(row.entityType())) {
            return row.entityId().equals(concert.salaConciertoId());
        }
        if ("artist".equals(row.entityType())) {
            return concert.artistIds().contains(row.entityId());
        }
        if ("concert".equals(row.entityType())) {
            return row.entityId().equals(concert.id());
        }
        return false;
    }

    private int entityTypePriority(String entityType) {
        return switch (entityType) {
            case "sala" -> 0;
            case "artist" -> 1;
            default -> 2;
        };
    }

    private void routeField(DataQuality row, EnrichedFieldValue fv, Instant now) {
        // Order is deliberate: applyResolution (entity write) happens BEFORE updateSuggestion (status write).
        // If updateSuggestion fails, the entity holds the good value and the row stays non-terminal,
        // so re-approval is safe and idempotent. Inverting would leave the row marked resolved while
        // the entity was never updated — the harmful state. True atomicity requires @Transactional (deferred).
        if (fv == null || fv.value() == null || fv.value().isBlank() || fv.confidence() < SCORE_THRESHOLD) {
            return; // stays missing
        }
        boolean severe = isSevereField(row.entityType(), row.field());
        if (!severe && fv.confidence() >= autoFillThreshold) {
            applyResolution(row, fv.value(), now);
            writer.updateSuggestion(row.id(), "auto_approved", fv.value(), fv.sourceUrl(), fv.confidence(), now);
        } else {
            writer.updateSuggestion(row.id(), "auto_found", fv.value(), fv.sourceUrl(), fv.confidence(), now);
        }
    }

    private boolean isSevereField(String entityType, String field) {
        if ("sala".equals(entityType)) {
            return "address".equals(field);
        }
        if ("artist".equals(entityType)) {
            return "genre".equals(field);
        }
        return false;
    }

    private String buildQuery(String name, String field, String city, String province,
                               Map<String, String> contextHints) {
        String spanishTerm = spanishFieldTerm(field);
        StringBuilder query = new StringBuilder(name).append(" ").append(spanishTerm);
        if (city != null) {
            query.append(" ").append(city);
        } else if (contextHints.containsKey("ciudades_donde_actua")) {
            // Use derived venue cities to improve grounding for artists with no home city
            query.append(" ").append(contextHints.get("ciudades_donde_actua"));
        }
        if (province != null) {
            query.append(" ").append(province);
        }
        return query.toString();
    }

    private String spanishFieldTerm(String field) {
        return switch (field) {
            case "address" -> "dirección";
            case "description" -> "descripción";
            case "genre" -> "género musical";
            default -> field;
        };
    }

    /**
     * Shared write path for both manual approve and auto-fill.
     * Writes the field value to the entity and bumps sync_meta.last_modified.
     */
    private void applyResolution(DataQuality dq, String value, Instant now) {
        if ("artist".equals(dq.entityType())) {
            artistWritePort.updateField(dq.entityId(), dq.field(), value, now);
            syncMetadataWritePort.updateLastModified(RESOURCE_ARTISTS, now);
        } else if ("sala".equals(dq.entityType())) {
            salaConciertoWritePort.updateField(dq.entityId(), dq.field(), value, now);
            syncMetadataWritePort.updateLastModified(RESOURCE_SALAS, now);
        }
    }

    private void geocodeSalaAddressIfNeeded(DataQuality dq, Instant now) {
        if (!"sala".equals(dq.entityType()) || !"address".equals(dq.field())) {
            return;
        }
        Optional<SalaConcierto> maybeSala = salaRepository.findByIdIncludingBlocked(dq.entityId());
        if (maybeSala.isEmpty()) {
            log.warn("Skipping geocoding after address approval: sala_id={} was not found", dq.entityId());
            return;
        }
        Optional<VenueMatch> maybeMatch = venueGeocodingUseCase.geocode(maybeSala.get());
        if (maybeMatch.isEmpty()) {
            return;    // both providers empty → leave coords null
        }

        VenueMatch m = maybeMatch.get();
        if (!isWritableCoordinate(m.lat(), m.lng())) {
            log.warn("Skipping geocoding after address approval: non-finite or out-of-range coordinates from provider={} sala_id={} lat={} lng={}",
                m.provider(), dq.entityId(), m.lat(), m.lng());
            return;
        }
        String lat = Double.toString(m.lat());
        String lng = Double.toString(m.lng());

        if (m.confidence() >= autoAcceptThreshold) {
            // Confident → write entity coords and auto_approved with real provider + real score
            salaConciertoWritePort.updateField(dq.entityId(), "lat", lat, now);
            salaConciertoWritePort.updateField(dq.entityId(), "lng", lng, now);
            writer.upsertResolution("sala", dq.entityId(), "lat", "auto_approved", lat, m.provider(), m.confidence(), now);
            writer.upsertResolution("sala", dq.entityId(), "lng", "auto_approved", lng, m.provider(), m.confidence(), now);
            syncMetadataWritePort.updateLastModified(RESOURCE_SALAS, now);
            syncMetadataWritePort.updateLastModified(RESOURCE_CONCERTS, now);
        } else {
            // Weak match → do NOT write entity coords; emit data_quality rows for manual review
            // (status "missing" → surfaces in existing SEVERE/admin-web pipeline with a candidate value)
            writer.upsertResolution("sala", dq.entityId(), "lat", "missing", lat, m.provider(), m.confidence(), now);
            writer.upsertResolution("sala", dq.entityId(), "lng", "missing", lng, m.provider(), m.confidence(), now);
        }
    }

    @Override
    public BackfillResult backfillLocationIqSalas() {
        Instant now = Instant.now(clock);
        List<String> ids = repository.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng"));
        int overwritten = 0, kept = 0, notFound = 0;
        for (String id : ids) {
            Optional<SalaConcierto> maybeSala = salaRepository.findByIdIncludingBlocked(id);
            if (maybeSala.isEmpty()) {
                notFound++;
                continue;
            }
            Optional<VenueMatch> maybeMatch = venueGeocodingUseCase.geocode(maybeSala.get());
            // Overwrite ONLY on a confident Foursquare match with valid coordinates; otherwise keep existing LocationIQ coords
            if (maybeMatch.isPresent()
                    && "foursquare".equals(maybeMatch.get().provider())
                    && maybeMatch.get().confidence() >= autoAcceptThreshold
                    && isWritableCoordinate(maybeMatch.get().lat(), maybeMatch.get().lng())) {
                VenueMatch m = maybeMatch.get();
                String lat = Double.toString(m.lat());
                String lng = Double.toString(m.lng());
                salaConciertoWritePort.updateField(id, "lat", lat, now);
                salaConciertoWritePort.updateField(id, "lng", lng, now);
                writer.upsertResolution("sala", id, "lat", "auto_approved", lat, "foursquare", m.confidence(), now);
                writer.upsertResolution("sala", id, "lng", "auto_approved", lng, "foursquare", m.confidence(), now);
                overwritten++;
            } else {
                kept++;    // existing locationiq value preserved (non-destructive)
            }
        }
        if (overwritten > 0) {
            syncMetadataWritePort.updateLastModified(RESOURCE_SALAS, now);
            syncMetadataWritePort.updateLastModified(RESOURCE_CONCERTS, now);
        }
        return new BackfillResult(ids.size(), overwritten, kept, notFound);
    }

    @Override
    public FillMissingResult fillMissingSalaCoords(boolean dryRun, int limit) {
        if (limit < 1 || limit > FILL_MISSING_MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "limit must be between 1 and " + FILL_MISSING_MAX_LIMIT);
        }

        Instant now = Instant.now(clock);
        List<SalaConcierto> salas = findFillMissingTargetSalas(limit);

        List<FillMissingItem> items = new ArrayList<>();
        int written = 0;
        int wouldWrite = 0;
        int addressesWritten = 0;
        int addressesWouldWrite = 0;
        int needsReview = 0;
        int noMatch = 0;

        for (SalaConcierto sala : salas) {
            Optional<VenueLookupCandidate> maybeMatch = venueGeocodingUseCase.bestFoursquareCandidate(
                sala, FILL_MISSING_DEFAULT_CANDIDATE_LIMIT);
            if (maybeMatch.isEmpty()) {
                noMatch++;
                items.add(fillMissingItem(sala, null, "no_match", "no_match", "No Foursquare candidate returned"));
                continue;
            }

            VenueLookupCandidate match = maybeMatch.get();
            boolean confidentFoursquare = "foursquare".equals(match.provider())
                && match.confidence() >= autoAcceptThreshold;
            if (!confidentFoursquare) {
                needsReview++;
                items.add(fillMissingItem(sala, match, "needs_review", addressDecisionForNonWrite(sala, match),
                    "Foursquare confidence is below threshold " + autoAcceptThreshold));
                continue;
            }

            boolean coordsWritable = isWritableCoordinate(match.lat(), match.lng());
            if (!coordsWritable) {
                log.warn("Skipping coordinate write in fillMissingSalaCoords: non-finite or out-of-range coordinates from provider={} sala_id={} lat={} lng={}",
                    match.provider(), sala.id(), match.lat(), match.lng());
            }
            boolean shouldWriteLat = sala.lat() == null && coordsWritable;
            boolean shouldWriteLng = sala.lng() == null && coordsWritable;
            boolean shouldWriteAddress = shouldWriteCandidateAddress(sala, match);
            boolean shouldWriteAnyField = shouldWriteLat || shouldWriteLng || shouldWriteAddress;
            if (!shouldWriteAnyField) {
                needsReview++;
                items.add(fillMissingItem(sala, match, "needs_review", addressDecisionForNonWrite(sala, match),
                    "No missing field can be filled from this Foursquare candidate"));
                continue;
            }

            if (dryRun) {
                wouldWrite++;
                if (shouldWriteAddress) {
                    addressesWouldWrite++;
                }
                items.add(fillMissingItem(sala, match, "would_write",
                    shouldWriteAddress ? "would_write" : addressDecisionForNonWrite(sala, match),
                    "Dry run: missing fields from confident Foursquare match would be written"));
            } else {
                if (shouldWriteLat) {
                    String lat = Double.toString(match.lat());
                    salaConciertoWritePort.updateField(sala.id(), "lat", lat, now);
                    writer.upsertResolution("sala", sala.id(), "lat", "auto_approved",
                        lat, "foursquare", match.confidence(), now);
                }
                if (shouldWriteLng) {
                    String lng = Double.toString(match.lng());
                    salaConciertoWritePort.updateField(sala.id(), "lng", lng, now);
                    writer.upsertResolution("sala", sala.id(), "lng", "auto_approved",
                        lng, "foursquare", match.confidence(), now);
                }

                String addressDecision = addressDecisionForNonWrite(sala, match);
                if (shouldWriteAddress) {
                    String address = match.formattedAddress().trim();
                    salaConciertoWritePort.updateField(sala.id(), "address", address, now);
                    writer.upsertResolution("sala", sala.id(), "address", "auto_approved",
                        address, "foursquare", match.confidence(), now);
                    addressesWritten++;
                    addressDecision = "written";
                }

                written++;
                items.add(fillMissingItem(sala, match, "written", addressDecision,
                    "Missing fields from confident Foursquare match written"));
            }
        }

        if (written > 0) {
            syncMetadataWritePort.updateLastModified(RESOURCE_SALAS, now);
            syncMetadataWritePort.updateLastModified(RESOURCE_CONCERTS, now);
        }

        return new FillMissingResult(dryRun, limit, autoAcceptThreshold,
            salas.size(), written, wouldWrite, addressesWritten, addressesWouldWrite,
            needsReview, noMatch, items);
    }

    private FillMissingItem fillMissingItem(SalaConcierto sala, VenueLookupCandidate match,
                                            String decision, String addressDecision, String reason) {
        return new FillMissingItem(
            sala.id(),
            sala.name(),
            sala.city(),
            sala.province(),
            match != null ? match.displayName() : null,
            match != null ? match.formattedAddress() : null,
            match != null ? match.lat() : null,
            match != null ? match.lng() : null,
            match != null ? match.provider() : null,
            match != null ? match.confidence() : null,
            decision,
            addressDecision,
            reason
        );
    }

    @Override
    public FillFromScraperResult fillSalaCoordsFromScraper(boolean dryRun, int limit) {
        if (limit < 1 || limit > FILL_FROM_SCRAPER_MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "limit must be between 1 and " + FILL_FROM_SCRAPER_MAX_LIMIT);
        }

        Instant now = Instant.now(clock);
        List<SalaConcierto> targets = findScraperBackfillTargets(limit);
        Set<String> manualSalaIds = Set.copyOf(
            repository.findEntityIdsBySourceAndField("sala", MANUAL_RESOLUTION_SOURCE, List.of("lat", "lng")));

        List<FillFromScraperItem> items = new ArrayList<>();
        int written = 0;
        int wouldWrite = 0;
        int keptManual = 0;
        int keptNoChange = 0;
        int noCoords = 0;

        for (SalaConcierto sala : targets) {
            Optional<ScrapedVenue> maybeVenue = fetchAndParse(sala);
            Double scrapedLat = maybeVenue.map(ScrapedVenue::lat).orElse(null);
            Double scrapedLng = maybeVenue.map(ScrapedVenue::lng).orElse(null);

            if (scrapedLat == null || scrapedLng == null) {
                noCoords++;
                items.add(new FillFromScraperItem(sala.id(), sala.name(),
                    sala.lat(), sala.lng(), null, null, "no-coords"));
                continue;
            }

            if (manualSalaIds.contains(sala.id())) {
                keptManual++;
                items.add(new FillFromScraperItem(sala.id(), sala.name(),
                    sala.lat(), sala.lng(), scrapedLat, scrapedLng, "kept-manual"));
                continue;
            }

            if (coordinatesEqual(sala.lat(), scrapedLat) && coordinatesEqual(sala.lng(), scrapedLng)) {
                keptNoChange++;
                items.add(new FillFromScraperItem(sala.id(), sala.name(),
                    sala.lat(), sala.lng(), scrapedLat, scrapedLng, "kept-no-change"));
                continue;
            }

            if (dryRun) {
                wouldWrite++;
                items.add(new FillFromScraperItem(sala.id(), sala.name(),
                    sala.lat(), sala.lng(), scrapedLat, scrapedLng, "wouldWrite"));
                continue;
            }

            String latStr = Double.toString(scrapedLat);
            String lngStr = Double.toString(scrapedLng);
            salaConciertoWritePort.updateField(sala.id(), "lat", latStr, now);
            salaConciertoWritePort.updateField(sala.id(), "lng", lngStr, now);
            writer.upsertResolution("sala", sala.id(), "lat", "auto_approved",
                latStr, SCRAPER_PROVIDER, SCRAPER_CONFIDENCE, now);
            writer.upsertResolution("sala", sala.id(), "lng", "auto_approved",
                lngStr, SCRAPER_PROVIDER, SCRAPER_CONFIDENCE, now);
            written++;
            items.add(new FillFromScraperItem(sala.id(), sala.name(),
                sala.lat(), sala.lng(), scrapedLat, scrapedLng, "written"));
        }

        if (written > 0) {
            syncMetadataWritePort.updateLastModified(RESOURCE_SALAS, now);
            syncMetadataWritePort.updateLastModified(RESOURCE_CONCERTS, now);
        }

        return new FillFromScraperResult(dryRun, limit, targets.size(), written, wouldWrite,
            keptManual, keptNoChange, noCoords, items);
    }

    /**
     * Re-fetches the sala's page and re-parses it for a validated coordinate pair.
     * On any fetch failure the sala is treated as {@code no-coords} (untouched) and
     * the caller continues with the next sala — matches how the sync pipeline tolerates
     * per-venue fetch errors (ADR-11).
     */
    private Optional<ScrapedVenue> fetchAndParse(SalaConcierto sala) {
        try {
            String html = htmlFetchPort.fetch(sala.sourceUrl());
            return venueDetailParser.parse(html, sala.province(), sala.id(), sala.sourceUrl(), new ArrayList<>());
        } catch (HtmlFetchException ex) {
            log.warn("fillSalaCoordsFromScraper: fetch failed for sala_id={} sourceUrl={}: {}",
                sala.id(), sala.sourceUrl(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Compares a (possibly null) current coordinate against a freshly scraped one within
     * {@link #COORDINATE_EPSILON}. A null current value never equals a non-null scraped
     * value (ADR-7: NULL coords are always written when a valid pin is found).
     */
    private boolean coordinatesEqual(Double current, double scraped) {
        return current != null && Math.abs(current - scraped) < COORDINATE_EPSILON;
    }

    /**
     * Target selection per ADR-6: all conciertos.club salas (blocked included),
     * truncated to {@code limit}. The {@code manual-} sourceUrl prefix and alcala
     * salas are excluded by the {@code https://conciertos.club} prefix filter.
     */
    private List<SalaConcierto> findScraperBackfillTargets(int limit) {
        return salaRepository.findAllIncludingBlocked().stream()
            .filter(sala -> sala.sourceUrl() != null && sala.sourceUrl().startsWith(CONCIERTOS_CLUB_BASE_URL))
            .limit(limit)
            .toList();
    }

    private List<SalaConcierto> findFillMissingTargetSalas(int limit) {
        List<SalaConcierto> allSalas = salaRepository.findAllIncludingBlocked();
        Map<String, SalaConcierto> salasById = new LinkedHashMap<>();
        for (SalaConcierto sala : allSalas) {
            salasById.put(sala.id(), sala);
        }

        Map<String, SalaConcierto> targets = new LinkedHashMap<>();
        List<DataQuality> adminVisibleMissingRows = repository.findByStatus("missing");
        if (adminVisibleMissingRows != null) {
            for (DataQuality row : adminVisibleMissingRows) {
                if (!"sala".equals(row.entityType()) || !FOURSQUARE_FILLABLE_SALA_FIELDS.contains(row.field())) {
                    continue;
                }
                SalaConcierto sala = salasById.get(row.entityId());
                if (sala != null && hasMissingFoursquareFillableField(sala)) {
                    targets.putIfAbsent(sala.id(), sala);
                }
                if (targets.size() >= limit) {
                    return new ArrayList<>(targets.values());
                }
            }
        }

        for (SalaConcierto sala : allSalas) {
            if (hasMissingFoursquareFillableField(sala)) {
                targets.putIfAbsent(sala.id(), sala);
            }
            if (targets.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(targets.values());
    }

    private boolean hasMissingFoursquareFillableField(SalaConcierto sala) {
        return sala.lat() == null || sala.lng() == null || isBlank(sala.address());
    }

    private boolean shouldWriteCandidateAddress(SalaConcierto sala, VenueLookupCandidate match) {
        return match != null && isBlank(sala.address()) && !isBlank(match.formattedAddress());
    }

    private String addressDecisionForNonWrite(SalaConcierto sala, VenueLookupCandidate match) {
        if (match == null) {
            return "no_match";
        }
        if (isBlank(match.formattedAddress())) {
            return "not_returned";
        }
        if (!isBlank(sala.address())) {
            return "preserved_existing";
        }
        return "needs_review";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Returns true only when both coordinates are finite numbers and within valid geographic ranges.
     * Guards all provider write sites so non-finite or out-of-range values from external APIs are
     * never persisted to the database.
     */
    private static boolean isWritableCoordinate(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
            && lat >= -90.0 && lat <= 90.0
            && lng >= -180.0 && lng <= 180.0;
    }

    private void bumpConcertSyncIfSevere(DataQuality dq, Instant now) {
        if ("severe".equals(dq.severity()) &&
            ("concert".equals(dq.entityType()) || "sala".equals(dq.entityType()) || "artist".equals(dq.entityType()))) {
            syncMetadataWritePort.updateLastModified(RESOURCE_CONCERTS, now);
        }
    }

    @Override
    public List<DataQuality> listIssues(String status) {
        return listIssues(status, null);
    }

    @Override
    public List<DataQuality> listIssues(String status, Double minScore) {
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
        List<DataQuality> rows;
        if (status == null) {
            rows = VALID_STATUSES.stream()
                .flatMap(s -> repository.findByStatus(s).stream())
                .toList();
        } else {
            rows = repository.findByStatus(status);
        }
        if (minScore != null) {
            double threshold = minScore;
            rows = rows.stream()
                .filter(dq -> dq.score() != null && dq.score() >= threshold)
                .toList();
        }
        return rows;
    }

    @Override
    public void approve(Long id) {
        DataQuality dq = findOrThrow(id);
        guardNotTerminal(dq);

        Instant now = Instant.now(clock);
        applyResolution(dq, dq.suggested(), now);
        writer.updateStatus(id, "approved", now);
        geocodeSalaAddressIfNeeded(dq, now);
        bumpConcertSyncIfSevere(dq, now);
    }

    @Override
    public void reject(Long id) {
        DataQuality dq = findOrThrow(id);
        guardNotTerminal(dq);

        Instant now = Instant.now(clock);
        writer.updateStatus(id, "rejected", now);
        bumpConcertSyncIfSevere(dq, now);
    }

    @Override
    public void fill(Long id, String value) {
        DataQuality dq = findOrThrow(id);
        guardNotTerminal(dq);
        if ("concert".equals(dq.entityType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Concert fields are not manually fillable");
        }
        if ("name".equals(dq.field())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "name is not manually fillable (dedup key)");
        }
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value must not be blank");
        }
        validateCoordinateIfNeeded(dq.field(), value);
        Instant now = Instant.now(clock);
        applyResolution(dq, value, now);
        writer.updateStatus(id, "approved", now);
        geocodeSalaAddressIfNeeded(dq, now);
        bumpConcertSyncIfSevere(dq, now);
    }

    private void validateCoordinateIfNeeded(String field, String value) {
        if (!"lat".equals(field) && !"lng".equals(field)) {
            return;
        }
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat/lng must be numeric");
        }
        if ("lat".equals(field) && (parsed < -90.0 || parsed > 90.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat must be between -90 and 90");
        }
        if ("lng".equals(field) && (parsed < -180.0 || parsed > 180.0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lng must be between -180 and 180");
        }
    }

    @Override
    public int approveAll(double minScore, String severityFilter) {
        List<DataQuality> candidates = repository.findByStatusAndScore("auto_found", minScore, severityFilter);
        Instant now = Instant.now(clock);
        int count = 0;
        for (DataQuality dq : candidates) {
            applyResolution(dq, dq.suggested(), now);
            writer.updateStatus(dq.id(), "approved", now);
            geocodeSalaAddressIfNeeded(dq, now);
            bumpConcertSyncIfSevere(dq, now);
            count++;
        }
        return count;
    }

    private DataQuality findOrThrow(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Data quality issue not found: " + id));
    }

    private void guardNotTerminal(DataQuality dq) {
        if ("approved".equals(dq.status()) || "rejected".equals(dq.status()) || "auto_approved".equals(dq.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cannot transition from status '" + dq.status() + "'");
        }
    }
}
