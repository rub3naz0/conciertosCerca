package com.rubenazo.buscaConciertos.adapters.out.healthchecks;

import com.rubenazo.buscaConciertos.application.ports.out.SyncHeartbeatPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
class HealthchecksConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Bean
    SyncHeartbeatPort syncHeartbeatPort(
        @Value("${app.healthchecks.ping-url:}") String pingUrl
    ) {
        if (pingUrl == null || pingUrl.isBlank()) {
            return new NoOpSyncHeartbeatAdapter();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        RestClient restClient = RestClient.builder()
            .baseUrl(pingUrl)
            .requestFactory(requestFactory)
            .build();
        return new HealthchecksAdapter(restClient);
    }
}
