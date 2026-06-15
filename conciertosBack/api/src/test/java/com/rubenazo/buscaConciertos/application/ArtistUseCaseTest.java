package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.ArtistRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataPort;
import com.rubenazo.buscaConciertos.domain.Artist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static com.rubenazo.buscaConciertos.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistUseCaseTest {

    @Mock
    private ArtistRepositoryPort repo;

    @Mock
    private SyncMetadataPort syncMeta;

    private ArtistUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ArtistUseCase(repo, syncMeta);
    }

    @Test
    void hasChangesShouldReturnFalseWhenSyncDisabled() {
        when(syncMeta.shouldSync("artists")).thenReturn(false);

        assertThat(useCase.hasChanges(OLD_TIMESTAMP)).isFalse();
        verify(syncMeta, never()).getLastModified(any());
    }

    @Test
    void hasChangesShouldReturnTrueWhenSyncEnabledAndSinceIsNull() {
        when(syncMeta.shouldSync("artists")).thenReturn(true);

        assertThat(useCase.hasChanges(null)).isTrue();
        verify(syncMeta, never()).getLastModified(any());
    }

    @Test
    void hasChangesShouldReturnTrueWhenSinceIsBeforeLastModified() {
        when(syncMeta.shouldSync("artists")).thenReturn(true);
        when(syncMeta.getLastModified("artists")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(OLD_TIMESTAMP)).isTrue();
    }

    @Test
    void hasChangesShouldReturnFalseWhenSinceIsAfterLastModified() {
        when(syncMeta.shouldSync("artists")).thenReturn(true);
        when(syncMeta.getLastModified("artists")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(FUTURE_TIMESTAMP)).isFalse();
    }

    @Test
    void hasChangesShouldReturnFalseWhenSinceEqualsLastModified() {
        when(syncMeta.shouldSync("artists")).thenReturn(true);
        when(syncMeta.getLastModified("artists")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(LAST_MODIFIED)).isFalse();
    }

    @Test
    void getArtistsShouldReturnAllWhenSinceIsNull() {
        List<Artist> allArtists = List.of(anArtist());
        when(repo.findAll()).thenReturn(allArtists);
        when(syncMeta.getLastModified("artists")).thenReturn(LAST_MODIFIED);

        SyncResponse<Artist> response = useCase.getArtists(null);

        assertThat(response.data()).isEqualTo(allArtists);
        verify(repo).findAll();
        verify(repo, never()).findModifiedAfter(any());
    }

    @Test
    void getArtistsShouldReturnModifiedWhenSinceProvided() {
        List<Artist> modified = List.of(anArtist());
        when(repo.findModifiedAfter(OLD_TIMESTAMP)).thenReturn(modified);
        when(syncMeta.getLastModified("artists")).thenReturn(LAST_MODIFIED);

        SyncResponse<Artist> response = useCase.getArtists(OLD_TIMESTAMP);

        assertThat(response.data()).isEqualTo(modified);
        verify(repo).findModifiedAfter(OLD_TIMESTAMP);
        verify(repo, never()).findAll();
    }

    @Test
    void getArtistsShouldSetTimestampFromSyncMetadata() {
        when(repo.findAll()).thenReturn(List.of());
        when(syncMeta.getLastModified("artists")).thenReturn(LAST_MODIFIED);

        SyncResponse<Artist> response = useCase.getArtists(null);

        assertThat(response.timestamp()).isEqualTo(LAST_MODIFIED);
    }

    @Test
    void getArtistsShouldReturnEmptyListWhenNoArtistsMatch() {
        when(repo.findModifiedAfter(FUTURE_TIMESTAMP)).thenReturn(List.of());
        when(syncMeta.getLastModified("artists")).thenReturn(LAST_MODIFIED);

        SyncResponse<Artist> response = useCase.getArtists(FUTURE_TIMESTAMP);

        assertThat(response.data()).isEmpty();
    }
}
