package com.rubenazo.buscaConciertos.application;

/**
 * Carries the editable fields for a sala PUT. {@code id}, when present, is compared against
 * the path id for mismatch detection (ADR-4/ADR-8) — it is never used to identify the target row.
 */
public record UpdateSalaCommand(
    String name,
    String address,
    String city,
    String province,
    Double lat,
    Double lng,
    String imageUrl,
    String description,
    String id
) {}
