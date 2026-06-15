package com.rubenazo.buscaConciertos.scraper.domain;

import com.rubenazo.buscaConciertos.domain.SalaConcierto;

import java.time.Instant;

public record ScrapedVenue(
    String id,
    String name,
    String address,
    String city,
    String province,
    Double lat,
    Double lng,
    String imageUrl,
    String description,
    String sourceUrl
) {
    public SalaConcierto toDomain(Instant updatedAt) {
        return new SalaConcierto(id, name, address, city, province,
            lat, lng, imageUrl, description, sourceUrl, updatedAt);
    }
}
