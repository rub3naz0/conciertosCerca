package com.rubenazo.buscaConciertos.adapters.out.alcala.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AemBandDto(
    Integer id,
    String name,
    String slug,
    String genre,
    @JsonProperty("band_image") String bandImage,
    @JsonProperty("profile_image") String profileImage,
    @JsonProperty("webpage_link") String webpageLink,
    String description
) {}
