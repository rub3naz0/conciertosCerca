package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;

public record SalaConciertoDto(
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
) {
    public static SalaConciertoDto from(SalaConcierto sala) {
        return new SalaConciertoDto(
            sala.id(),
            sala.name(),
            sala.address(),
            sala.city(),
            sala.province(),
            sala.lat(),
            sala.lng(),
            sala.imageUrl(),
            sala.description(),
            sala.sourceUrl()
        );
    }
}
