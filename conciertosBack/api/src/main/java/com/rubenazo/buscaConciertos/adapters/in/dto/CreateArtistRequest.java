package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateArtistRequest(
    String name,
    String genre,
    @JsonProperty("image_url") String imageUrl,
    String website,
    String description
) {}
