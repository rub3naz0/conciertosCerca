package com.rubenazo.buscaConciertos.adapters.out.tavily;

import com.rubenazo.buscaConciertos.application.ports.out.TavilySearchPort;
import com.rubenazo.buscaConciertos.domain.SearchOptions;
import com.rubenazo.buscaConciertos.domain.TavilyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TavilyNoOpAdapter implements TavilySearchPort {

    private static final Logger log = LoggerFactory.getLogger(TavilyNoOpAdapter.class);

    @Override
    public List<TavilyResult> search(String query, SearchOptions options) {
        log.info("Tavily enrichment is disabled: app.tavily.api-key is not configured");
        return List.of();
    }
}
