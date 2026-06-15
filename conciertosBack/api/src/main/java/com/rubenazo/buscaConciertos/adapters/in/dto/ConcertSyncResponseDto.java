package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rubenazo.buscaConciertos.application.ConcertSyncResponse;

import java.util.List;

public record ConcertSyncResponseDto(
    String timestamp,
    List<ConcertDto> data,
    @JsonProperty("deleted_ids") List<String> deletedIds
) {
    public static ConcertSyncResponseDto from(ConcertSyncResponse response) {
        return new ConcertSyncResponseDto(
            response.timestamp().toString(),
            response.data().stream().map(ConcertDto::from).toList(),
            response.deletedIds()
        );
    }
}
