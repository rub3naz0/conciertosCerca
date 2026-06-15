package com.rubenazo.buscaConciertos.application;

/**
 * Carries the editable fields for an artist PUT. {@code id}, when present, is compared against
 * the path id for mismatch detection (ADR-4/ADR-8) — it is never used to identify the target row.
 */
public record UpdateArtistCommand(
    String name,
    String genre,
    String description,
    String imageUrl,
    String id
) {}
