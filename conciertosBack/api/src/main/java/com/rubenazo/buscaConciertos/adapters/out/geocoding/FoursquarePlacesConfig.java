package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class FoursquarePlacesConfig {

    @Bean
    VenueLookupPort venueLookupPort(
        @Value("${app.foursquare.api-key:}") String apiKey,
        @Value("${app.foursquare.base-url:https://places-api.foursquare.com}") String baseUrl,
        @Value("${app.foursquare.places-api-version:2025-06-17}") String apiVersion
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return new NoOpVenueLookupAdapter();
        }
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("X-Places-Api-Version", apiVersion)
            .defaultHeader("Accept", "application/json")
            .build();
        return new FoursquarePlacesAdapter(restClient);
    }
}
