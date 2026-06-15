package com.rubenazo.buscaConciertos.application;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinateValidatorTest {

    @Test
    void validate_bothNull_passes() {
        assertThatCode(() -> CoordinateValidator.validate(null, null)).doesNotThrowAnyException();
    }

    @Test
    void validate_validCoordinates_passes() {
        assertThatCode(() -> CoordinateValidator.validate(40.0, -3.7)).doesNotThrowAnyException();
    }

    @Test
    void validate_latOutOfRange_throwsBadRequest() {
        assertThatThrownBy(() -> CoordinateValidator.validate(999.0, -3.7))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void validate_latTooLow_throwsBadRequest() {
        assertThatThrownBy(() -> CoordinateValidator.validate(-91.0, 0.0))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void validate_lngOutOfRange_throwsBadRequest() {
        assertThatThrownBy(() -> CoordinateValidator.validate(40.0, 181.0))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void validate_latPresentLngNull_throwsBadRequest() {
        assertThatThrownBy(() -> CoordinateValidator.validate(40.0, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void validate_latNullLngPresent_throwsBadRequest() {
        assertThatThrownBy(() -> CoordinateValidator.validate(null, -3.7))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
