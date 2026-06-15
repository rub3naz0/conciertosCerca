package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.AlcalaSyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.AdminArea;
import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSnapshot;
import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSourcePort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ReverseGeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.FieldSeverity;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.ExistingDataReaderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ingests concerts from the secondary source (the alcalaesmusica JSON API) and merges them with the
 * primary scraped data, mirroring {@link SyncUseCase}'s transactional, FK-safe upsert flow.
 *
 * The extra concern here is deduplication across sources: {@link CrossSourceMatcher} decides whether
 * an Alcalá venue/concert already exists from the primary source before inserting, so the two feeds
 * don't create duplicates. Venue coordinates come from {@link ReverseGeocodingPort} (lat/lng → area)
 * since this source provides points rather than addresses.
 */
@Service
public class AlcalaSyncUseCase implements AlcalaSyncInputPort {

    private static final Logger log = LoggerFactory.getLogger(AlcalaSyncUseCase.class);

    private final AlcalaSourcePort alcalaSourcePort;
    private final ReverseGeocodingPort reverseGeocodingPort;
    private final CrossSourceMatcher crossSourceMatcher;
    private final SalaConciertoWritePort salaWriter;
    private final ArtistWritePort artistWriter;
    private final ConcertWritePort concertWriter;
    private final SyncMetadataWritePort syncMetaWriter;
    private final SyncRunPort syncRunPort;
    private final DataQualityWritePort qualityWriter;
    private final ExistingDataReaderPort existingDataReaderPort;
    private final SalaConciertoRepositoryPort salaRepositoryPort;
    private final ConcertRepositoryPort concertRepositoryPort;
    private final TransactionTemplate txTemplate;
    private final Clock clock;

    public AlcalaSyncUseCase(
        AlcalaSourcePort alcalaSourcePort,
        ReverseGeocodingPort reverseGeocodingPort,
        CrossSourceMatcher crossSourceMatcher,
        SalaConciertoWritePort salaWriter,
        ArtistWritePort artistWriter,
        ConcertWritePort concertWriter,
        SyncMetadataWritePort syncMetaWriter,
        SyncRunPort syncRunPort,
        DataQualityWritePort qualityWriter,
        ExistingDataReaderPort existingDataReaderPort,
        SalaConciertoRepositoryPort salaRepositoryPort,
        ConcertRepositoryPort concertRepositoryPort,
        TransactionTemplate txTemplate,
        Clock clock
    ) {
        this.alcalaSourcePort = alcalaSourcePort;
        this.reverseGeocodingPort = reverseGeocodingPort;
        this.crossSourceMatcher = crossSourceMatcher;
        this.salaWriter = salaWriter;
        this.artistWriter = artistWriter;
        this.concertWriter = concertWriter;
        this.syncMetaWriter = syncMetaWriter;
        this.syncRunPort = syncRunPort;
        this.qualityWriter = qualityWriter;
        this.existingDataReaderPort = existingDataReaderPort;
        this.salaRepositoryPort = salaRepositoryPort;
        this.concertRepositoryPort = concertRepositoryPort;
        this.txTemplate = txTemplate;
        this.clock = clock;
    }

    @Override
    @Async
    public void execute(String runId) {
        execute(runId, null, null);
    }

    @Override
    @Async
    public void execute(String runId, LocalDate from, LocalDate to) {
        try {
            doExecute(runId, from, to);
        } catch (Exception e) {
            log.error("Alcala sync run {} failed: {}", runId, e.getMessage(), e);
            try {
                syncRunPort.fail(runId, e.getMessage());
            } catch (Exception failEx) {
                // Marking the run failed itself failed (e.g. DB locked). Since execute() is @Async,
                // an escaping exception here would be swallowed by Spring's async handler and the run
                // would silently stay 'running', blocking every future sync until startup cleanup.
                // Log loudly and swallow so the failure is at least diagnosable.
                log.error("Alcala sync run {} could not be marked failed; run may remain 'running' "
                    + "until startup cleanup clears orphaned runs: {}", runId, failEx.getMessage(), failEx);
            }
        }
    }

