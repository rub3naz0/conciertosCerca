package com.rubenazo.buscaConciertos.scraper.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void slugify_lowercasesInput() {
        assertThat(SlugUtils.slugify("Barcelona")).isEqualTo("barcelona");
    }

    @Test
    void slugify_stripsDiacritics() {
        assertThat(SlugUtils.slugify("Mäneskin")).isEqualTo("maneskin");
        assertThat(SlugUtils.slugify("Björk")).isEqualTo("bjork");
        assertThat(SlugUtils.slugify("Sigur Rós")).isEqualTo("sigur-ros");
    }

    @Test
    void slugify_replacesSpacesWithHyphens() {
        assertThat(SlugUtils.slugify("Sala Apolo")).isEqualTo("sala-apolo");
    }

    @Test
    void slugify_collapseConsecutiveHyphens() {
        assertThat(SlugUtils.slugify("Sala  Apolo")).isEqualTo("sala-apolo");
        assertThat(SlugUtils.slugify("Sala---Apolo")).isEqualTo("sala-apolo");
    }

    @Test
    void slugify_stripsPunctuation() {
        assertThat(SlugUtils.slugify("Sala (Apolo)")).isEqualTo("sala-apolo");
        assertThat(SlugUtils.slugify("Rock & Roll")).isEqualTo("rock-roll");
    }

    @Test
    void slugify_trimsBoundaryHyphens() {
        assertThat(SlugUtils.slugify("-sala-")).isEqualTo("sala");
        assertThat(SlugUtils.slugify("(test)")).isEqualTo("test");
    }

    @Test
    void venueId_combinesProvinceAndVenueSlug() {
        assertThat(SlugUtils.venueId("Barcelona", "Sala Apolo"))
            .isEqualTo("barcelona-sala-apolo");
    }

    @ParameterizedTest
    @CsvSource({
        "barcelona, sala-apolo, barcelona-sala-apolo",
        "Madrid, El Sol, madrid-el-sol",
        "País Vasco, Bilborock, pais-vasco-bilborock"
    })
    void venueId_derivedDeterministically(String province, String name, String expected) {
        assertThat(SlugUtils.venueId(province, name)).isEqualTo(expected);
    }
}
