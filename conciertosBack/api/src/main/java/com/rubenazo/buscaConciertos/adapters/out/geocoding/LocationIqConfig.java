package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.rubenazo.buscaConciertos.application.ports.out.GeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.ReverseGeocodingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class LocationIqConfig {

    @Bean
    GeocodingPort geocodingPort(
        @Value("${app.locationiq.api-key:}") String apiKey,
        @Value("${app.locationiq.base-url:https://us1.locationiq.com}") String baseUrl,
        @Value("${app.locationiq.limit:5}") int limit,
        @Value("${app.locationiq.countrycodes:es}") String countryCodes
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return new NoOpGeocodingAdapter();
        }
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
        return new LocationIqGeocodingAdapter(restClient, apiKey, limit, countryCodes);
    }

    @Bean
    ReverseGeocodingPort reverseGeocodingPort(
        @Value("${app.locationiq.api-key:}") String apiKey,
        @Value("${app.locationiq.base-url:https://us1.locationiq.com}") String baseUrl
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return new NoOpReverseGeocodingAdapter();
        }
        RestClient restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
        return new LocationIqReverseAdapter(restClient, apiKey);
    }
}
