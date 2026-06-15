package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rubenazo.buscaConciertos.domain.Concert;

import java.util.List;

public record ConcertDto(
    String id,
    @JsonProperty("salaConcierto_id") String salaConciertoId,
    @JsonProperty("artist_ids") List<String> artistIds,
    String date,
    String time,
    String price,
    @JsonProperty("source_url") String sourceUrl,
    @JsonProperty("updated_at") String updatedAt
) {
    public static ConcertDto from(Concert concert) {
        return new ConcertDto(
            concert.id(),
            concert.salaConciertoId(),
            concert.artistIds(),
            concert.date().toString(),
            concert.time(),
            concert.price(),
            concert.sourceUrl(),
            concert.updatedAt().toString()
        );
    }
}
