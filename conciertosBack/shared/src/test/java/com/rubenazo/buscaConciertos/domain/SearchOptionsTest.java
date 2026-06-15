package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchOptionsTest {

    @Test
    void defaults_returnsBasicSearchDepthAndFiveResults() {
        SearchOptions options = SearchOptions.defaults();

        assertThat(options.searchDepth()).isEqualTo("basic");
        assertThat(options.maxResults()).isEqualTo(5);
    }

    @Test
    void shouldCreateSearchOptionsWithCustomValues() {
        SearchOptions options = new SearchOptions("advanced", 10);

        assertThat(options.searchDepth()).isEqualTo("advanced");
        assertThat(options.maxResults()).isEqualTo(10);
    }

    @Test
    void shouldBeEqualForIdenticalValues() {
        SearchOptions a = new SearchOptions("basic", 5);
        SearchOptions b = new SearchOptions("basic", 5);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
