package com.rubenazo.buscaConciertos.domain;

import java.time.Instant;

public record SalaConcierto(
    String id,
    String name,
    String address,
    String city,
    String province,
    Double lat,
    Double lng,
    String imageUrl,
    String description,
    String sourceUrl,
    Instant updatedAt
) {}
