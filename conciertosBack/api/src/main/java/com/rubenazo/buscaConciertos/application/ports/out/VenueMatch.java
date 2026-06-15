package com.rubenazo.buscaConciertos.application.ports.out;

public record VenueMatch(
    double lat,
    double lng,
    String displayName,
    double confidence,   // already clamped to [0,1] by the producing adapter
    String matchType,    // "name" (Foursquare) | "address" (LocationIQ)
    String provider      // "foursquare" | "locationiq"
) {}
