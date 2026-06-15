package com.rubenazo.buscaConciertos.adapters.out.geocoding;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.rubenazo.buscaConciertos.application.ports.out.GeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.ValidatedGeocodingPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class LocationIqGeocodingAdapterTest {

    private WireMockServer wireMock;
    private LocationIqGeocodingAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        adapter = new LocationIqGeocodingAdapter(
            RestClient.builder()
                .baseUrl("http://localhost:" + wireMock.port())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build(),
            "test-locationiq-key",
            1,
            "es"
        );
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void geocode_callsLocationIqSearchAndParsesFirstCoordinate() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .withQueryParam("key", equalTo("test-locationiq-key"))
            .withQueryParam("format", equalTo("json"))
            .withQueryParam("limit", equalTo("1"))
            .withQueryParam("countrycodes", equalTo("es"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [
                      {
                        "lat": "40.4168",
                        "lon": "-3.7038",
                        "display_name": "Calle Mayor 5, Madrid, España"
                      }
                    ]
                    """)));

        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Calle Mayor 5", "Madrid", "Madrid");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(40.4168);
        assertThat(result.get().lng()).isEqualTo(-3.7038);
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/v1/search"))
            .withQueryParam("q", equalTo("Calle Mayor 5, Madrid, Madrid, España")));
    }

    @Test
    void geocode_returnsEmptyWhenLocationIqHasNoResults() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));

        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Unknown", "Madrid", "Madrid");

        assertThat(result).isEmpty();
    }

    @Test
    void geocode_returnsEmptyWhenCoordinatesAreOutOfRange() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{ "lat": "123.0", "lon": "-3.7038" }]
                    """)));

        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Bad", "Madrid", "Madrid");

        assertThat(result).isEmpty();
    }

    // --- Centroid rejection tests ---

    @Test
    void geocode_rejectsCentroid_classBoundary() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{"lat":"40.4168","lon":"-3.7038","display_name":"Madrid","class":"boundary","type":"administrative","importance":"0.85"}]
                    """)));

        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Madrid", "Madrid", "Madrid");

        assertThat(result).isEmpty();
    }

    @Test
    void geocode_rejectsCentroid_typeContainsCity() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{"lat":"41.38","lon":"2.17","display_name":"Barcelona","class":"place","type":"city","importance":"0.9"}]
                    """)));

        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Barcelona", "Barcelona", "Barcelona");

        assertThat(result).isEmpty();
    }

    @Test
    void geocode_passesLegitimateVenueResult() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{"lat":"41.375","lon":"2.169","display_name":"Sala Apolo","class":"amenity","type":"music_venue","importance":"0.62"}]
                    """)));

        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Sala Apolo", "Barcelona", "Cataluña");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(41.375);
    }

    @Test
    void geocode_importanceClamped_whenAbove1() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{"lat":"41.375","lon":"2.169","display_name":"Some Venue","class":"amenity","type":"music_venue","importance":"1.25"}]
                    """)));

        // Just verify it does NOT reject the result (importance out of range is clamped, not error)
        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Some Venue", "Barcelona", "Cataluña");
        assertThat(result).isPresent();

        // Verify clamped importance via geocodeValidated
        ValidatedGeocodingPort.ValidatedCoordinates validated =
            adapter.geocodeValidated("Some Venue", "Barcelona", "Cataluña").orElse(null);
        assertThat(validated).isNotNull();
        assertThat(validated.importance()).isEqualTo(1.0);
    }

    @Test
    void geocode_skipsLeadingCentroidAndReturnsNextRealVenue() {
        // results[0] is a city centroid, results[1] is a real venue.
        // The adapter must skip the centroid and return the venue instead of giving up.
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [
                      {"lat":"41.38","lon":"2.17","display_name":"Barcelona","class":"place","type":"city","importance":"0.9"},
                      {"lat":"41.375","lon":"2.169","display_name":"Sala Apolo","class":"amenity","type":"music_venue","importance":"0.6"}
                    ]
                    """)));

        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Sala Apolo", "Barcelona", "Cataluña");

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(41.375);
        assertThat(result.get().lng()).isEqualTo(2.169);
    }

    @Test
    void geocode_allCentroids_returnsEmpty() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [
                      {"lat":"41.38","lon":"2.17","display_name":"Barcelona","class":"place","type":"city","importance":"0.9"},
                      {"lat":"40.41","lon":"-3.70","display_name":"Madrid","class":"boundary","type":"administrative","importance":"0.85"}
                    ]
                    """)));

        Optional<GeocodingPort.Coordinates> result = adapter.geocode("Nowhere", "Madrid", "Madrid");

        assertThat(result).isEmpty();
    }

    @Test
    void geocode_nanImportance_defaultsTo0NotNaN() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{"lat":"41.375","lon":"2.169","display_name":"Some Venue","class":"amenity","type":"music_venue","importance":"NaN"}]
                    """)));

        ValidatedGeocodingPort.ValidatedCoordinates validated =
            adapter.geocodeValidated("Some Venue", "Barcelona", "Cataluña").orElse(null);
        assertThat(validated).isNotNull();
        assertThat(Double.isNaN(validated.importance())).isFalse();
        assertThat(validated.importance()).isEqualTo(0.0);
    }

    @Test
    void geocode_nullImportance_defaultsTo0() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/search"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [{"lat":"41.375","lon":"2.169","display_name":"Some Venue","class":"amenity","type":"music_venue"}]
                    """)));

        ValidatedGeocodingPort.ValidatedCoordinates validated =
            adapter.geocodeValidated("Some Venue", "Barcelona", "Cataluña").orElse(null);
        assertThat(validated).isNotNull();
        assertThat(validated.importance()).isEqualTo(0.0);
    }
}
