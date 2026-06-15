package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rubenazo.buscaConciertos.application.ports.out.AdminArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class LocationIqReverseAdapterTest {

    private WireMockServer wireMock;
    private LocationIqReverseAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        adapter = new LocationIqReverseAdapter(
            RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build(),
            "test-locationiq-key"
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // Phase 4.1 — county → province extraction; city from address.city
    @Test
    void reverse_extractsCountyAsProvinceAndCityAsCity() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/reverse"))
            .withQueryParam("key", equalTo("test-locationiq-key"))
            .withQueryParam("lat", equalTo("40.481"))
            .withQueryParam("lon", equalTo("-3.364"))
            .withQueryParam("format", equalTo("json"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "address": {
                        "city": "Alcalá de Henares",
                        "county": "Madrid",
                        "state": "Comunidad de Madrid"
                      }
                    }
                    """)));

        Optional<AdminArea> result = adapter.reverse(40.481, -3.364);

        assertThat(result).isPresent();
        assertThat(result.get().city()).isEqualTo("Alcalá de Henares");
        assertThat(result.get().province()).isEqualTo("Madrid");
    }

    // Phase 4.2 — province fallback chain: no county → use address.province
    @Test
    void reverse_fallsBackToProvinceWhenCountyAbsent() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/reverse"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "address": {
                        "city": "Guadalajara",
                        "province": "Guadalajara",
                        "state": "Castilla-La Mancha"
                      }
                    }
                    """)));

        Optional<AdminArea> result = adapter.reverse(40.632, -3.166);

        assertThat(result).isPresent();
        assertThat(result.get().province()).isEqualTo("Guadalajara");
    }

    // Phase 4.3 — city fallback chain: no city → use address.town
    @Test
    void reverse_fallsBackToTownWhenCityAbsent() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/reverse"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "address": {
                        "town": "Meco",
                        "county": "Madrid",
                        "state": "Comunidad de Madrid"
                      }
                    }
                    """)));

        Optional<AdminArea> result = adapter.reverse(40.559, -3.336);

        assertThat(result).isPresent();
        assertThat(result.get().city()).isEqualTo("Meco");
        assertThat(result.get().province()).isEqualTo("Madrid");
    }

    // Phase 4.4 — HTTP error → Optional.empty(), no exception propagated
    @Test
    void reverse_returnsEmptyOnHttpError() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/reverse"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));

        Optional<AdminArea> result = adapter.reverse(40.481, -3.364);

        assertThat(result).isEmpty();
    }
}
