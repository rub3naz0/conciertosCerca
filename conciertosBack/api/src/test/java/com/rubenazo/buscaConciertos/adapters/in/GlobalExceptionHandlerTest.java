package com.rubenazo.buscaConciertos.adapters.in;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDateTimeParseShouldReturn400WithErrorMessage() {
        DateTimeParseException ex = new DateTimeParseException("bad format", "not-a-date", 0);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleDateTimeParse(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @Test
    void handleGenericShouldReturn500WithErrorMessage() {
        RuntimeException ex = new RuntimeException("something broke");

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Internal server error");
        assertThat(response.getBody().status()).isEqualTo(500);
    }

    @Test
    void handleDateTimeParseShouldNotLeakExceptionDetails() {
        DateTimeParseException ex = new DateTimeParseException("secret internal detail", "bad-input", 0);

        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response = handler.handleDateTimeParse(ex);

        assertThat(response.getBody().error())
                .doesNotContain("secret internal detail")
                .contains("ISO 8601");
    }

    @Test
    void errorResponseShouldSerializeCorrectly() {
        GlobalExceptionHandler.ErrorResponse errorResponse = new GlobalExceptionHandler.ErrorResponse("test error", 418);

        assertThat(errorResponse.error()).isEqualTo("test error");
        assertThat(errorResponse.status()).isEqualTo(418);
    }
}
