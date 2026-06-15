package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataPort;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.rubenazo.buscaConciertos.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalaConciertoUseCaseTest {

    @Mock
    private SalaConciertoRepositoryPort repo;

    @Mock
    private SyncMetadataPort syncMeta;

    private SalaConciertoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SalaConciertoUseCase(repo, syncMeta);
    }

    @Test
    void hasChangesShouldReturnFalseWhenSyncDisabled() {
        when(syncMeta.shouldSync("salas-concierto")).thenReturn(false);

        assertThat(useCase.hasChanges(OLD_TIMESTAMP)).isFalse();
        verify(syncMeta, never()).getLastModified(any());
    }

    @Test
    void hasChangesShouldReturnTrueWhenSyncEnabledAndSinceIsNull() {
        when(syncMeta.shouldSync("salas-concierto")).thenReturn(true);

        assertThat(useCase.hasChanges(null)).isTrue();
        verify(syncMeta, never()).getLastModified(any());
    }

    @Test
    void hasChangesShouldReturnTrueWhenSinceIsBeforeLastModified() {
        when(syncMeta.shouldSync("salas-concierto")).thenReturn(true);
        when(syncMeta.getLastModified("salas-concierto")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(OLD_TIMESTAMP)).isTrue();
    }

    @Test
    void hasChangesShouldReturnFalseWhenSinceIsAfterLastModified() {
        when(syncMeta.shouldSync("salas-concierto")).thenReturn(true);
        when(syncMeta.getLastModified("salas-concierto")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(FUTURE_TIMESTAMP)).isFalse();
    }

    @Test
    void hasChangesShouldReturnFalseWhenSinceEqualsLastModified() {
        when(syncMeta.shouldSync("salas-concierto")).thenReturn(true);
        when(syncMeta.getLastModified("salas-concierto")).thenReturn(LAST_MODIFIED);

        assertThat(useCase.hasChanges(LAST_MODIFIED)).isFalse();
    }

    @Test
    void getSalasShouldReturnAllWhenSinceIsNull() {
        List<SalaConcierto> allSalas = List.of(aSala());
        when(repo.findAll()).thenReturn(allSalas);
        when(syncMeta.getLastModified("salas-concierto")).thenReturn(LAST_MODIFIED);

        SyncResponse<SalaConcierto> response = useCase.getSalas(null);

        assertThat(response.data()).isEqualTo(allSalas);
        verify(repo).findAll();
        verify(repo, never()).findModifiedAfter(any());
    }

    @Test
    void getSalasShouldReturnModifiedWhenSinceProvided() {
        List<SalaConcierto> modified = List.of(aSala());
        when(repo.findModifiedAfter(OLD_TIMESTAMP)).thenReturn(modified);
        when(syncMeta.getLastModified("salas-concierto")).thenReturn(LAST_MODIFIED);

        SyncResponse<SalaConcierto> response = useCase.getSalas(OLD_TIMESTAMP);

        assertThat(response.data()).isEqualTo(modified);
        verify(repo).findModifiedAfter(OLD_TIMESTAMP);
        verify(repo, never()).findAll();
    }

    @Test
    void getSalasShouldSetTimestampFromSyncMetadata() {
        when(repo.findAll()).thenReturn(List.of());
        when(syncMeta.getLastModified("salas-concierto")).thenReturn(LAST_MODIFIED);

        SyncResponse<SalaConcierto> response = useCase.getSalas(null);

        assertThat(response.timestamp()).isEqualTo(LAST_MODIFIED);
    }

    @Test
    void getSalasShouldReturnEmptyListWhenNoSalasMatch() {
        when(repo.findModifiedAfter(FUTURE_TIMESTAMP)).thenReturn(List.of());
        when(syncMeta.getLastModified("salas-concierto")).thenReturn(LAST_MODIFIED);

        SyncResponse<SalaConcierto> response = useCase.getSalas(FUTURE_TIMESTAMP);

        assertThat(response.data()).isEmpty();
    }
}
