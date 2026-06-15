package com.rubenazo.buscaConciertos.adapters.out.llm;

import com.rubenazo.buscaConciertos.application.ports.out.EntityEnrichmentPort;
import com.rubenazo.buscaConciertos.domain.EntityEnrichmentRequest;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LlmNoOpAdapter implements EntityEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(LlmNoOpAdapter.class);

    @Override
    public EnrichedEntityResult enrich(EntityEnrichmentRequest request) {
        log.info("LLM enrichment disabled: app.llm.api-key is not configured");
        return EnrichedEntityResult.empty();
    }
}
