package com.rubenazo.buscaConciertos.scraper.application.parsers;

import com.rubenazo.buscaConciertos.scraper.domain.ArtistDetail;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArtistDetailParserTest {

    private ArtistDetailParser parser;

    @BeforeEach
    void setUp() {
        parser = new ArtistDetailParser();
    }

    private String loadFixture(String name) throws IOException {
        URL resource = getClass().getClassLoader().getResource("html/" + name);
        return new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void parseDescription_validPage_returnsDescription() throws IOException {
        String html = loadFixture("artist-detail-happy.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<String> result = parser.parseDescription(html, discrepancies);

        assertThat(result).isPresent();
        String description = result.get();
        assertThat(description).doesNotContain("YouTube");
        assertThat(description).doesNotContain("youtube.com");
        assertThat(description).contains("Vetusta Morla es una banda de indie rock");
        assertThat(description).contains("VETUSTA MORLA");
        assertThat(discrepancies).isEmpty();
    }

    @Test
    void parseDescription_noOptionals_returnsEmpty() throws IOException {
        String html = loadFixture("artist-detail-no-optionals.html");
        List<Discrepancy> discrepancies = new ArrayList<>();

        Optional<String> result = parser.parseDescription(html, discrepancies);

        assertThat(result).isEmpty();
        assertThat(discrepancies).isEmpty();
    }

    // 4.2 [RED] parse() extracts img[itemprop="image"] src as imageUrl
    @Test
    void parse_htmlWithItempropImage_extractsImageUrl() {
        String html = """
            <html><body>
            <img itemprop="image" src="/doc/a/2023/a_russianred.jpg" />
            <div class="texto">
            <h3>Russian Red</h3>
            </div>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        ArtistDetail result = parser.parse(html, discrepancies);

        assertThat(result.imageUrl()).isEqualTo("https://conciertos.club/doc/a/2023/a_russianred.jpg");
    }

    // 4.3 [RED] absent itemprop image → imageUrl is null
    @Test
    void parse_htmlWithoutItempropImage_imageUrlIsNull() {
        String html = """
            <html><body>
            <div class="texto">
            <h3>Some Artist</h3>
            <p>A description paragraph.</p>
            </div>
            </body></html>
            """;
        List<Discrepancy> discrepancies = new ArrayList<>();

        ArtistDetail result = parser.parse(html, discrepancies);

        assertThat(result.imageUrl()).isNull();
    }
}
