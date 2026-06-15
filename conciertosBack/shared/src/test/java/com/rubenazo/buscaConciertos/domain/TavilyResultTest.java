package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TavilyResultTest {

    @Test
    void shouldCreateTavilyResultWithAllFields() {
        TavilyResult result = new TavilyResult(
            "Vetusta Morla - Official Site",
            "https://vetusta.com",
            "Official website of the Spanish indie rock band Vetusta Morla.",
            0.87
        );

        assertThat(result.title()).isEqualTo("Vetusta Morla - Official Site");
        assertThat(result.url()).isEqualTo("https://vetusta.com");
        assertThat(result.content()).isEqualTo("Official website of the Spanish indie rock band Vetusta Morla.");
        assertThat(result.score()).isEqualTo(0.87);
    }

    @Test
    void shouldSupportLowScoreResult() {
        TavilyResult result = new TavilyResult("Some Page", "https://example.com", "Some content", 0.42);

        assertThat(result.score()).isEqualTo(0.42);
    }

    @Test
    void shouldBeEqualForIdenticalValues() {
        TavilyResult a = new TavilyResult("Title", "https://url.com", "Content", 0.9);
        TavilyResult b = new TavilyResult("Title", "https://url.com", "Content", 0.9);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenScoreDiffers() {
        TavilyResult a = new TavilyResult("Title", "https://url.com", "Content", 0.9);
        TavilyResult b = new TavilyResult("Title", "https://url.com", "Content", 0.5);

        assertThat(a).isNotEqualTo(b);
    }
}
