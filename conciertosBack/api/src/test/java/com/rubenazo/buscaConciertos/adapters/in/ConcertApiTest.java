package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.adapters.in.dto.ConcertSyncResponseDto;
import com.rubenazo.buscaConciertos.application.ConcertSyncResponse;
import com.rubenazo.buscaConciertos.application.ports.in.ConcertInputPort;
import com.rubenazo.buscaConciertos.domain.Concert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.format.DateTimeParseException;
import java.util.List;

import static com.rubenazo.buscaConciertos.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertApiTest {

    @Mock
    private ConcertInputPort inputPort;

    private ConcertApi api;

    @BeforeEach
    void setUp() {
        api = new ConcertApi(inputPort);
    }

    @Test
    void headShouldReturn200WhenChangesExist() {
        when(inputPort.hasChanges(null)).thenReturn(true);

        ResponseEntity<Void> response = api.head(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void headShouldReturn204WhenNoChanges() {
        when(inputPort.hasChanges(null)).thenReturn(false);

        ResponseEntity<Void> response = api.head(null);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void headShouldParseSinceAndDelegateToInputPort() {
        when(inputPort.hasChanges(OLD_TIMESTAMP)).thenReturn(true);

        ResponseEntity<Void> response = api.head("2026-05-17T00:00:00Z");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(inputPort).hasChanges(OLD_TIMESTAMP);
    }

    @Test
    void headShouldPassNullWhenSinceIsAbsent() {
        when(inputPort.hasChanges(null)).thenReturn(true);

        api.head(null);

        verify(inputPort).hasChanges(null);
    }

    @Test
    void headShouldThrowDateTimeParseExceptionForInvalidSince() {
        assertThatThrownBy(() -> api.head("not-a-date"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void getShouldReturnOkWithConcertSyncResponse() {
        List<Concert> concerts = List.of(aConcert());
        when(inputPort.getConcerts(null))
                .thenReturn(new ConcertSyncResponse(LAST_MODIFIED, concerts, List.of()));

        ResponseEntity<ConcertSyncResponseDto> response = api.get(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().timestamp()).isEqualTo("2026-05-20T15:30:00Z");
    }

    @Test
    void getShouldIncludeDeletedIds() {
        List<String> deletedIds = List.of("c10", "c20");
        when(inputPort.getConcerts(null))
                .thenReturn(new ConcertSyncResponse(LAST_MODIFIED, List.of(), deletedIds));

        ResponseEntity<ConcertSyncResponseDto> response = api.get(null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().deletedIds()).containsExactly("c10", "c20");
    }

    @Test
    void getShouldParseSinceAndDelegateToInputPort() {
        when(inputPort.getConcerts(OLD_TIMESTAMP))
                .thenReturn(new ConcertSyncResponse(LAST_MODIFIED, List.of(), List.of()));

        api.get("2026-05-17T00:00:00Z");

        verify(inputPort).getConcerts(OLD_TIMESTAMP);
    }

    @Test
    void getShouldPassNullWhenSinceIsAbsent() {
        when(inputPort.getConcerts(null))
                .thenReturn(new ConcertSyncResponse(LAST_MODIFIED, List.of(), List.of()));

        api.get(null);

        verify(inputPort).getConcerts(null);
    }

    @Test
    void getShouldReturnEmptyDataWhenNoConcerts() {
        when(inputPort.getConcerts(null))
                .thenReturn(new ConcertSyncResponse(LAST_MODIFIED, List.of(), List.of()));

        ResponseEntity<ConcertSyncResponseDto> response = api.get(null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEmpty();
        assertThat(response.getBody().deletedIds()).isEmpty();
    }

    @Test
    void getShouldMapDomainFieldsToDto() {
        Concert concert = aConcert();
        when(inputPort.getConcerts(null))
                .thenReturn(new ConcertSyncResponse(LAST_MODIFIED, List.of(concert), List.of()));

        ResponseEntity<ConcertSyncResponseDto> response = api.get(null);

        var dto = response.getBody().data().get(0);
        assertThat(dto.id()).isEqualTo(concert.id());
        assertThat(dto.salaConciertoId()).isEqualTo(concert.salaConciertoId());
        assertThat(dto.artistIds()).isEqualTo(concert.artistIds());
    }

    @Test
    void getShouldThrowDateTimeParseExceptionForInvalidSince() {
        assertThatThrownBy(() -> api.get("bad-timestamp"))
                .isInstanceOf(DateTimeParseException.class);
    }
}
