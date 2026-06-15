package com.rubenazo.buscaConciertos.adapters.out.tavily;

import com.rubenazo.buscaConciertos.application.ports.out.TavilySearchPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TavilyConfig {

    @Bean
    TavilySearchPort tavilySearchPort(
        @Value("${app.tavily.api-key:}") String apiKey
    ) {
        if (apiKey != null && !apiKey.isBlank()) {
            return new TavilySearchAdapter(apiKey);
        }
        return new TavilyNoOpAdapter();
    }
}