    private void doExecute(String runId, LocalDate from, LocalDate to) {
        Instant now = Instant.now(clock);
        AlcalaSnapshot snapshot = alcalaSourcePort.fetch(from, to);

        List<Concert> windowedAemConcerts = safeList(snapshot.concerts()).stream()
            .filter(concert -> insideWindow(concert.date(), from, to))
            .toList();

        Set<String> referencedVenueIds = windowedAemConcerts.stream()
            .map(Concert::salaConciertoId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toCollection(HashSet::new));
        Set<String> referencedArtistIds = windowedAemConcerts.stream()
            .flatMap(concert -> safeList(concert.artistIds()).stream())
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toCollection(HashSet::new));

        List<SalaConcierto> aemVenues = dedupeById(safeList(snapshot.venues()), SalaConcierto::id).stream()
            .filter(venue -> referencedVenueIds.contains(venue.id()))
            .toList();
        List<Artist> aemArtists = dedupeById(safeList(snapshot.artists()), Artist::id).stream()
            .filter(artist -> referencedArtistIds.contains(artist.id()))
            .toList();

        Set<String> existingVenueIds = safeSet(existingDataReaderPort.existingVenueIds());
        Set<String> existingArtistIds = safeSet(existingDataReaderPort.existingArtistIds());
        Set<String> existingConcertIds = safeSet(existingDataReaderPort.existingConcertIds());
        // Full-table loads are intentional: CrossSourceMatcher compares each AEM venue against ALL
        // existing venues (geographic 150m match), so there is no cheaper pre-filter. O(n*m) is
        // negligible at current volume (~tens of venues, ~hundreds of concerts). Revisit with a
        // spatial index only if the catalog grows by orders of magnitude.
        List<SalaConcierto> existingVenues = safeList(salaRepositoryPort.findAll());
        List<Concert> existingConcerts = safeList(concertRepositoryPort.findAll());

        CrossSourceMatcher.VenueResolution venueResolution = crossSourceMatcher.resolveVenues(aemVenues, existingVenues);
        CrossSourceMatcher.ArtistResolution artistResolution = crossSourceMatcher.resolveArtists(aemArtists, existingArtistIds);
        CrossSourceMatcher.ConcertResolution concertResolution = crossSourceMatcher.resolveConcerts(
            windowedAemConcerts,
            venueResolution.resolvedIds(),
            existingConcerts
        );

        Map<String, SalaConcierto> existingVenueById = existingVenues.stream()
            .filter(v -> v.id() != null)
            .collect(Collectors.toMap(SalaConcierto::id, v -> v, (a, b) -> a));

        List<DataQuality> provinceIssues = new ArrayList<>();
        List<SalaConcierto> venuesToWrite = resolveVenuesToWrite(aemVenues, venueResolution, existingVenueIds, existingVenueById, provinceIssues, now);
        List<Artist> artistsToWrite = resolveArtistsToWrite(aemArtists, artistResolution, existingArtistIds, now);
        List<Concert> concertsToWrite = resolveConcertsToWrite(windowedAemConcerts, concertResolution, venueResolution, artistResolution, existingConcertIds, now);

        txTemplate.executeWithoutResult(status -> {
            for (SalaConcierto venue : venuesToWrite) {
                salaWriter.upsert(venue);
            }
            for (Artist artist : artistsToWrite) {
                artistWriter.upsert(artist);
            }
            for (Concert concert : concertsToWrite) {
                concertWriter.upsert(concert);
            }

            if (!provinceIssues.isEmpty()) {
                qualityWriter.saveAll(provinceIssues);
            }

            if (!venuesToWrite.isEmpty()) {
                syncMetaWriter.updateLastModified("salas-concierto", now);
            }
            if (!artistsToWrite.isEmpty()) {
                syncMetaWriter.updateLastModified("artists", now);
            }
            if (!concertsToWrite.isEmpty()) {
                syncMetaWriter.updateLastModified("concerts", now);
            }

            syncRunPort.complete(runId, venuesToWrite.size(), artistsToWrite.size(), concertsToWrite.size(), 0, 0);
        });

