package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rubenazo.buscaConciertos.application.ports.out.GeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.ValidatedGeocodingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

class LocationIqGeocodingAdapter implements ValidatedGeocodingPort {

    private static final Logger log = LoggerFactory.getLogger(LocationIqGeocodingAdapter.class);

    private final RestClient restClient;
    private final String apiKey;
    private final int limit;
    private final String countryCodes;

    LocationIqGeocodingAdapter(RestClient restClient, String apiKey, int limit, String countryCodes) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.limit = limit;
        this.countryCodes = countryCodes;
    }

    @Override
    public Optional<Coordinates> geocode(String address, String city, String province) {
        return geocodeValidated(address, city, province)
            .map(v -> new Coordinates(v.lat(), v.lng()));
    }

    @Override
    public Optional<ValidatedCoordinates> geocodeValidated(String address, String city, String province) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        String query = buildQuery(address, city, province);
        try {
            LocationIqResult[] results = restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/v1/search")
                        .queryParam("key", apiKey)
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("limit", limit);
                    if (countryCodes != null && !countryCodes.isBlank()) {
                        builder.queryParam("countrycodes", countryCodes);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(LocationIqResult[].class);

            if (results == null || results.length == 0) {
                return Optional.empty();
            }
            // Return the first non-centroid result that parses to valid coordinates.
            // LocationIQ often ranks a city/administrative centroid first; giving up on
            // results[0] alone would discard a real venue match sitting at results[1+].
            for (LocationIqResult candidate : results) {
                if (isCityCentroid(candidate)) {
                    continue;
                }
                Optional<ValidatedCoordinates> parsed = parseValidated(candidate);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("LocationIQ geocoding failed for query='{}': {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildQuery(String address, String city, String province) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, address);
        addIfPresent(parts, city);
        addIfPresent(parts, province);
        parts.add("España");
        return String.join(", ", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.strip());
        }
    }

    private Optional<ValidatedCoordinates> parseValidated(LocationIqResult result) {
        if (result.lat() == null || result.lon() == null) {
            return Optional.empty();
        }
        try {
            double lat = Double.parseDouble(result.lat());
            double lng = Double.parseDouble(result.lon());
            if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                return Optional.empty();
            }
            double importance = parseImportance(result.importance());
            return Optional.of(new ValidatedCoordinates(lat, lng, importance));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private double parseImportance(String importanceStr) {
        if (importanceStr == null) return 0.0;
        try {
            double v = Double.parseDouble(importanceStr);
            if (Double.isNaN(v)) return 0.0;
            return Math.max(0.0, Math.min(1.0, v));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    /**
     * City-centroid rejection predicate.
     * Rejects results where class=boundary OR type contains "administrative" OR type contains "city".
     */
    private boolean isCityCentroid(LocationIqResult r) {
        String cls = r.clazz() != null ? r.clazz().toLowerCase(Locale.ROOT) : "";
        String type = r.type() != null ? r.type().toLowerCase(Locale.ROOT) : "";
        return cls.equals("boundary")
            || type.contains("administrative")
            || type.contains("city");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocationIqResult(
        String lat,
        String lon,
        String display_name,
        @JsonProperty("class") String clazz,
        String type,
        String importance
    ) {}
}
