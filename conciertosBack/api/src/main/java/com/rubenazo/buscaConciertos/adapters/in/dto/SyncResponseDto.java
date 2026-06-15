package com.rubenazo.buscaConciertos.adapters.in.dto;

import com.rubenazo.buscaConciertos.application.SyncResponse;

import java.util.List;
import java.util.function.Function;

public record SyncResponseDto<T>(String timestamp, List<T> data) {
    public static <T, D> SyncResponseDto<D> from(SyncResponse<T> response, Function<T, D> mapper) {
        return new SyncResponseDto<>(
            response.timestamp().toString(),
            response.data().stream().map(mapper).toList()
        );
    }
}
