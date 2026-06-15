package com.rubenazo.buscaConciertos.scraper.application.parsers;

import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VenueListParserTest {

    private VenueListParser parser;

    @BeforeEach
    void setUp() {
        parser = new VenueListParser();
    }

    private String loadFixture(String name) throws IOException {
        URL resource = getClass().getClassLoader().getResource("html/" + name);
        return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void parse_happyPath_returnsVenueUrls() throws IOException {
        String html = loadFixture("venue-list-happy.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<String> urls = parser.parse(html, "barcelona", discrepancies);

        assertThat(urls).hasSize(2);
        assertThat(urls).contains("/barcelona/locales/sala-apolo", "/barcelona/locales/razzmatazz");
        assertThat(discrepancies).isEmpty();
    }

    @Test
    void parse_emptyListing_returnsEmptyList() throws IOException {
        String html = loadFixture("venue-list-empty.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<String> urls = parser.parse(html, "province", discrepancies);

        assertThat(urls).isEmpty();
        assertThat(discrepancies).isEmpty();
    }

    @Test
    void parse_malformedHtml_recordsSchemaChangeDiscrepancy() throws IOException {
        String html = loadFixture("venue-list-malformed.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<String> urls = parser.parse(html, "barcelona", discrepancies);

        assertThat(urls).isEmpty();
        assertThat(discrepancies).hasSize(1);
        assertThat(discrepancies.get(0).type()).isEqualTo(DiscrepancyType.SCHEMA_CHANGE);
    }
}
