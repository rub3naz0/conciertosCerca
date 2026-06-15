package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.scraper.application.SlugUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class CrossSourceMatcher {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double VENUE_MATCH_RADIUS_METERS = 150.0;

    public VenueResolution resolveVenues(List<SalaConcierto> aemVenues, List<SalaConcierto> existingVenues) {
        Map<String, EntityResolution> assignments = new LinkedHashMap<>();
        for (SalaConcierto aemVenue : safeList(aemVenues)) {
            String sourceId = additiveId(aemVenue.id(), "aem-venue-" + SlugUtils.slugify(aemVenue.name()));
            SalaConcierto matched = uniqueCoordinateMatch(aemVenue, safeList(existingVenues));
            if (matched == null) {
                matched = uniqueSlugFallbackMatch(aemVenue, safeList(existingVenues));
            }
            assignments.put(sourceId, new EntityResolution(
                sourceId,
                matched != null ? matched.id() : sourceId,
                matched != null
            ));
        }
        return new VenueResolution(assignments);
    }

    public ArtistResolution resolveArtists(List<Artist> aemArtists, Set<String> existingArtistIds) {
        Set<String> safeExistingIds = existingArtistIds == null ? Set.of() : existingArtistIds;
        Map<String, EntityResolution> assignments = new LinkedHashMap<>();
        for (Artist aemArtist : safeList(aemArtists)) {
            String sourceId = additiveId(aemArtist.id(), "aem-band-" + SlugUtils.slugify(aemArtist.name()));
            String slug = SlugUtils.slugify(aemArtist.name());
            boolean matched = !slug.isBlank() && safeExistingIds.contains(slug);
            assignments.put(sourceId, new EntityResolution(sourceId, matched ? slug : sourceId, matched));
        }
        return new ArtistResolution(assignments);
    }

    public ConcertResolution resolveConcerts(
        List<Concert> aemConcerts,
        Map<String, String> resolvedVenueIds,
        List<Concert> existingConcerts
    ) {
        Map<String, String> safeResolvedVenueIds = resolvedVenueIds == null ? Map.of() : resolvedVenueIds;
        List<Concert> safeExistingConcerts = safeList(existingConcerts);
        Map<String, EntityResolution> assignments = new LinkedHashMap<>();

        for (Concert aemConcert : safeList(aemConcerts)) {
            String sourceId = additiveId(aemConcert.id(), "aem-event-" + Objects.toString(aemConcert.date(), "unknown"));
            String sourceVenueId = aemConcert.salaConciertoId();
            String resolvedVenueId = sourceVenueId == null
                ? null
                : safeResolvedVenueIds.getOrDefault(sourceVenueId, sourceVenueId);
            List<Concert> matches = resolvedVenueId == null
                ? List.of()
                : safeExistingConcerts.stream()
                    .filter(existing -> Objects.equals(existing.salaConciertoId(), resolvedVenueId))
                    .filter(existing -> Objects.equals(existing.date(), aemConcert.date()))
                    .toList();

            boolean matched = matches.size() == 1;
            assignments.put(sourceId, new EntityResolution(
                sourceId,
                matched ? matches.getFirst().id() : sourceId,
                matched
            ));
        }
        return new ConcertResolution(assignments);
    }

    private SalaConcierto uniqueCoordinateMatch(SalaConcierto aemVenue, List<SalaConcierto> existingVenues) {
        if (aemVenue.lat() == null || aemVenue.lng() == null) {
            return null;
        }
        List<SalaConcierto> matches = existingVenues.stream()
            .filter(existing -> existing.lat() != null && existing.lng() != null)
            .filter(existing -> distanceMeters(aemVenue.lat(), aemVenue.lng(), existing.lat(), existing.lng()) <= VENUE_MATCH_RADIUS_METERS)
            .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private SalaConcierto uniqueSlugFallbackMatch(SalaConcierto aemVenue, List<SalaConcierto> existingVenues) {
        String aemSlug = SlugUtils.slugify(aemVenue.name());
        if (aemSlug.isBlank()) {
            return null;
        }
        List<SalaConcierto> matches = existingVenues.stream()
            .filter(existing -> existing.lat() == null || existing.lng() == null)
            .filter(existing -> aemSlug.equals(SlugUtils.slugify(existing.name())) || existingIdNamePartMatches(existing.id(), aemSlug))
            .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private boolean existingIdNamePartMatches(String existingId, String aemSlug) {
        if (existingId == null || existingId.isBlank()) {
            return false;
        }
        return existingId.equals(aemSlug) || existingId.endsWith("-" + aemSlug);
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double meanLat = Math.toRadians((lat1 + lat2) / 2.0);
        double x = dLng * Math.cos(meanLat);
        double y = dLat;
        return EARTH_RADIUS_METERS * Math.sqrt(x * x + y * y);
    }

    private String additiveId(String id, String fallback) {
        if (id != null && !id.isBlank()) {
            return id;
        }
        return fallback;
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    public record EntityResolution(String sourceId, String resolvedId, boolean matched) {}

    public record VenueResolution(Map<String, EntityResolution> assignments) {
        public VenueResolution {
            assignments = Map.copyOf(assignments);
        }

        public String resolvedIdFor(String sourceId) {
            if (sourceId == null) return null;
            EntityResolution resolution = assignments.get(sourceId);
            return resolution != null ? resolution.resolvedId() : sourceId;
        }

        public boolean matched(String sourceId) {
            if (sourceId == null) return false;
            EntityResolution resolution = assignments.get(sourceId);
            return resolution != null && resolution.matched();
        }

        public Map<String, String> resolvedIds() {
            return assignments.values().stream()
                .collect(Collectors.toUnmodifiableMap(EntityResolution::sourceId, EntityResolution::resolvedId));
        }
    }

    public record ArtistResolution(Map<String, EntityResolution> assignments) {
        public ArtistResolution {
            assignments = Map.copyOf(assignments);
        }

        public String resolvedIdFor(String sourceId) {
            if (sourceId == null) return null;
            EntityResolution resolution = assignments.get(sourceId);
            return resolution != null ? resolution.resolvedId() : sourceId;
        }

        public boolean matched(String sourceId) {
            if (sourceId == null) return false;
            EntityResolution resolution = assignments.get(sourceId);
            return resolution != null && resolution.matched();
        }
    }

    public record ConcertResolution(Map<String, EntityResolution> assignments) {
        public ConcertResolution {
            assignments = Map.copyOf(assignments);
        }

        public String resolvedIdFor(String sourceId) {
            if (sourceId == null) return null;
            EntityResolution resolution = assignments.get(sourceId);
            return resolution != null ? resolution.resolvedId() : sourceId;
        }

        public boolean matched(String sourceId) {
            if (sourceId == null) return false;
            EntityResolution resolution = assignments.get(sourceId);
            return resolution != null && resolution.matched();
        }
    }
}
