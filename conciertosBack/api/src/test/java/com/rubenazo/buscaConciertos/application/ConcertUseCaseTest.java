package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataPort;
import com.rubenazo.buscaConciertos.domain.Concert;
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
class ConcertUseCaseTest {

    @Mock
    private ConcertRepositoryPort repo;

    @Mock
    private SyncMetadataPort syncMeta;

    private ConcertUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConcertUseCase(repo, syncMeta);
    }

    @Test
    void hasChangesShouldReturnFalseWhenSyncDisabled() {
        when(syncMeta.shouldSync("concerts")).thenReturn(false);

        assertThat(useCase.hasChanges(OLD_TIMESTAMP)).isFalse();
        verify(syncMeta, never()).getLastModified(any());
    }

    @Test
    void hasChangesShouldReturnTrueWhenSyncEnabledAndSinceIsNull() {
        when(syncMeta.shouldSync("concerts")).thenReturn(true);

        assertThat(useCase.hasChanges(null)).isTrue();
        verify(syncMeta, never()).getLastModified(any());
    }

    @Test
    void hasChangesShouldReturnTrueWhenSinceIsBeforeLastModified() {
        when(syncMeta.shouldSync("concerts")).thenReturn(true);
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(OLD_TIMESTAMP)).isTrue();
    }

    @Test
    void hasChangesShouldReturnFalseWhenSinceIsAfterLastModified() {
        when(syncMeta.shouldSync("concerts")).thenReturn(true);
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(FUTURE_TIMESTAMP)).isFalse();
    }

    @Test
    void hasChangesShouldReturnFalseWhenSinceEqualsLastModified() {
        when(syncMeta.shouldSync("concerts")).thenReturn(true);
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(LAST_MODIFIED)).isFalse();
    }

    @Test
    void getConcertsShouldReturnAllWhenSinceIsNull() {
        List<Concert> allConcerts = List.of(aConcert());
        when(repo.findAll()).thenReturn(allConcerts);
        when(repo.getDeletedIds(null)).thenReturn(List.of("c99"));
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        ConcertSyncResponse response = useCase.getConcerts(null);

        assertThat(response.data()).isEqualTo(allConcerts);
        verify(repo).findAll();
        verify(repo, never()).findModifiedAfter(any());
    }

    @Test
    void getConcertsShouldReturnModifiedWhenSinceProvided() {
        List<Concert> modified = List.of(aConcert());
        when(repo.findModifiedAfter(OLD_TIMESTAMP)).thenReturn(modified);
        when(repo.getDeletedIds(OLD_TIMESTAMP)).thenReturn(List.of());
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        ConcertSyncResponse response = useCase.getConcerts(OLD_TIMESTAMP);

        assertThat(response.data()).isEqualTo(modified);
        verify(repo).findModifiedAfter(OLD_TIMESTAMP);
        verify(repo, never()).findAll();
    }

    @Test
    void getConcertsShouldIncludeDeletedIds() {
        List<String> deletedIds = List.of("c10", "c20");
        when(repo.findAll()).thenReturn(List.of());
        when(repo.getDeletedIds(null)).thenReturn(deletedIds);
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        ConcertSyncResponse response = useCase.getConcerts(null);

        assertThat(response.deletedIds()).containsExactly("c10", "c20");
    }

    @Test
    void getConcertsShouldSetTimestampFromSyncMetadata() {
        when(repo.findAll()).thenReturn(List.of());
        when(repo.getDeletedIds(null)).thenReturn(List.of());
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        ConcertSyncResponse response = useCase.getConcerts(null);

        assertThat(response.timestamp()).isEqualTo(LAST_MODIFIED);
    }

    @Test
    void getConcertsShouldPassSinceToGetDeletedIds() {
        when(repo.findModifiedAfter(OLD_TIMESTAMP)).thenReturn(List.of());
        when(repo.getDeletedIds(OLD_TIMESTAMP)).thenReturn(List.of("c-old-deleted"));
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        ConcertSyncResponse response = useCase.getConcerts(OLD_TIMESTAMP);

        verify(repo).getDeletedIds(OLD_TIMESTAMP);
        assertThat(response.deletedIds()).containsExactly("c-old-deleted");
    }

    @Test
    void getConcertsShouldPassNullSinceToGetDeletedIdsOnFirstSync() {
        when(repo.findAll()).thenReturn(List.of());
        when(repo.getDeletedIds(null)).thenReturn(List.of("c-hist"));
        when(syncMeta.getLastModified("concerts")).thenReturn(LAST_MODIFIED);

        ConcertSyncResponse response = useCase.getConcerts(null);

        verify(repo).getDeletedIds(null);
        assertThat(response.deletedIds()).containsExactly("c-hist");
    }
}
