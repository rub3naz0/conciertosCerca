package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.domain.Concert;

import java.time.Instant;
import java.util.List;

public record ConcertSyncResponse(Instant timestamp, List<Concert> data, List<String> deletedIds) {}
