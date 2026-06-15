package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateArtistRequest(
    String name,
    String genre,
    String description,
    @JsonProperty("image_url") String imageUrl,
    String id
) {}
