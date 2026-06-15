package com.rubenazo.buscaConciertos.scraper.adapters.out;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsoupHtmlFetchAdapterTest {

    private WireMockServer wireMock;
    private JsoupHtmlFetchAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        adapter = new JsoupHtmlFetchAdapter(500, 3);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void fetch_returnsHtmlOnSuccess() throws HtmlFetchException {
        wireMock.stubFor(get("/test").willReturn(
            aResponse().withStatus(200).withBody("<html><body>Hello</body></html>")
        ));

        String html = adapter.fetch("http://localhost:" + wireMock.port() + "/test");

        assertThat(html).contains("Hello");
    }

    @Test
    void fetch_sendsBrowserUserAgent() throws HtmlFetchException {
        wireMock.stubFor(get("/agent-check").willReturn(
            aResponse().withStatus(200).withBody("<html></html>")
        ));

        adapter.fetch("http://localhost:" + wireMock.port() + "/agent-check");

        wireMock.verify(getRequestedFor(urlEqualTo("/agent-check"))
            .withHeader("User-Agent", matching("Mozilla.*")));
    }

    @Test
    void fetch_404_throwsHtmlFetchException() {
        wireMock.stubFor(get("/missing").willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> adapter.fetch("http://localhost:" + wireMock.port() + "/missing"))
            .isInstanceOf(HtmlFetchException.class)
            .satisfies(e -> assertThat(((HtmlFetchException) e).getStatusCode()).isEqualTo(404));
    }

    @Test
    void fetch_rateLimitEnforcedBetweenTwoCalls() throws HtmlFetchException {
        wireMock.stubFor(get(anyUrl()).willReturn(
            aResponse().withStatus(200).withBody("<html></html>")
        ));

        String url = "http://localhost:" + wireMock.port() + "/page";
        long before = System.currentTimeMillis();
        adapter.fetch(url);
        adapter.fetch(url);
        long elapsed = System.currentTimeMillis() - before;

        assertThat(elapsed).isGreaterThanOrEqualTo(500L);
    }

    @Test
    void fetch_retriesOn503_upToMaxRetries() throws HtmlFetchException {
        wireMock.stubFor(get("/retry")
            .inScenario("503-then-200")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("After-1st-503")
        );
        wireMock.stubFor(get("/retry")
            .inScenario("503-then-200")
            .whenScenarioStateIs("After-1st-503")
            .willReturn(aResponse().withStatus(200).withBody("<html>OK</html>"))
        );

        // Use adapter with no rate limit for this test to speed it up
        JsoupHtmlFetchAdapter fastAdapter = new JsoupHtmlFetchAdapter(0, 3);
        String html = fastAdapter.fetch("http://localhost:" + wireMock.port() + "/retry");

        assertThat(html).contains("OK");
    }
}
