package com.rubenazo.buscaConciertos.scraper.domain;

import com.rubenazo.buscaConciertos.domain.Artist;

import java.time.Instant;

public record ScrapedArtist(
    String id,
    String name,
    String genre,
    String imageUrl,
    String website,
    String sourceUrl,
    String description
) {
    public Artist toDomain(Instant updatedAt) {
        return new Artist(id, name, genre, imageUrl, website, sourceUrl, description, updatedAt);
    }
}
