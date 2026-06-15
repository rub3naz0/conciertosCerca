package com.rubenazo.buscaConciertos.adapters.out.tavily;

import com.rubenazo.buscaConciertos.application.ports.out.TavilySearchPort;
import com.rubenazo.buscaConciertos.domain.SearchOptions;
import com.rubenazo.buscaConciertos.domain.TavilyResult;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TavilySearchAdapter implements TavilySearchPort {

    private final RestClient restClient;
    private final String apiKey;

    TavilySearchAdapter(String apiKey) {
        this.restClient = RestClient.builder()
            .baseUrl("https://api.tavily.com")
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
        this.apiKey = apiKey;
    }

    // Package-visible constructor for testing with a custom RestClient and base URL
    TavilySearchAdapter(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    @Override
    public List<TavilyResult> search(String query, SearchOptions options) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("search_depth", options.searchDepth());
        body.put("max_results", options.maxResults());

        TavilyResponse response = restClient.post()
            .uri("/search")
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .body(body)
            .retrieve()
            .body(TavilyResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
            .map(r -> new TavilyResult(r.title(), r.url(), r.content(), r.score()))
            .toList();
    }

    record TavilyResponse(List<TavilyResultItem> results) {}
    record TavilyResultItem(String title, String url, String content, double score) {}
}
