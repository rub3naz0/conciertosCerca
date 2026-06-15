package com.rubenazo.buscaConciertos.application;

/**
 * Carries the editable fields for a concert PUT — date/time/price only (ADR-3: FKs are
 * sourced from the stored row, not the command). {@code id}, when present, is compared
 * against the path id for mismatch detection (ADR-4/ADR-8). {@code salaConciertoId} and
 * {@code artistIds} are accepted only for the equal-or-absent validation (Req "Edit a
 * concert's date, time, and price only") — they are never written.
 */
public record UpdateConcertCommand(
    String date,
    String time,
    String price,
    String id,
    String salaConciertoId,
    java.util.List<String> artistIds
) {}
