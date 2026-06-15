package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.GeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.ValidatedGeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupCandidate;
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupPort;
import com.rubenazo.buscaConciertos.application.ports.out.VenueMatch;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

@Service
public class VenueGeocodingUseCase {

    private static final Logger log = LoggerFactory.getLogger(VenueGeocodingUseCase.class);

    private final VenueLookupPort venueLookupPort;
    private final GeocodingPort geocodingPort;
    private final double autoAcceptThreshold;

    public VenueGeocodingUseCase(
        VenueLookupPort venueLookupPort,
        GeocodingPort geocodingPort,
        @Value("${app.foursquare.auto-accept-threshold:0.8}") double autoAcceptThreshold
    ) {
        this.venueLookupPort = venueLookupPort;
        this.geocodingPort = geocodingPort;
        this.autoAcceptThreshold = autoAcceptThreshold;
    }

    /**
     * Fallback chain: Foursquare by name (primary) → LocationIQ by address (validated fallback).
     * Returns the best available VenueMatch, or the sub-threshold primary if LocationIQ also fails,
     * so the caller (DataQuality) can route to manual review with a real candidate.
     */
    public Optional<VenueMatch> geocode(SalaConcierto sala) {
        // 1. Foursquare by name (primary)
        Optional<VenueMatch> primary = safeLookup(sala);
        if (primary.isPresent() && primary.get().confidence() >= autoAcceptThreshold) {
            return primary;
        }

        // 2. LocationIQ by address (validated fallback — centroid rejection already applied in adapter)
        Optional<VenueMatch> fallback = safeAddressGeocode(sala);
        if (fallback.isPresent()) {
            return fallback;
        }

        // 3. Nothing confident from LocationIQ: return sub-threshold primary (if any) so
        //    DataQualityUseCase can route it to manual review, else empty.
        return primary;
    }

    /**
     * Foursquare-only candidate lookup for admin fill-missing flows.
     * Does not fall back to LocationIQ and never writes anything.
     */
    public Optional<VenueLookupCandidate> bestFoursquareCandidate(SalaConcierto sala, int limit) {
        if (sala.name() == null || sala.name().isBlank()) {
            return Optional.empty();
        }
        try {
            return venueLookupPort.lookupCandidates(sala.name(), sala.city(), sala.province(), limit).stream()
                .filter(match -> "foursquare".equals(match.provider()))
                .max(Comparator.comparingDouble(VenueLookupCandidate::confidence));
        } catch (Exception ex) {
            log.warn("Foursquare candidate lookup failed for sala_id={}: {}", sala.id(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Idempotent sync-path helper. Writes coordinates to the sala ONLY when
     * confidence >= threshold. Never auto-writes weak matches.
     */
    public SalaConcierto geocodeIfNeeded(SalaConcierto sala, Instant updatedAt) {
        if (sala.lat() != null && sala.lng() != null) {
            return sala;
        }
        Optional<VenueMatch> match = geocode(sala);
        if (match.isEmpty() || match.get().confidence() < autoAcceptThreshold) {
            return sala;
        }
        VenueMatch m = match.get();
        return new SalaConcierto(
            sala.id(), sala.name(), sala.address(), sala.city(), sala.province(),
            m.lat(), m.lng(), sala.imageUrl(), sala.description(), sala.sourceUrl(), updatedAt
        );
    }

    private Optional<VenueMatch> safeLookup(SalaConcierto sala) {
        if (sala.name() == null || sala.name().isBlank()) {
            return Optional.empty();
        }
        try {
            return venueLookupPort.lookup(sala.name(), sala.city(), sala.province());
        } catch (Exception ex) {
            log.warn("Foursquare lookup failed for sala_id={}: {}", sala.id(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<VenueMatch> safeAddressGeocode(SalaConcierto sala) {
        if (sala.address() == null || sala.address().isBlank()) {
            return Optional.empty();
        }
        try {
            // Use the ValidatedGeocodingPort path (centroid rejection + importance) if available
            if (geocodingPort instanceof ValidatedGeocodingPort validatedPort) {
                return validatedPort.geocodeValidated(sala.address(), sala.city(), sala.province())
                    .map(v -> {
                        double importance = v.importance();
                        double confidence = Double.isNaN(importance)
                            ? 0.0 : Math.max(0.0, Math.min(1.0, importance));
                        return new VenueMatch(v.lat(), v.lng(), sala.address(), confidence, "address", "locationiq");
                    });
            }
            // Fallback for non-validated adapters (e.g. NoOp in tests): default confidence 0.0
            return geocodingPort.geocode(sala.address(), sala.city(), sala.province())
                .map(coords -> new VenueMatch(
                    coords.lat(), coords.lng(),
                    sala.address(),
                    0.0,
                    "address",
                    "locationiq"
                ));
        } catch (Exception ex) {
            log.warn("LocationIQ geocoding failed for sala_id={}: {}", sala.id(), ex.getMessage());
            return Optional.empty();
        }
    }
}
