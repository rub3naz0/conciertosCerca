package com.rubenazo.buscaConciertos.adminweb.adapters.out.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConcertProxyDto(
    String id,
    @JsonProperty("salaConcierto_id") String salaConciertoId,
    @JsonProperty("artist_ids") List<String> artistIds,
    String date,
    String time,
    String price,
    @JsonProperty("source_url") String sourceUrl,
    @JsonProperty("updated_at") String updatedAt
) {}
