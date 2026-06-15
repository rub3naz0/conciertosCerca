package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rubenazo.buscaConciertos.application.TextSimilarity;
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupCandidate;
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupPort;
import com.rubenazo.buscaConciertos.application.ports.out.VenueMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class FoursquarePlacesAdapter implements VenueLookupPort {

    private static final Logger log = LoggerFactory.getLogger(FoursquarePlacesAdapter.class);

    /**
     * Soft-boost allow-set for music/venue-ish Foursquare category names.
     * Matched via substring after normalize(). Small and explicit so it is easy to tune.
     */
    private static final Set<String> VENUE_CATEGORY_HINTS = Set.of(
        "music venue", "concert hall", "rock club", "nightclub", "night club",
        "bar", "pub", "theater", "theatre", "performing arts venue",
        "amphitheater", "stadium", "arena", "auditorium", "live music"
    );

    private final RestClient restClient;

    FoursquarePlacesAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<VenueMatch> lookup(String name, String city, String province) {
        return lookupCandidates(name, city, province, 1).stream()
            .findFirst()
            .map(VenueLookupCandidate::toVenueMatch);
    }

    @Override
    public List<VenueLookupCandidate> lookupCandidates(String name, String city, String province, int limit) {
        try {
            int safeLimit = Math.max(1, Math.min(limit, 10));
            // Build "near" from non-blank parts only — a null/blank city or province must not
            // leak the literal string "null" into the Foursquare location query (which degrades
            // the match). Mirrors LocationIqGeocodingAdapter.buildQuery.
            List<String> nearParts = new ArrayList<>();
            if (city != null && !city.isBlank()) nearParts.add(city.strip());
            if (province != null && !province.isBlank()) nearParts.add(province.strip());
            nearParts.add("España");
            String near = String.join(", ", nearParts);
            FsqSearchResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/places/search")
                    .queryParam("query", name)
                    .queryParam("near", near)
                    .queryParam("limit", safeLimit)
                    .build())
                .retrieve()
                .body(FsqSearchResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return List.of();
            }

            return response.results().stream()
                .map(place -> toVenueLookupCandidate(name, place))
                .flatMap(Optional::stream)
                .toList();

        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 422 || status == 429) {
                log.warn("Foursquare lookup returned {} for name='{}', city='{}': {}",
                    status, name, city, ex.getMessage());
            } else {
                log.warn("Foursquare lookup failed with HTTP {} for name='{}', city='{}': {}",
                    status, name, city, ex.getMessage());
            }
            return List.of();
        } catch (Exception ex) {
            log.warn("Foursquare lookup threw unexpected error for name='{}', city='{}': {}",
                name, city, ex.getMessage());
            return List.of();
        }
    }

    private Optional<VenueLookupCandidate> toVenueLookupCandidate(String requestedName, FsqPlace place) {
        if (place.latitude() == null || place.longitude() == null) {
            return Optional.empty();
        }

        double lat = place.latitude();
        double lng = place.longitude();
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            return Optional.empty();
        }

        String displayName = place.name() != null ? place.name()
            : (place.location() != null ? place.location().formattedAddress() : null);

        double nameSim = TextSimilarity.nameSimilarity(requestedName, place.name() != null ? place.name() : "");
        double catMatch = categoryMatch(place.categories());
        double confidence = clamp(0.6 * nameSim + 0.4 * catMatch, 0.0, 1.0);

        String formattedAddress = place.location() != null ? place.location().formattedAddress() : null;
        return Optional.of(new VenueLookupCandidate(
            lat,
            lng,
            displayName,
            formattedAddress,
            confidence,
            "name",
            "foursquare"
        ));
    }

    private double categoryMatch(List<FsqCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return 0.5;
        }
        for (FsqCategory cat : categories) {
            if (cat.name() == null) continue;
            String normalized = TextSimilarity.normalize(cat.name());
            for (String hint : VENUE_CATEGORY_HINTS) {
                if (normalized.contains(hint)) {
                    return 1.0;
                }
            }
        }
        return 0.5;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // --- Inner Jackson response records (pinned to new Places API shape) ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FsqSearchResponse(List<FsqPlace> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FsqPlace(
        String name,
        Double latitude,
        Double longitude,
        FsqLocation location,
        List<FsqCategory> categories
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FsqLocation(@JsonProperty("formatted_address") String formattedAddress) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FsqCategory(Long id, String name) {}
}
