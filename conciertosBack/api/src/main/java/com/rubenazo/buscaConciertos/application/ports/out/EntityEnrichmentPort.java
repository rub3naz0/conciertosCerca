package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.EntityEnrichmentRequest;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;

public interface EntityEnrichmentPort {
    EnrichedEntityResult enrich(EntityEnrichmentRequest request);
}
