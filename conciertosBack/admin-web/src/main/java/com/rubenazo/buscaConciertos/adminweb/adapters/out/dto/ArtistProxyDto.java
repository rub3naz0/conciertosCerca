package com.rubenazo.buscaConciertos.adminweb.adapters.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtistProxyDto(
    String id,
    String name,
    String genre,
    @JsonProperty("image_url") String imageUrl,
    String website,
    String description,
    @JsonProperty("source_url") String sourceUrl
) {}
