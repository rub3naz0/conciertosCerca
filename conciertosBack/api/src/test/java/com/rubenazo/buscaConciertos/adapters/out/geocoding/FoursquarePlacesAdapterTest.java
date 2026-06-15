package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupCandidate;
import com.rubenazo.buscaConciertos.application.ports.out.VenueMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FoursquarePlacesAdapterTest {

    private static final String API_VERSION = "2025-06-17";
    private static final String TEST_KEY = "test-key";

    private WireMockServer wireMock;
    private FoursquarePlacesAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        RestClient restClient = RestClient.builder()
            .baseUrl("http://localhost:" + wireMock.port())
            .requestFactory(new SimpleClientHttpRequestFactory())
            .defaultHeader("Authorization", "Bearer " + TEST_KEY)
            .defaultHeader("X-Places-Api-Version", API_VERSION)
            .defaultHeader("Accept", "application/json")
            .build();

        adapter = new FoursquarePlacesAdapter(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // --- Happy path ---

    @Test
    void lookup_happyPath_returnsMappedVenueMatch() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(GOLDEN_RESPONSE)));

        Optional<VenueMatch> result = adapter.lookup("Sala Apolo", "Barcelona", "Cataluña");

        assertThat(result).isPresent();
        VenueMatch match = result.get();
        assertThat(match.provider()).isEqualTo("foursquare");
        assertThat(match.matchType()).isEqualTo("name");
        assertThat(match.lat()).isCloseTo(41.375, within(0.001));
        assertThat(match.lng()).isCloseTo(2.169, within(0.001));
        assertThat(match.displayName()).isEqualTo("Sala Apolo");
    }

    // Judgment Day 2026-06-08: null city/province must not leak "null" into the near query.
    @Test
    void lookup_nullCityAndProvince_nearOmitsNullAndKeepsCountry() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(GOLDEN_RESPONSE)));

        adapter.lookup("Sala Apolo", null, null);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/places/search"))
            .withQueryParam("near", equalTo("España")));
    }

    @Test
    void lookup_nullProvinceOnly_nearKeepsCityAndCountry() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(GOLDEN_RESPONSE)));

        adapter.lookup("Sala Apolo", "Barcelona", null);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/places/search"))
            .withQueryParam("near", equalTo("Barcelona, España")));
    }

    @Test
    void lookup_blankCityWithProvince_nearOmitsBlankCity() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(GOLDEN_RESPONSE)));

        adapter.lookup("Sala Apolo", "   ", "Cataluña");

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/places/search"))
            .withQueryParam("near", equalTo("Cataluña, España")));
    }

    @Test
    void lookup_requestCarriesAuthorizationHeader() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .withHeader("Authorization", equalTo("Bearer test-key"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(GOLDEN_RESPONSE)));

        adapter.lookup("Sala Apolo", "Barcelona", "Cataluña");

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/places/search"))
            .withHeader("Authorization", equalTo("Bearer test-key")));
    }

    @Test
    void lookup_requestCarriesVersionHeader() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .withHeader("X-Places-Api-Version", equalTo(API_VERSION))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(GOLDEN_RESPONSE)));

        adapter.lookup("Sala Apolo", "Barcelona", "Cataluña");

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/places/search"))
            .withHeader("X-Places-Api-Version", equalTo(API_VERSION)));
    }

    @Test
    void lookup_confidenceFormula_musicCategory_equals_1() {
        // identical name + music category → confidence = 0.6*1.0 + 0.4*1.0 = 1.0
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(GOLDEN_RESPONSE)));

        Optional<VenueMatch> result = adapter.lookup("Sala Apolo", "Barcelona", "Cataluña");

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isCloseTo(1.0, within(0.01));
    }

    @Test
    void lookupCandidates_usesRequestedLimitAndMapsValidResults() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"results":[
                      {"name":"Sala Apolo","latitude":41.375,"longitude":2.169,
                       "categories":[{"id":10032,"name":"Music Venue"}],
                       "location":{"formatted_address":"Carrer Nou 113, Barcelona"}},
                      {"name":"Apolo Store","latitude":41.0,"longitude":2.0,
                       "categories":[{"id":13000,"name":"Retail"}],
                       "location":{"formatted_address":"Barcelona"}}
                    ]}
                    """)));

        List<VenueLookupCandidate> results = adapter.lookupCandidates("Sala Apolo", "Barcelona", "Cataluña", 3);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).displayName()).isEqualTo("Sala Apolo");
        assertThat(results.get(0).formattedAddress()).isEqualTo("Carrer Nou 113, Barcelona");
        assertThat(results.get(1).displayName()).isEqualTo("Apolo Store");
        assertThat(results.get(1).formattedAddress()).isEqualTo("Barcelona");
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/places/search"))
            .withQueryParam("limit", equalTo("3")));
    }

    // --- Error contract: 422, 429, empty results ---

    @Test
    void lookup_returns422_returnsEmpty_noException() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse().withStatus(422)));

        Optional<VenueMatch> result = adapter.lookup("Sala Granada", "locales", "Andalucía");

        assertThat(result).isEmpty();
    }

    @Test
    void lookup_returns429_returnsEmpty_noException() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse().withStatus(429)));

        Optional<VenueMatch> result = adapter.lookup("Sala Test", "Madrid", "Madrid");

        assertThat(result).isEmpty();
    }

    @Test
    void lookup_emptyResults_returnsEmpty() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"results\":[]}")));

        Optional<VenueMatch> result = adapter.lookup("Unknown Venue", "Madrid", "Madrid");

        assertThat(result).isEmpty();
    }

    @Test
    void lookup_nullLatitude_returnsEmpty() {
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"results":[{"name":"Some Venue","latitude":null,"longitude":2.169,"categories":[],
                    "location":{"formatted_address":"Somewhere"}}]}
                    """)));

        Optional<VenueMatch> result = adapter.lookup("Some Venue", "Barcelona", "Cataluña");

        assertThat(result).isEmpty();
    }

    // --- categoryMatch booster ---

    @Test
    void lookup_restaurantCategory_reducesConfidence() {
        // identical name + restaurant category → categoryMatch=0.5 → confidence = 0.6*1.0 + 0.4*0.5 = 0.8
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"results":[{"name":"Sala Apolo","latitude":41.375,"longitude":2.169,
                    "categories":[{"id":13000,"name":"Restaurant"}],
                    "location":{"formatted_address":"Carrer Nou 113, Barcelona"}}]}
                    """)));

        Optional<VenueMatch> result = adapter.lookup("Sala Apolo", "Barcelona", "Cataluña");

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isCloseTo(0.8, within(0.01));
    }

    @Test
    void lookup_musicVenueCategory_boostsConfidence() {
        // identical name + "Music Venue" category → categoryMatch=1.0 → confidence = 1.0
        wireMock.stubFor(get(urlPathEqualTo("/places/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"results":[{"name":"Sala Apolo","latitude":41.375,"longitude":2.169,
                    "categories":[{"id":10032,"name":"Music Venue"}],
                    "location":{"formatted_address":"Carrer Nou 113, Barcelona"}}]}
                    """)));

        Optional<VenueMatch> result = adapter.lookup("Sala Apolo", "Barcelona", "Cataluña");

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isCloseTo(1.0, within(0.01));
    }

    // --- Golden JSON pinned to new API shape (top-level lat/lng) ---

    private static final String GOLDEN_RESPONSE = """
        {
          "results": [
            {
              "name": "Sala Apolo",
              "latitude": 41.375,
              "longitude": 2.169,
              "categories": [
                {
                  "id": 10032,
                  "name": "Music Venue"
                }
              ],
              "location": {
                "formatted_address": "Carrer de la Nou de la Rambla 113, Barcelona"
              }
            }
          ]
        }
        """;
}
