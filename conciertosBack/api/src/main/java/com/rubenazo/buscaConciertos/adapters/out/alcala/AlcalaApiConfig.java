package com.rubenazo.buscaConciertos.adapters.out.alcala;

import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSourcePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class AlcalaApiConfig {

    @Bean
    AlcalaSourcePort alcalaSourcePort(
        @Value("${app.alcala.enabled:false}") boolean enabled,
        @Value("${app.alcala.base-url:https://alcalaesmusica.org}") String baseUrl
    ) {
        if (!enabled) {
            return new NoOpAlcalaSourceAdapter();
        }
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
        return new AlcalaApiClientAdapter(restClient);
    }
}
