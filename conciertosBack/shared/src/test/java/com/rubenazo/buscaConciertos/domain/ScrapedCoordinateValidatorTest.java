package com.rubenazo.buscaConciertos.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapedCoordinateValidatorTest {

    @Test
    void barcelona_isValid() {
        assertThat(ScrapedCoordinateValidator.isValid(41.3735, 2.1700)).isTrue();
    }

    @Test
    void tenerife_isValid() {
        assertThat(ScrapedCoordinateValidator.isValid(28.46, -16.25)).isTrue();
    }

    @Test
    void palma_isValid() {
        assertThat(ScrapedCoordinateValidator.isValid(39.57, 2.65)).isTrue();
    }

    @Test
    void paris_isRejected_outsideSpainBoundingBox() {
        assertThat(ScrapedCoordinateValidator.isValid(48.8566, 2.3522)).isFalse();
    }

    @Test
    void nan_isRejected() {
        assertThat(ScrapedCoordinateValidator.isValid(Double.NaN, 2.1700)).isFalse();
        assertThat(ScrapedCoordinateValidator.isValid(41.3735, Double.NaN)).isFalse();
    }

    @Test
    void infinity_isRejected() {
        assertThat(ScrapedCoordinateValidator.isValid(Double.POSITIVE_INFINITY, 2.1700)).isFalse();
        assertThat(ScrapedCoordinateValidator.isValid(41.3735, Double.NEGATIVE_INFINITY)).isFalse();
    }

    @Test
    void latOutOfGlobalRange_isRejected() {
        assertThat(ScrapedCoordinateValidator.isValid(200.0, 2.1700)).isFalse();
    }

    @Test
    void elHierro_westmostLng_isValid() {
        // El Hierro westmost (~-18.16) is inside the Spain bounding box
        assertThat(ScrapedCoordinateValidator.isValid(27.7, -18.16)).isTrue();
    }

    @Test
    void lngJustOutsideBox_isRejected() {
        assertThat(ScrapedCoordinateValidator.isValid(41.3735, 4.6)).isFalse();
    }
}
