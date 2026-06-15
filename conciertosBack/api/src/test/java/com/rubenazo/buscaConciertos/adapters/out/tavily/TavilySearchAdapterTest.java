package com.rubenazo.buscaConciertos.adapters.out.tavily;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rubenazo.buscaConciertos.domain.SearchOptions;
import com.rubenazo.buscaConciertos.domain.TavilyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class TavilySearchAdapterTest {

    private WireMockServer wireMock;
    private TavilySearchAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        adapter = new TavilySearchAdapter(
            RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build(),
            "test-api-key"
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void search_postsToTavilyWithAuthorizationHeader() {
        wireMock.stubFor(post(urlEqualTo("/search"))
            .withHeader("Authorization", equalTo("Bearer test-api-key"))
            .withHeader("Content-Type", containing("application/json"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "results": [
                        {
                          "title": "Sala Apolo Barcelona",
                          "url": "https://www.sala-apolo.com",
                          "content": "+34 933 441 001",
                          "score": 0.92
                        }
                      ]
                    }
                    """)));

        List<TavilyResult> results = adapter.search("Sala Apolo Barcelona phone");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).url()).isEqualTo("https://www.sala-apolo.com");
        assertThat(results.get(0).score()).isEqualTo(0.92);
        assertThat(results.get(0).content()).isEqualTo("+34 933 441 001");
        assertThat(results.get(0).title()).isEqualTo("Sala Apolo Barcelona");
    }

    @Test
    void search_sendsQueryInRequestBody() {
        wireMock.stubFor(post(urlEqualTo("/search"))
            .withRequestBody(containing("\"query\""))
            .withRequestBody(containing("Vetusta Morla genre"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "results": []
                    }
                    """)));

        List<TavilyResult> results = adapter.search("Vetusta Morla genre");

        assertThat(results).isEmpty();
        wireMock.verify(1, postRequestedFor(urlEqualTo("/search"))
            .withRequestBody(containing("Vetusta Morla genre")));
    }

    @Test
    void search_returnsEmptyListOnEmptyResults() {
        wireMock.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "results": []
                    }
                    """)));

        List<TavilyResult> results = adapter.search("no results query");

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsMultipleResults() {
        wireMock.stubFor(post(urlEqualTo("/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "results": [
                        {
                          "title": "Result 1",
                          "url": "https://example1.com",
                          "content": "content one",
                          "score": 0.9
                        },
                        {
                          "title": "Result 2",
                          "url": "https://example2.com",
                          "content": "content two",
                          "score": 0.6
                        }
                      ]
                    }
                    """)));

        List<TavilyResult> results = adapter.search("multi results");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).score()).isEqualTo(0.9);
        assertThat(results.get(1).score()).isEqualTo(0.6);
    }

    @Test
    void withOptions_sendSearchDepthAndMaxResults() {
        wireMock.stubFor(post(urlEqualTo("/search"))
            .withRequestBody(containing("\"search_depth\""))
            .withRequestBody(containing("\"advanced\""))
            .withRequestBody(containing("\"max_results\""))
            .withRequestBody(containing("10"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "results": []
                    }
                    """)));

        TavilySearchAdapter advancedAdapter = new TavilySearchAdapter(
            RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build(),
            "test-api-key"
        );

        advancedAdapter.search("Sala Apolo descripción", new SearchOptions("advanced", 10));

        wireMock.verify(1, postRequestedFor(urlEqualTo("/search"))
            .withRequestBody(containing("\"search_depth\""))
            .withRequestBody(containing("\"advanced\""))
            .withRequestBody(containing("\"max_results\"")));
    }

    @Test
    void singleArgOverload_usesDefaults() {
        wireMock.stubFor(post(urlEqualTo("/search"))
            .withRequestBody(containing("\"search_depth\""))
            .withRequestBody(containing("\"basic\""))
            .withRequestBody(containing("\"max_results\""))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "results": [
                        {
                          "title": "Result",
                          "url": "https://example.com",
                          "content": "content",
                          "score": 0.8
                        }
                      ]
                    }
                    """)));

        List<TavilyResult> results = adapter.search("Vetusta Morla género musical");

        assertThat(results).hasSize(1);
        wireMock.verify(1, postRequestedFor(urlEqualTo("/search"))
            .withRequestBody(containing("\"search_depth\""))
            .withRequestBody(containing("\"basic\"")));
    }
}
