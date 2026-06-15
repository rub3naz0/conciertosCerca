package com.rubenazo.buscaConciertos.adminweb.adapters.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalaConciertoProxyDto(
    String id,
    String name,
    String address,
    String city,
    String province,
    Double lat,
    Double lng,
    @JsonProperty("image_url") String imageUrl,
    String description,
    @JsonProperty("source_url") String sourceUrl
) {}
