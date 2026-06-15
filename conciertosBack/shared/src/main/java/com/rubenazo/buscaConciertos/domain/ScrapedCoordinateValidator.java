package com.rubenazo.buscaConciertos.domain;

/**
 * Validates a (lat, lng) pair extracted from a scraped venue page (e.g. a Google Maps
 * iframe {@code q=lat,lng} parameter) before it is accepted into a {@code ScrapedVenue}.
 *
 * <p>Pure, dependency-free, no Spring: this lives in {@code shared} so both the
 * {@code scraper} module (parser-side validation) and the {@code api} module
 * (backfill re-validation) can use it without crossing the {@code api -> scraper -> shared}
 * dependency direction.
 *
 * <p>A pair is valid only if both values are finite, within the global lat/lng range, AND
 * within a single conservative Spain bounding box (mainland, Balearics, Canary Islands,
 * Ceuta/Melilla). The box intentionally over-approximates (e.g. it also covers Portugal and
 * a strip of southern France) — it is a coarse sanity guard against grossly wrong parses
 * (foreign embeds, swapped lat/lng, decorative widget placeholders), not a precise
 * geofence.
 */
public final class ScrapedCoordinateValidator {

    // Global geographic range.
    private static final double GLOBAL_LAT_MIN = -90.0;
    private static final double GLOBAL_LAT_MAX = 90.0;
    private static final double GLOBAL_LNG_MIN = -180.0;
    private static final double GLOBAL_LNG_MAX = 180.0;

    // Single conservative Spain bounding box.
    // lat: Canarias south edge (~27.6) up to mainland north (~43.8) -> [27.0, 44.0]
    // lng: El Hierro westmost (~-18.2) to Menorca/Catalonia east (~4.3) -> [-18.5, 4.5]
    // Locked per design ADR-2: a single box, not a multi-polygon. Includes Canarias,
    // Baleares, Ceuta and Melilla. Also accepts Portugal and a sliver of southern France
    // as a documented over-approximation (no conciertos.club venue is located there).
    private static final double SPAIN_LAT_MIN = 27.0;
    private static final double SPAIN_LAT_MAX = 44.0;
    private static final double SPAIN_LNG_MIN = -18.5;
    private static final double SPAIN_LNG_MAX = 4.5;

    private ScrapedCoordinateValidator() {
    }

    /**
     * Returns {@code true} only if both values are finite numbers, within the global
     * lat/lng range, AND within the Spain bounding box.
     */
    public static boolean isValid(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
            return false;
        }
        if (lat < GLOBAL_LAT_MIN || lat > GLOBAL_LAT_MAX
            || lng < GLOBAL_LNG_MIN || lng > GLOBAL_LNG_MAX) {
            return false;
        }
        return lat >= SPAIN_LAT_MIN && lat <= SPAIN_LAT_MAX
            && lng >= SPAIN_LNG_MIN && lng <= SPAIN_LNG_MAX;
    }
}
