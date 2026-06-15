package com.rubenazo.buscaConciertos.domain;

import java.util.Map;

public record EnrichedEntityResult(Map<String, EnrichedFieldValue> fields) {

    public static EnrichedEntityResult empty() {
        return new EnrichedEntityResult(Map.of());
    }
}
