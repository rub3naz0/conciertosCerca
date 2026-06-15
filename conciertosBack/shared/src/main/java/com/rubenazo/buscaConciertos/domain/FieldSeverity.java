package com.rubenazo.buscaConciertos.domain;

import java.util.Map;

public enum FieldSeverity {
    SEVERE, NON_SEVERE;

    private static final Map<String, Map<String, FieldSeverity>> REGISTRY = Map.of(
        "sala", Map.of(
            "name", SEVERE,
            "address", SEVERE,
            "lat", SEVERE,
            "lng", SEVERE,
            "city", SEVERE,
            "province", SEVERE,
            "description", NON_SEVERE,
            "image_url", NON_SEVERE,
            "source_url", NON_SEVERE
        ),
        "artist", Map.of(
            "name", SEVERE,
            "genre", SEVERE,
            "image_url", NON_SEVERE,
            "website", NON_SEVERE,
            "description", NON_SEVERE,
            "source_url", NON_SEVERE
        ),
        "concert", Map.of(
            "sala_concierto_id", SEVERE,
            "date", SEVERE,
            "time", NON_SEVERE,
            "price", NON_SEVERE,
            "source_url", NON_SEVERE
        )
    );

    public static FieldSeverity of(String entityType, String field) {
        return REGISTRY.getOrDefault(entityType, Map.of()).getOrDefault(field, NON_SEVERE);
    }

    public String toDbValue() {
        return this == SEVERE ? "severe" : "non_severe";
    }
}
