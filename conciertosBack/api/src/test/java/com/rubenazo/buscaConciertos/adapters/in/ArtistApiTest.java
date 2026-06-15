package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.adapters.in.dto.ArtistDto;
import com.rubenazo.buscaConciertos.adapters.in.dto.SyncResponseDto;
import com.rubenazo.buscaConciertos.application.SyncResponse;
import com.rubenazo.buscaConciertos.application.ports.in.ArtistInputPort;
import com.rubenazo.buscaConciertos.domain.Artist;
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
class ArtistApiTest {

    @Mock
    private ArtistInputPort inputPort;

    private ArtistApi api;

    @BeforeEach
    void setUp() {
        api = new ArtistApi(inputPort);
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
    void getShouldReturnOkWithSyncResponse() {
        List<Artist> artists = List.of(anArtist());
        when(inputPort.getArtists(null)).thenReturn(new SyncResponse<>(LAST_MODIFIED, artists));

        ResponseEntity<SyncResponseDto<ArtistDto>> response = api.get(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().timestamp()).isEqualTo("2026-05-20T15:30:00Z");
    }

    @Test
    void getShouldParseSinceAndDelegateToInputPort() {
        when(inputPort.getArtists(OLD_TIMESTAMP)).thenReturn(new SyncResponse<>(LAST_MODIFIED, List.of()));

        api.get("2026-05-17T00:00:00Z");

        verify(inputPort).getArtists(OLD_TIMESTAMP);
    }

    @Test
    void getShouldPassNullWhenSinceIsAbsent() {
        when(inputPort.getArtists(null)).thenReturn(new SyncResponse<>(LAST_MODIFIED, List.of()));

        api.get(null);

        verify(inputPort).getArtists(null);
    }

    @Test
    void getShouldReturnEmptyDataWhenNoArtists() {
        when(inputPort.getArtists(null)).thenReturn(new SyncResponse<>(LAST_MODIFIED, List.of()));

        ResponseEntity<SyncResponseDto<ArtistDto>> response = api.get(null);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEmpty();
    }

    @Test
    void getShouldMapDomainFieldsToDto() {
        Artist artist = anArtist();
        when(inputPort.getArtists(null)).thenReturn(new SyncResponse<>(LAST_MODIFIED, List.of(artist)));

        ResponseEntity<SyncResponseDto<ArtistDto>> response = api.get(null);

        ArtistDto dto = response.getBody().data().get(0);
        assertThat(dto.id()).isEqualTo(artist.id());
        assertThat(dto.name()).isEqualTo(artist.name());
        assertThat(dto.genre()).isEqualTo(artist.genre());
    }

    @Test
    void getShouldThrowDateTimeParseExceptionForInvalidSince() {
        assertThatThrownBy(() -> api.get("bad-timestamp"))
                .isInstanceOf(DateTimeParseException.class);
    }
}
