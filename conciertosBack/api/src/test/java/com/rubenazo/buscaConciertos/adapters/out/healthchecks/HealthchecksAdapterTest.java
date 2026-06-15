package com.rubenazo.buscaConciertos.adapters.out.healthchecks;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatNoException;

class HealthchecksAdapterTest {

    private WireMockServer wireMock;
    private HealthchecksAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:" + wireMock.port())
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
        adapter = new HealthchecksAdapter(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void pingStart_postsToSlashStart() {
        wireMock.stubFor(post(urlEqualTo("/start")).willReturn(aResponse().withStatus(200)));

        adapter.pingStart();

        wireMock.verify(1, postRequestedFor(urlEqualTo("/start")));
    }

    @Test
    void pingSuccess_postsToRootWithSummaryBody() {
        wireMock.stubFor(post(urlEqualTo("/")).willReturn(aResponse().withStatus(200)));

        adapter.pingSuccess("principal=completed alcala=completed");

        wireMock.verify(1, postRequestedFor(urlEqualTo("/")));
    }

    @Test
    void pingFail_postsToSlashFailWithSummaryBody() {
        wireMock.stubFor(post(urlEqualTo("/fail")).willReturn(aResponse().withStatus(200)));

        adapter.pingFail("principal=failed alcala=skipped");

        wireMock.verify(1, postRequestedFor(urlEqualTo("/fail"))
            .withRequestBody(equalTo("principal=failed alcala=skipped")));
    }

    @Test
    void ping_doesNotThrowWhenServerReturnsError() {
        wireMock.stubFor(post(urlEqualTo("/start")).willReturn(aResponse().withStatus(500)));

        assertThatNoException().isThrownBy(adapter::pingStart);
    }

    @Test
    void ping_doesNotThrowWhenServerIsUnreachable() {
        wireMock.stop();

        assertThatNoException().isThrownBy(adapter::pingStart);
        assertThatNoException().isThrownBy(() -> adapter.pingSuccess("ok"));
        assertThatNoException().isThrownBy(() -> adapter.pingFail("nope"));
    }
}
