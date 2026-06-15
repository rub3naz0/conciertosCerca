package com.rubenazo.buscaConciertos.adapters.out.alcala.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AemVenueDto(
    Integer id,
    String name,
    String address,
    String latitude,
    String longitude,
    String image,
    @JsonProperty("profile_image") String profileImage,
    String description
) {}
