package com.rubenazo.buscaConciertos.domain;

import java.time.Instant;

public record Artist(
    String id,
    String name,
    String genre,
    String imageUrl,
    String website,
    String sourceUrl,
    String description,
    Instant updatedAt
) {}
