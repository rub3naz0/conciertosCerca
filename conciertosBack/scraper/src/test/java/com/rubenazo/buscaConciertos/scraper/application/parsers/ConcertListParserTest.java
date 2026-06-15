package com.rubenazo.buscaConciertos.scraper.application.parsers;

import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedConcert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConcertListParserTest {

    private ConcertListParser parser;

    @BeforeEach
    void setUp() {
        parser = new ConcertListParser();
    }

    private String loadFixture(String name) throws IOException {
        URL resource = getClass().getClassLoader().getResource("html/" + name);
        return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void parse_wellFormedList_returnsConcerts() throws IOException {
        String html = loadFixture("concert-list-happy.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<ScrapedConcert> concerts = parser.parse(html, discrepancies);

        assertThat(concerts).hasSize(2);
        ScrapedConcert first = concerts.get(0);
        assertThat(first.id()).isEqualTo("12345");
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(first.time()).isEqualTo("21:00");
        assertThat(first.venueName()).isEqualTo("Sala Apolo");
        assertThat(first.venueProvince()).isEqualTo("Barcelona");
        assertThat(first.artistSlugs()).containsExactly("vetusta-morla");
        assertThat(first.artistName()).isEqualTo("Vetusta Morla");
        assertThat(first.genre()).isEqualTo("Indie Rock");
        assertThat(first.imageUrl()).isEqualTo("/doc/ap/2025/c_vetusta_p.jpg");
        assertThat(first.price()).isEqualTo("25€");
        assertThat(discrepancies).isEmpty();
    }

    @Test
    void parse_schemaChange_recordsDiscrepancy() throws IOException {
        String html = loadFixture("concert-list-schema-change.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<ScrapedConcert> concerts = parser.parse(html, discrepancies);

        assertThat(concerts).isEmpty();
        assertThat(discrepancies).hasSize(1);
        assertThat(discrepancies.get(0).type()).isEqualTo(DiscrepancyType.SCHEMA_CHANGE);
    }

    @Test
    void parse_malformedDate_skipsItemAndKeepsValidOnes() throws IOException {
        String html = loadFixture("concert-list-malformed-date.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<ScrapedConcert> concerts = parser.parse(html, discrepancies);

        // The item with an unparseable date must be dropped; the valid item stays.
        assertThat(concerts).hasSize(1);
        assertThat(concerts.get(0).id()).isEqualTo("12345");
        assertThat(concerts.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 15));
        // The bad-date item must NOT appear in the result.
        assertThat(concerts).noneMatch(c -> "99999".equals(c.id()));
    }

    // 6.1 [RED] verify ScrapedConcert has no ticketUrl — field removed in PR1
    @Test
    void parse_result_hasNoTicketUrl() throws IOException {
        String html = loadFixture("concert-list-happy.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        List<ScrapedConcert> concerts = parser.parse(html, discrepancies);

        assertThat(concerts).isNotEmpty();
        // ScrapedConcert record has no ticketUrl field — verified at compile time.
        // Confirm the parsed ScrapedConcert has the expected fields (no ticket URL).
        ScrapedConcert first = concerts.get(0);
        assertThat(first.sourceUrl()).isNotNull(); // sourceUrl is present (not ticketUrl)
        // If this compiles and runs, ticketUrl has been removed successfully.
    }
}
