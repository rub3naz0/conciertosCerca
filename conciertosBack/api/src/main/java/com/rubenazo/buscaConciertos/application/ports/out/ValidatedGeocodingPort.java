package com.rubenazo.buscaConciertos.application.ports.out;

import java.util.Optional;

/**
 * Extended geocoding port that surfaces importance/confidence alongside coordinates.
 * Implemented by LocationIqGeocodingAdapter to enable centroid-rejection and
 * importance-based confidence scoring in VenueGeocodingUseCase.
 * GeocodingPort itself remains UNCHANGED per ADR-1.
 */
public interface ValidatedGeocodingPort extends GeocodingPort {

    Optional<ValidatedCoordinates> geocodeValidated(String address, String city, String province);

    record ValidatedCoordinates(double lat, double lng, double importance) {}
}
