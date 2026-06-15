package com.rubenazo.buscaConciertos.domain;

import java.util.List;
import java.util.Map;

public record EntityEnrichmentRequest(
    String entityType,
    String entityId,
    String name,
    String city,
    String province,
    Map<String, String> knownFields,
    List<String> missingFields,
    Map<String, String> contextHints,
    Map<String, List<TavilyResult>> evidence
) {}