        log.info("[{}] Alcala sync completed — salas={} artists={} concerts={}",
            runId, venuesToWrite.size(), artistsToWrite.size(), concertsToWrite.size());
    }

    private List<SalaConcierto> resolveVenuesToWrite(
        List<SalaConcierto> aemVenues,
        CrossSourceMatcher.VenueResolution venueResolution,
        Set<String> existingVenueIds,
        Map<String, SalaConcierto> existingVenueById,
        List<DataQuality> provinceIssues,
        Instant now
    ) {
        List<SalaConcierto> venues = new ArrayList<>();
        for (SalaConcierto aemVenue : aemVenues) {
            String sourceId = aemVenue.id();
            String resolvedId = venueResolution.resolvedIdFor(sourceId);
            boolean matched = venueResolution.matched(sourceId);
            if (!matched && existingVenueIds.contains(resolvedId)) {
                continue;
            }

            String city;
            String province;
            if (matched) {
                // AEM does not provide city/province — preserve the existing row's values.
                SalaConcierto existing = existingVenueById.get(resolvedId);
                city = (existing != null && existing.city() != null && !existing.city().isBlank())
                    ? existing.city() : aemVenue.city();
                province = (existing != null && existing.province() != null && !existing.province().isBlank())
                    ? existing.province() : aemVenue.province();
            } else {
                city = aemVenue.city();
                province = aemVenue.province();
                if (aemVenue.lat() != null && aemVenue.lng() != null) {
                    Optional<AdminArea> adminArea = reverseGeocodingPort.reverse(aemVenue.lat(), aemVenue.lng());
                    city = adminArea.map(AdminArea::city).orElse(city);
                    province = adminArea.map(AdminArea::province).orElse(province);
                }
                AdminArea addressFallback = adminAreaFromAddress(aemVenue.address());
                city = firstNonBlank(city, addressFallback.city());
                province = firstNonBlank(province, addressFallback.province());
            }

            SalaConcierto venue = new SalaConcierto(
                resolvedId,
                aemVenue.name(),
                aemVenue.address(),
                city,
                province,
                aemVenue.lat(),
                aemVenue.lng(),
                aemVenue.imageUrl(),
                aemVenue.description(),
                aemVenue.sourceUrl(),
                now
            );
            venues.add(venue);

            if (!matched && province == null) {
                provinceIssues.add(new DataQuality(
                    null,
                    "sala",
                    resolvedId,
                    "province",
                    "missing",
                    FieldSeverity.of("sala", "province").toDbValue(),
                    null,
                    null,
                    null,
                    now
                ));
            }
        }
        return venues;
    }

    private List<Artist> resolveArtistsToWrite(
        List<Artist> aemArtists,
        CrossSourceMatcher.ArtistResolution artistResolution,
        Set<String> existingArtistIds,
        Instant now
    ) {
        List<Artist> artists = new ArrayList<>();
        for (Artist aemArtist : aemArtists) {
            String sourceId = aemArtist.id();
            String resolvedId = artistResolution.resolvedIdFor(sourceId);
            boolean matched = artistResolution.matched(sourceId);
            if (!matched && existingArtistIds.contains(resolvedId)) {
                continue;
            }
            artists.add(new Artist(
                resolvedId,
                aemArtist.name(),
                aemArtist.genre(),
                aemArtist.imageUrl(),
                aemArtist.website(),
                aemArtist.sourceUrl(),
                aemArtist.description(),
                now
            ));
        }
        return artists;
    }

    private List<Concert> resolveConcertsToWrite(
        List<Concert> aemConcerts,
        CrossSourceMatcher.ConcertResolution concertResolution,
        CrossSourceMatcher.VenueResolution venueResolution,
        CrossSourceMatcher.ArtistResolution artistResolution,
        Set<String> existingConcertIds,
        Instant now
    ) {
        List<Concert> concerts = new ArrayList<>();
        for (Concert aemConcert : aemConcerts) {
            String sourceId = aemConcert.id();
            String resolvedId = concertResolution.resolvedIdFor(sourceId);
            boolean matched = concertResolution.matched(sourceId);
            if (!matched && existingConcertIds.contains(resolvedId)) {
                continue;
            }

            if (aemConcert.date() == null) {
                continue;
            }
            String resolvedVenueId = venueResolution.resolvedIdFor(aemConcert.salaConciertoId());
            if (resolvedVenueId == null || resolvedVenueId.isBlank()) {
                continue;
            }
            List<String> resolvedArtistIds = safeList(aemConcert.artistIds()).stream()
                .map(artistResolution::resolvedIdFor)
                .toList();

            concerts.add(new Concert(
                resolvedId,
                resolvedVenueId,
                resolvedArtistIds,
                aemConcert.date(),
                aemConcert.time(),
                aemConcert.price(),
                aemConcert.sourceUrl(),
                now
            ));
        }
        return concerts;
    }

    private AdminArea adminAreaFromAddress(String address) {
        if (address == null || address.isBlank()) {
            return new AdminArea(null, null);
        }
        String[] rawParts = address.split(",");
        List<String> parts = new ArrayList<>();
        for (String rawPart : rawParts) {
            String part = rawPart.trim();
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return new AdminArea(null, null);
        }
        String last = stripPostalCode(parts.getLast());
        String previous = parts.size() >= 2 ? stripPostalCode(parts.get(parts.size() - 2)) : null;

        if (isKnownProvince(last)) {
            String city = isAddressLike(previous) ? null : previous;
            return new AdminArea(blankToNull(city), last);
        }

        String inferredProvince = provinceForKnownCity(last);
        if (inferredProvince != null && parts.size() >= 2) {
            return new AdminArea(last, inferredProvince);
        }

        if (parts.size() >= 2 && !isAddressLike(last)) {
            return new AdminArea(blankToNull(last), null);
        }

        return new AdminArea(null, null);
    }

    private boolean isKnownProvince(String value) {
        return "Madrid".equalsIgnoreCase(value) || "Guadalajara".equalsIgnoreCase(value);
    }

    private String provinceForKnownCity(String value) {
        if (value == null) return null;
        if ("Alcalá de Henares".equalsIgnoreCase(value)) return "Madrid";
        if ("Mejorada del Campo".equalsIgnoreCase(value)) return "Madrid";
        if ("Guadalajara".equalsIgnoreCase(value)) return "Guadalajara";
        return null;
    }

    private boolean isAddressLike(String value) {
        if (value == null || value.isBlank()) return true;
        String normalized = value.trim().toLowerCase();
        return normalized.matches(".*\\d.*")
            || normalized.startsWith("c/")
            || normalized.startsWith("calle ")
            || normalized.startsWith("avda")
            || normalized.startsWith("avenida ")
            || normalized.startsWith("plaza ")
            || normalized.startsWith("parque ");
    }

    private String stripPostalCode(String value) {
        if (value == null) return null;
        return value.replaceFirst("^\\d{5}\\s+", "").trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private boolean insideWindow(LocalDate date, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (date == null) {
            return false;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private <T> List<T> dedupeById(List<T> values, Function<T, String> idExtractor) {
        Map<String, T> deduped = new LinkedHashMap<>();
        for (T value : values) {
            String id = idExtractor.apply(value);
            if (id != null && !id.isBlank()) {
                deduped.putIfAbsent(id, value);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private <T> Set<T> safeSet(Set<T> set) {
        return set == null ? Set.of() : set;
    }
}
