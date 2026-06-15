package com.rubenazo.buscaConciertos.scraper.application.parsers;

import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VenueDetailParserTest {

    private VenueDetailParser parser;

    @BeforeEach
    void setUp() {
        parser = new VenueDetailParser();
    }

    private String loadFixture(String name) throws IOException {
        URL resource = getClass().getClassLoader().getResource("html/" + name);
        return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void parse_allFields_returnsCompleteVenue() throws IOException {
        String html = loadFixture("venue-detail-happy.html");
        List<Discrepancy> discrepancies = new ArrayList<>();
        String sourceUrl = "https://conciertos.club/barcelona/locales/sala-apolo";

        Optional<ScrapedVenue> result = parser.parse(html, "barcelona", "sala-apolo", sourceUrl, discrepancies);

        assertThat(result).isPresent();
        ScrapedVenue venue = result.get();
        assertThat(venue.id()).isEqualTo("barcelona-sala-apolo");
        assertThat(venue.name()).isEqualTo("Sala Apolo");
        assertThat(venue.city()).isEqualTo("Barcelona");
        assertThat(venue.province()).isEqualTo("Barcelona");
        assertThat(venue.lat()).isEqualTo(41.3735);
        assertThat(venue.lng()).isEqualTo(2.1700);
        assertThat(discrepancies).isEmpty();
    }

    @Test
    void parse_missingRequiredField_recordsParseError() throws IOException {
        String html = loadFixture("venue-detail-missing-name.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "barcelona", "unknown", "https://url", discrepancies);

        assertThat(result).isEmpty();
        assertThat(discrepancies).hasSize(1);
        assertThat(discrepancies.get(0).type()).isEqualTo(DiscrepancyType.PARSE_ERROR);
        assertThat(discrepancies.get(0).field()).isEqualTo("name");
    }

    @Test
    void parse_missingOptionalField_doesNotRecordDiscrepancy() throws IOException {
        String html = loadFixture("venue-detail-no-optionals.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "barcelona", "razzmatazz", "https://url", discrepancies);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isNull();
        assertThat(result.get().lat()).isNull();
        assertThat(discrepancies).isEmpty();
    }

    // 5.1 [RED] div.texto present → description extracted
    @Test
    void parse_htmlWithDivTexto_extractsDescription() {
        String html = """
            <html><body>
            <article><div class="box">
              <h2>Sala Test</h2>
              <div class="texto">Some venue description</div>
            </div></article>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "madrid", "sala-test", "https://url", discrepancies);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isEqualTo("Some venue description");
    }

    // 5.2 [RED] no div.texto → description is null
    @Test
    void parse_htmlWithoutDivTexto_descriptionIsNull() {
        String html = """
            <html><body>
            <article><div class="box">
              <h2>Sala Test</h2>
            </div></article>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "madrid", "sala-test", "https://url", discrepancies);

        assertThat(result).isPresent();
        assertThat(result.get().description()).isNull();
    }

    @Test
    void parse_noGoogleMaps_stay22ScriptPresent_ignoresLatLng() {
        String html = """
            <html><body>
            <article><div class="box">
              <h2>Sala Test</h2>
            </div></article>
            <script>
              var widget = new Stay22({lat: 40.4168, lng: -3.7038, color: '#000'});
            </script>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "madrid", "sala-test", "https://url", discrepancies);

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isNull();
        assertThat(result.get().lng()).isNull();
    }

    // 5.5 [RED] both sources absent → lat/lng null
    @Test
    void parse_neitherGoogleMapsNorStay22_latLngNull() {
        String html = """
            <html><body>
            <article><div class="box">
              <h2>Sala Test</h2>
            </div></article>
            <script>var x = 1;</script>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "madrid", "sala-test", "https://url", discrepancies);

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isNull();
        assertThat(result.get().lng()).isNull();
    }

    // Iframe q= IS extracted (no-space variant); Stay22 script values STAY ignored.
    @Test
    void parse_googleMapsIframe_extractsCoords_stay22StillIgnored() {
        String html = """
            <html><body>
            <article><div class="box">
              <h2>Sala Apolo</h2>
              <div class="map">
                <iframe src="https://maps.google.com/maps?q=41.3735,2.1700&t=&z=17&ie=UTF8&output=embed"></iframe>
              </div>
            </div></article>
            <script>
              var widget = new Stay22({lat: 99.9999, lng: 88.8888});
            </script>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "barcelona", "sala-apolo", "https://url", discrepancies);

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(41.3735);
        assertThat(result.get().lng()).isEqualTo(2.1700);
    }

    // Out-of-Spain iframe q= (Paris) -> rejected by ScrapedCoordinateValidator -> null
    @Test
    void parse_googleMapsIframe_outOfSpainCoordinates_areRejected() {
        String html = """
            <html><body>
            <article><div class="box">
              <h2>Sala Paris</h2>
              <div class="map">
                <iframe src="https://maps.google.com/maps?q=48.8566,2.3522&t=&z=17&ie=UTF8&output=embed"></iframe>
              </div>
            </div></article>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "paris", "sala-paris", "https://url", discrepancies);

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isNull();
        assertThat(result.get().lng()).isNull();
    }

    // Fallback selector: iframe[src*=maps.google] when not nested under div.map
    @Test
    void parse_googleMapsIframe_outsideDivMap_usesFallbackSelector() {
        String html = """
            <html><body>
            <article><div class="box">
              <h2>Sala Test</h2>
            </div></article>
            <iframe src="https://maps.google.com/maps?q=41.3735,2.1700&t=&z=17&ie=UTF8&output=embed"></iframe>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<ScrapedVenue> result = parser.parse(html, "barcelona", "sala-test", "https://url", discrepancies);

        assertThat(result).isPresent();
        assertThat(result.get().lat()).isEqualTo(41.3735);
        assertThat(result.get().lng()).isEqualTo(2.1700);
    }
}
