package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldSeverityTest {

    @Test
    void salaAddress_isSevere() {
        assertThat(FieldSeverity.of("sala", "address")).isEqualTo(FieldSeverity.SEVERE);
    }

    @Test
    void salaImageUrl_isNonSevere() {
        assertThat(FieldSeverity.of("sala", "image_url")).isEqualTo(FieldSeverity.NON_SEVERE);
    }

    @Test
    void artistGenre_isSevere() {
        assertThat(FieldSeverity.of("artist", "genre")).isEqualTo(FieldSeverity.SEVERE);
    }

    @Test
    void unknownEntityAndField_isNonSevere() {
        assertThat(FieldSeverity.of("unknown", "anything")).isEqualTo(FieldSeverity.NON_SEVERE);
    }

    @Test
    void toDbValue_severeReturnsSevereString() {
        assertThat(FieldSeverity.SEVERE.toDbValue()).isEqualTo("severe");
    }

    @Test
    void toDbValue_nonSevereReturnsNonSevereString() {
        assertThat(FieldSeverity.NON_SEVERE.toDbValue()).isEqualTo("non_severe");
    }

    @Test
    void salaLat_isSevere() {
        assertThat(FieldSeverity.of("sala", "lat")).isEqualTo(FieldSeverity.SEVERE);
    }

    @Test
    void salaLng_isSevere() {
        assertThat(FieldSeverity.of("sala", "lng")).isEqualTo(FieldSeverity.SEVERE);
    }

    @Test
    void artistImageUrl_isNonSevere() {
        assertThat(FieldSeverity.of("artist", "image_url")).isEqualTo(FieldSeverity.NON_SEVERE);
    }

    @Test
    void artistName_isSevere() {
        assertThat(FieldSeverity.of("artist", "name")).isEqualTo(FieldSeverity.SEVERE);
    }

    @Test
    void concertDate_isSevere() {
        assertThat(FieldSeverity.of("concert", "date")).isEqualTo(FieldSeverity.SEVERE);
    }

    @Test
    void concertTime_isNonSevere() {
        assertThat(FieldSeverity.of("concert", "time")).isEqualTo(FieldSeverity.NON_SEVERE);
    }
}
