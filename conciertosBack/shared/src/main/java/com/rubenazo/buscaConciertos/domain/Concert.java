package com.rubenazo.buscaConciertos.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Concert(
    String id,
    String salaConciertoId,
    List<String> artistIds,
    LocalDate date,
    String time,
    String price,
    String sourceUrl,
    Instant updatedAt
) {}
