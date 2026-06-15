package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateConcertRequest(
    @JsonProperty("salaConciertoId") String salaConciertoId,
    @JsonProperty("artistIds") List<String> artistIds,
    String date,
    String time,
    String price
) {}
