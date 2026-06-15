package com.rubenazo.buscaConciertos.adminweb.adapters.out;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ApiFillProxyAdapterTest {

    private WireMockServer wireMock;
    private ApiFillProxyAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        String baseUrl = "http://localhost:" + wireMock.port();
        adapter = new ApiFillProxyAdapter(new RestTemplate(), baseUrl);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // --- 200: success path ---

    @Test
    void fill_successWhenApiReturns200() {
        stubFor(post(urlEqualTo("/api/admin/quality/7/fill"))
            .willReturn(aResponse().withStatus(200)));

        assertThatCode(() -> adapter.fill(7L, "Calle Mayor 5"))
            .doesNotThrowAnyException();
    }

    // --- 400: api returns 400, surfaced as ResponseStatusException ---

    @Test
    void fill_throws400WhenApiReturns400() {
        stubFor(post(urlEqualTo("/api/admin/quality/12/fill"))
            .willReturn(aResponse().withStatus(400).withBody("value must not be blank")));

        assertThatThrownBy(() -> adapter.fill(12L, ""))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- 404: api returns 404, surfaced as ResponseStatusException ---

    @Test
    void fill_throws404WhenApiReturns404() {
        stubFor(post(urlEqualTo("/api/admin/quality/9999/fill"))
            .willReturn(aResponse().withStatus(404).withBody("not found")));

        assertThatThrownBy(() -> adapter.fill(9999L, "value"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- 409: terminal status surfaced ---

    @Test
    void fill_throws409WhenApiReturns409() {
        stubFor(post(urlEqualTo("/api/admin/quality/1/fill"))
            .willReturn(aResponse().withStatus(409).withBody("already approved")));

        assertThatThrownBy(() -> adapter.fill(1L, "value"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- 422: unfillable field surfaced ---

    @Test
    void fill_throws422WhenApiReturns422() {
        stubFor(post(urlEqualTo("/api/admin/quality/20/fill"))
            .willReturn(aResponse().withStatus(422).withBody("Concert fields are not manually fillable")));

        assertThatThrownBy(() -> adapter.fill(20L, "sala-foo"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }
}
