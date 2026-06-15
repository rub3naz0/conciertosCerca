package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateSalaRequest(
    String name,
    String address,
    String city,
    String province,
    Double lat,
    Double lng,
    @JsonProperty("image_url") String imageUrl,
    String description
) {}
