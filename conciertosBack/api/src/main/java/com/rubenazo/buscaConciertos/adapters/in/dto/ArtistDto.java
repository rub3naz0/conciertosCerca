package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rubenazo.buscaConciertos.domain.Artist;

public record ArtistDto(
    String id,
    String name,
    String genre,
    @JsonProperty("image_url") String imageUrl,
    String website,
    String description,
    @JsonProperty("source_url") String sourceUrl
) {
    public static ArtistDto from(Artist artist) {
        return new ArtistDto(
            artist.id(),
            artist.name(),
            artist.genre(),
            artist.imageUrl(),
            artist.website(),
            artist.description(),
            artist.sourceUrl()
        );
    }
}
