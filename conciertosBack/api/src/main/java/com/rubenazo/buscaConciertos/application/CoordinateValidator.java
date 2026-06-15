package com.rubenazo.buscaConciertos.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates geographic coordinate pairs for manually supplied lat/lng values.
 * Enforces both-or-neither rule and valid ranges (lat ∈ [-90,90], lng ∈ [-180,180]).
 */
final class CoordinateValidator {

    private CoordinateValidator() {}

    /**
     * Validates lat and lng.
     * Both null → OK (geocoding will be attempted).
     * One present, one null → 400 (both-or-neither).
     * Both present but out of range → 400.
     */
    static void validate(Double lat, Double lng) {
        boolean latPresent = lat != null;
        boolean lngPresent = lng != null;

        if (latPresent != lngPresent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "lat and lng must both be provided or both be absent");
        }

        if (!latPresent) {
            return;
        }

        if (lat < -90.0 || lat > 90.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "lat must be between -90 and 90, got: " + lat);
        }
        if (lng < -180.0 || lng > 180.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "lng must be between -180 and 180, got: " + lng);
        }
    }
}
