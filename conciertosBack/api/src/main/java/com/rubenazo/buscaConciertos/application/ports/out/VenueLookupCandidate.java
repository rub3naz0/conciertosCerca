package com.rubenazo.buscaConciertos.application.ports.out;

public record VenueLookupCandidate(
    double lat,
    double lng,
    String displayName,
    String formattedAddress,
    double confidence,
    String matchType,
    String provider
) {
    public VenueMatch toVenueMatch() {
        return new VenueMatch(lat, lng, displayName, confidence, matchType, provider);
    }

    public static VenueLookupCandidate from(VenueMatch match) {
        return new VenueLookupCandidate(
            match.lat(),
            match.lng(),
            match.displayName(),
            null,
            match.confidence(),
            match.matchType(),
            match.provider()
        );
    }
}
