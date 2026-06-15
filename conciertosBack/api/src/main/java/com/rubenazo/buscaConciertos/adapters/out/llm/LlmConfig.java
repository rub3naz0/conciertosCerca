package com.rubenazo.buscaConciertos.adapters.out.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.rubenazo.buscaConciertos.application.ports.out.EntityEnrichmentPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
class LlmConfig {

    @Bean
    EntityEnrichmentPort entityEnrichmentPort(
        @Value("${app.llm.api-key:}") String apiKey,
        @Value("${app.llm.model:gpt-4o-mini}") String model,
        @Value("${app.llm.timeout-ms:30000}") long timeoutMs,
        @Value("${app.llm.max-retries:2}") int maxRetries
    ) {
        if (apiKey != null && !apiKey.isBlank()) {
            OpenAIClient openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofMillis(timeoutMs))
                .maxRetries(maxRetries)
                .build();
            return new OpenAiEnrichmentAdapter(openAIClient, model);
        }
        return new LlmNoOpAdapter();
    }
}
