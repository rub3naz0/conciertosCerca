package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rubenazo.buscaConciertos.application.ports.out.AdminArea;
import com.rubenazo.buscaConciertos.application.ports.out.ReverseGeocodingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Optional;

class LocationIqReverseAdapter implements ReverseGeocodingPort {

    private static final Logger log = LoggerFactory.getLogger(LocationIqReverseAdapter.class);

    private final RestClient restClient;
    private final String apiKey;

    LocationIqReverseAdapter(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public Optional<AdminArea> reverse(double lat, double lng) {
        try {
            ReverseResult result = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/v1/reverse")
                    .queryParam("key", apiKey)
                    .queryParam("lat", lat)
                    .queryParam("lon", lng)   // NOTE: param is "lon", not "lng" (D3)
                    .queryParam("format", "json")
                    .build())
                .retrieve()
                .body(ReverseResult.class);

            if (result == null || result.address() == null) {
                return Optional.empty();
            }

            String province = resolveProvince(result.address());
            String city = resolveCity(result.address());

            if (province == null && city == null) {
                return Optional.empty();
            }

            return Optional.of(new AdminArea(city, province));
        } catch (Exception ex) {
            log.warn("LocationIQ reverse geocoding failed for lat={}, lng={}: {}", lat, lng, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extract province using D3 fallback chain:
     * county → province → state_district
     * state is NOT used here to avoid autonomous-community ambiguity.
     */
    private String resolveProvince(ReverseResult.Address address) {
        if (isPresent(address.county())) return address.county();
        if (isPresent(address.province())) return address.province();
        if (isPresent(address.state_district())) return address.state_district();
        return null;
    }

    /**
     * Extract city using D3 fallback chain:
     * city → town → village → municipality
     */
    private String resolveCity(ReverseResult.Address address) {
        if (isPresent(address.city())) return address.city();
        if (isPresent(address.town())) return address.town();
        if (isPresent(address.village())) return address.village();
        if (isPresent(address.municipality())) return address.municipality();
        return null;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Pinned DTO per D3 design decision — lenient binding, Nominatim-style nested address.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReverseResult(Address address) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Address(
            String city,
            String town,
            String village,
            String municipality,
            String county,
            String province,
            String state_district,
            String state
        ) {}
    }
}
