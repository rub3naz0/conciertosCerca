package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.ArtistRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualCrudUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-09T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock private ConcertWritePort concertWritePort;
    @Mock private SalaConciertoWritePort salaWritePort;
    @Mock private ArtistWritePort artistWritePort;
    @Mock private SalaConciertoRepositoryPort salaRepo;
    @Mock private ArtistRepositoryPort artistRepo;
    @Mock private ConcertRepositoryPort concertRepo;
    @Mock private VenueGeocodingUseCase venueGeocoding;
    @Mock private SyncMetadataWritePort syncMeta;
    @Mock private DataQualityWritePort dataQualityWritePort;
    @Mock private PlatformTransactionManager txManager;

    private ManualCrudUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        useCase = new ManualCrudUseCase(
            concertWritePort, salaWritePort, artistWritePort,
            salaRepo, artistRepo, concertRepo, venueGeocoding, syncMeta, dataQualityWritePort,
            new TransactionTemplate(txManager), FIXED_CLOCK
        );
    }

    // ── deleteConcert ─────────────────────────────────────────────────────────

    @Test
    void deleteConcert_404WhenNotFound() {
        when(concertWritePort.markDeleted("c-missing")).thenReturn(0);

        assertThatThrownBy(() -> useCase.deleteConcert("c-missing"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(syncMeta, never()).updateLastModified(any(), any());
    }

    @Test
    void deleteConcert_204WhenActiveRow() {
        when(concertWritePort.markDeleted("c1")).thenReturn(1);

        useCase.deleteConcert("c1");

        verify(syncMeta).updateLastModified("concerts", FIXED_NOW);
    }

    @Test
    void deleteConcert_204WhenAlreadyDeleted_idempotent() {
        // D3: SQLite returns match-count 1 even for already-deleted rows
        when(concertWritePort.markDeleted("c-deleted")).thenReturn(1);

        useCase.deleteConcert("c-deleted");

        verify(syncMeta).updateLastModified("concerts", FIXED_NOW);
    }

    // ── createSala ────────────────────────────────────────────────────────────

    @Test
    void createSala_400OnBlankName() {
        var cmd = new CreateSalaCommand("", "Calle 1", "Madrid", "Madrid", null, null, null, null);

        assertThatThrownBy(() -> useCase.createSala(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createSala_400OnNullAddress() {
        var cmd = new CreateSalaCommand("Sala X", null, "Madrid", "Madrid", null, null, null, null);

        assertThatThrownBy(() -> useCase.createSala(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createSala_400OnNullCity() {
        var cmd = new CreateSalaCommand("Sala X", "Calle 1", null, "Madrid", null, null, null, null);

        assertThatThrownBy(() -> useCase.createSala(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createSala_400OnNullProvince() {
        var cmd = new CreateSalaCommand("Sala X", "Calle 1", "Madrid", null, null, null, null, null);

        assertThatThrownBy(() -> useCase.createSala(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createSala_400OnInvalidCoordinateRange() {
        var cmd = new CreateSalaCommand("Sala X", "Calle 1", "Madrid", "Madrid", 999.0, -3.7, null, null);

        assertThatThrownBy(() -> useCase.createSala(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createSala_happyPathWithExplicitCoords() {
        var cmd = new CreateSalaCommand("Sala Roca", "Calle Roca 1", "Madrid", "Madrid", 40.0, -3.7, null, null);

        SalaConcierto result = useCase.createSala(cmd);

        assertThat(result.id()).startsWith("manual-sala-");
        verify(salaWritePort).upsert(any(SalaConcierto.class));
        verify(syncMeta).updateLastModified("salas-concierto", FIXED_NOW);
        verify(venueGeocoding, never()).geocodeIfNeeded(any(), any());
    }

    @Test
    void createSala_geocodeOnMissingCoords() {
        var cmd = new CreateSalaCommand("Sala Roca", "Calle Roca 1", "Madrid", "Madrid", null, null, null, null);
        SalaConcierto geocodedSala = new SalaConcierto(
            "manual-sala-sala-roca-madrid", "Sala Roca", "Calle Roca 1", "Madrid", "Madrid",
            40.0, -3.7, null, null, "manual", FIXED_NOW
        );
        when(venueGeocoding.geocodeIfNeeded(any(SalaConcierto.class), eq(FIXED_NOW))).thenReturn(geocodedSala);

        SalaConcierto result = useCase.createSala(cmd);

        verify(venueGeocoding).geocodeIfNeeded(any(SalaConcierto.class), eq(FIXED_NOW));
        ArgumentCaptor<SalaConcierto> captor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWritePort).upsert(captor.capture());
        assertThat(captor.getValue().lat()).isEqualTo(40.0);
    }

    @Test
    void createSala_409WhenSalaAlreadyExists() {
        var cmd = new CreateSalaCommand("Sala Roca", "Calle Roca 1", "Madrid", "Madrid", 40.0, -3.7, null, null);
        when(salaRepo.findByIdIncludingBlocked("manual-sala-sala-roca-madrid"))
            .thenReturn(Optional.of(buildSala("manual-sala-sala-roca-madrid")));

        assertThatThrownBy(() -> useCase.createSala(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).contains("manual-sala-sala-roca-madrid");
            });

        verify(salaWritePort, never()).upsert(any());
        verify(syncMeta, never()).updateLastModified(any(), any());
    }

    @Test
    void createSala_writesInsideTransaction() {
        var cmd = new CreateSalaCommand("Sala Roca", "Calle Roca 1", "Madrid", "Madrid", 40.0, -3.7, null, null);

        useCase.createSala(cmd);

        verify(txManager).commit(any());
    }

    @Test
    void createSala_geocodeFailsSalaStillPersisted() {
        var cmd = new CreateSalaCommand("Sala Roca", "Calle Roca 1", "Madrid", "Madrid", null, null, null, null);
        // geocoding returns sala without coords (no confident match)
        when(venueGeocoding.geocodeIfNeeded(any(SalaConcierto.class), eq(FIXED_NOW)))
            .thenAnswer(inv -> inv.getArgument(0));

        SalaConcierto result = useCase.createSala(cmd);

        verify(salaWritePort).upsert(any(SalaConcierto.class));
        assertThat(result).isNotNull();
    }

    // ── createArtist ──────────────────────────────────────────────────────────

    @Test
    void createArtist_400OnBlankName() {
        var cmd = new CreateArtistCommand("", null, null, null, null);

        assertThatThrownBy(() -> useCase.createArtist(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createArtist_happyPath() {
        var cmd = new CreateArtistCommand("Artista X", "Rock", null, null, null);

        Artist result = useCase.createArtist(cmd);

        assertThat(result.id()).startsWith("manual-artist-");
        verify(artistWritePort).upsert(any(Artist.class));
        verify(syncMeta).updateLastModified("artists", FIXED_NOW);
    }

    @Test
    void createArtist_409WhenArtistAlreadyExists() {
        var cmd = new CreateArtistCommand("Artista X", "Rock", null, null, null);
        when(artistRepo.existsById("manual-artist-artista-x")).thenReturn(true);

        assertThatThrownBy(() -> useCase.createArtist(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).contains("manual-artist-artista-x");
            });

        verify(artistWritePort, never()).upsert(any());
        verify(syncMeta, never()).updateLastModified(any(), any());
    }

    // ── createConcert ─────────────────────────────────────────────────────────

    @Test
    void createConcert_400OnEmptyArtistIds() {
        var cmd = new CreateConcertCommand("sala1", List.of(), "2026-08-01", null, null);

        assertThatThrownBy(() -> useCase.createConcert(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createConcert_400OnBlankSalaConciertoId() {
        var cmd = new CreateConcertCommand("", List.of("a1"), "2026-08-01", null, null);

        assertThatThrownBy(() -> useCase.createConcert(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createConcert_400OnBlankDate() {
        var cmd = new CreateConcertCommand("sala1", List.of("a1"), "", null, null);

        assertThatThrownBy(() -> useCase.createConcert(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createConcert_422WhenSalaNotFound() {
        var cmd = new CreateConcertCommand("S_MISSING", List.of("a1"), "2026-08-01", null, null);
        when(salaRepo.findByIdIncludingBlocked("S_MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.createConcert(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).contains("S_MISSING");
            });
    }

    @Test
    void createConcert_422WhenArtistNotFound() {
        var sala = buildSala("sala1");
        var cmd = new CreateConcertCommand("sala1", List.of("A_MISSING"), "2026-08-01", null, null);
        when(salaRepo.findByIdIncludingBlocked("sala1")).thenReturn(Optional.of(sala));
        when(artistRepo.existsById("A_MISSING")).thenReturn(false);

        assertThatThrownBy(() -> useCase.createConcert(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).contains("A_MISSING");
            });
    }

    @Test
    void createConcert_happyPath() {
        var sala = buildSala("sala1");
        var cmd = new CreateConcertCommand("sala1", List.of("a1"), "2026-08-01", "21:00", "15€");
        when(salaRepo.findByIdIncludingBlocked("sala1")).thenReturn(Optional.of(sala));
        when(artistRepo.existsById("a1")).thenReturn(true);

        Concert result = useCase.createConcert(cmd);

        assertThat(result.id()).startsWith("manual-");
        verify(concertWritePort).upsert(any(Concert.class));
        verify(syncMeta).updateLastModified("concerts", FIXED_NOW);
    }

    @Test
    void createConcert_409WhenActiveConcertExists() {
        var sala = buildSala("sala1");
        var cmd = new CreateConcertCommand("sala1", List.of("a1"), "2026-08-01", null, null);
        when(salaRepo.findByIdIncludingBlocked("sala1")).thenReturn(Optional.of(sala));
        when(artistRepo.existsById("a1")).thenReturn(true);
        when(concertRepo.existsActiveById("manual-sala1-2026-08-01")).thenReturn(true);

        assertThatThrownBy(() -> useCase.createConcert(cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(rse.getReason()).contains("manual-sala1-2026-08-01");
            });

        verify(concertWritePort, never()).upsert(any());
        verify(syncMeta, never()).updateLastModified(any(), any());
    }

    @Test
    void createConcert_allowsRecreatingDeletedConcert() {
        // a manually deleted concert (deleted=1) is not "active" — re-creating revives it
        var sala = buildSala("sala1");
        var cmd = new CreateConcertCommand("sala1", List.of("a1"), "2026-08-01", null, null);
        when(salaRepo.findByIdIncludingBlocked("sala1")).thenReturn(Optional.of(sala));
        when(artistRepo.existsById("a1")).thenReturn(true);
        when(concertRepo.existsActiveById("manual-sala1-2026-08-01")).thenReturn(false);

        Concert result = useCase.createConcert(cmd);

        assertThat(result.id()).isEqualTo("manual-sala1-2026-08-01");
        verify(concertWritePort).upsert(any(Concert.class));
    }

    @Test
    void createConcert_writesInsideTransaction() {
        var sala = buildSala("sala1");
        var cmd = new CreateConcertCommand("sala1", List.of("a1"), "2026-08-01", null, null);
        when(salaRepo.findByIdIncludingBlocked("sala1")).thenReturn(Optional.of(sala));
        when(artistRepo.existsById("a1")).thenReturn(true);

        useCase.createConcert(cmd);

        verify(txManager).commit(any());
    }

    @Test
    void deleteConcert_writesInsideTransaction() {
        when(concertWritePort.markDeleted("c1")).thenReturn(1);

        useCase.deleteConcert("c1");

        verify(txManager).commit(any());
    }

    @Test
    void createConcert_deterministicId() {
        var sala = buildSala("sala1");
        var cmd = new CreateConcertCommand("sala1", List.of("a1"), "2026-08-01", null, null);
        when(salaRepo.findByIdIncludingBlocked("sala1")).thenReturn(Optional.of(sala));
        when(artistRepo.existsById("a1")).thenReturn(true);

        Concert first = useCase.createConcert(cmd);
        Concert second = useCase.createConcert(cmd);

        assertThat(first.id()).isEqualTo(second.id());
    }

    // ── updateSala ────────────────────────────────────────────────────────────

    @Test
    void updateSala_404WhenMissing() {
        when(salaRepo.findByIdIncludingBlocked("sala-missing")).thenReturn(Optional.empty());
        var cmd = new UpdateSalaCommand("New Name", "New Address", "New City", "New Province",
            40.0, -3.7, null, "New description", null);

        assertThatThrownBy(() -> useCase.updateSala("sala-missing", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(salaWritePort, never()).updateAll(any());
    }

    @Test
    void updateSala_400OnBlankName() {
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(buildSala("sala-1")));
        var cmd = new UpdateSalaCommand("", "New Address", "New City", "New Province",
            40.0, -3.7, null, "New description", null);

        assertThatThrownBy(() -> useCase.updateSala("sala-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(salaWritePort, never()).updateAll(any());
    }

    @Test
    void updateSala_400OnBlankCity() {
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(buildSala("sala-1")));
        var cmd = new UpdateSalaCommand("New Name", "New Address", "", "New Province",
            40.0, -3.7, null, "New description", null);

        assertThatThrownBy(() -> useCase.updateSala("sala-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(salaWritePort, never()).updateAll(any());
    }

    @Test
    void updateSala_400OnBlankProvince() {
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(buildSala("sala-1")));
        var cmd = new UpdateSalaCommand("New Name", "New Address", "New City", null,
            40.0, -3.7, null, "New description", null);

        assertThatThrownBy(() -> useCase.updateSala("sala-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(salaWritePort, never()).updateAll(any());
    }

    @Test
    void updateSala_400OnInvalidCoordinateRange() {
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(buildSala("sala-1")));
        var cmd = new UpdateSalaCommand("New Name", "New Address", "New City", "New Province",
            999.0, -3.7, null, "New description", null);

        assertThatThrownBy(() -> useCase.updateSala("sala-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(salaWritePort, never()).updateAll(any());
    }

    @Test
    void updateSala_400OnPartialCoordinates() {
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(buildSala("sala-1")));
        var cmd = new UpdateSalaCommand("New Name", "New Address", "New City", "New Province",
            40.0, null, null, "New description", null);

        assertThatThrownBy(() -> useCase.updateSala("sala-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(salaWritePort, never()).updateAll(any());
    }

    @Test
    void updateSala_400WhenBodyIdDiffersFromPathId() {
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(buildSala("sala-1")));
        var cmd = new UpdateSalaCommand("New Name", "New Address", "New City", "New Province",
            40.0, -3.7, null, "New description", "sala-2");

        assertThatThrownBy(() -> useCase.updateSala("sala-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(salaWritePort, never()).updateAll(any());
    }

    @Test
    void updateSala_acceptsWhenBodyIdMatchesPathId() {
        var stored = buildSala("sala-1");
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateSalaCommand("New Name", "Calle 1", "Madrid", "Madrid",
            40.0, -3.7, null, "New description", "sala-1");

        SalaConcierto result = useCase.updateSala("sala-1", cmd);

        assertThat(result.id()).isEqualTo("sala-1");
        verify(salaWritePort).updateAll(any(SalaConcierto.class));
    }

    @Test
    void updateSala_fullEditUpdatesAllFields() {
        var stored = buildSala("sala-1");
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateSalaCommand("New Name", "New Address", "New City", "New Province",
            41.5, -2.5, "https://example.com/img.jpg", "New description", null);

        SalaConcierto result = useCase.updateSala("sala-1", cmd);

        assertThat(result.id()).isEqualTo("sala-1");
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.address()).isEqualTo("New Address");
        assertThat(result.city()).isEqualTo("New City");
        assertThat(result.province()).isEqualTo("New Province");
        assertThat(result.lat()).isEqualTo(41.5);
        assertThat(result.lng()).isEqualTo(-2.5);
        assertThat(result.imageUrl()).isEqualTo("https://example.com/img.jpg");
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.sourceUrl()).isEqualTo(stored.sourceUrl());

        ArgumentCaptor<SalaConcierto> captor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWritePort).updateAll(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("sala-1");
        assertThat(captor.getValue().updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void updateSala_bumpsSyncMetaInSameTransactionAsUpdatedAt() {
        var stored = buildSala("sala-1");
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateSalaCommand("New Name", "Calle 1", "Madrid", "Madrid",
            40.0, -3.7, null, "New description", null);

        useCase.updateSala("sala-1", cmd);

        verify(syncMeta).updateLastModified("salas-concierto", FIXED_NOW);
        verify(txManager).commit(any());
    }

    @Test
    void updateSala_nonTerminalDqRowTransitionsToApprovedWithSuppliedValue() {
        var stored = buildSala("sala-1");
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateSalaCommand("Sala Test", "Calle 1", "Madrid", "Madrid",
            40.0, -3.7, null, "New description", null);

        useCase.updateSala("sala-1", cmd);

        verify(dataQualityWritePort).resolvePendingForField("sala", "sala-1", "description", "New description", FIXED_NOW);
    }

    @Test
    void updateSala_resolvesDqForEachEditableFieldWithVocabulary() {
        var stored = buildSala("sala-1");
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateSalaCommand("Sala Test", "New Address", "Madrid", "Madrid",
            41.5, -2.5, null, "New description", null);

        useCase.updateSala("sala-1", cmd);

        verify(dataQualityWritePort).resolvePendingForField("sala", "sala-1", "address", "New Address", FIXED_NOW);
        verify(dataQualityWritePort).resolvePendingForField("sala", "sala-1", "lat", "41.5", FIXED_NOW);
        verify(dataQualityWritePort).resolvePendingForField("sala", "sala-1", "lng", "-2.5", FIXED_NOW);
        verify(dataQualityWritePort).resolvePendingForField("sala", "sala-1", "description", "New description", FIXED_NOW);
    }

    @Test
    void updateSala_addressChangedWithEmptyCoords_triggersGeocodingAfterCommit() {
        var stored = new SalaConcierto("sala-1", "Sala Test", "Old Address", "Madrid", "Madrid",
            null, null, null, null, "manual", FIXED_NOW);
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(stored));
        SalaConcierto geocoded = new SalaConcierto("sala-1", "Sala Test", "New Address", "Madrid", "Madrid",
            40.0, -3.7, null, null, "manual", FIXED_NOW);
        when(venueGeocoding.geocodeIfNeeded(any(SalaConcierto.class), eq(FIXED_NOW))).thenReturn(geocoded);
        var cmd = new UpdateSalaCommand("Sala Test", "New Address", "Madrid", "Madrid",
            null, null, null, null, null);

        useCase.updateSala("sala-1", cmd);

        verify(venueGeocoding).geocodeIfNeeded(any(SalaConcierto.class), eq(FIXED_NOW));
    }

    @Test
    void updateSala_presentCoordinatesAreNeverOverwritten_geocodingDoesNotRun() {
        var stored = new SalaConcierto("sala-1", "Sala Test", "Old Address", "Madrid", "Madrid",
            40.0, -3.0, null, null, "manual", FIXED_NOW);
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateSalaCommand("Sala Test", "New Address", "Madrid", "Madrid",
            40.0, -3.0, null, null, null);

        useCase.updateSala("sala-1", cmd);

        verify(venueGeocoding, never()).geocodeIfNeeded(any(), any());
        ArgumentCaptor<SalaConcierto> captor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWritePort).updateAll(captor.capture());
        assertThat(captor.getValue().lat()).isEqualTo(40.0);
        assertThat(captor.getValue().lng()).isEqualTo(-3.0);
    }

    @Test
    void updateSala_addressUnchanged_doesNotTriggerGeocoding() {
        var stored = new SalaConcierto("sala-1", "Sala Test", "Same Address", "Madrid", "Madrid",
            null, null, null, null, "manual", FIXED_NOW);
        when(salaRepo.findByIdIncludingBlocked("sala-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateSalaCommand("Sala Test", "Same Address", "Madrid", "Madrid",
            null, null, null, null, null);

        useCase.updateSala("sala-1", cmd);

        verify(venueGeocoding, never()).geocodeIfNeeded(any(), any());
    }

    // ── updateArtist ──────────────────────────────────────────────────────────

    @Test
    void updateArtist_404WhenMissing() {
        when(artistRepo.findByIdIncludingBlocked("artist-missing")).thenReturn(Optional.empty());
        var cmd = new UpdateArtistCommand("New Name", "New Genre", "New description", null, null);

        assertThatThrownBy(() -> useCase.updateArtist("artist-missing", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(artistWritePort, never()).updateAll(any());
    }

    @Test
    void updateArtist_400OnBlankName() {
        when(artistRepo.findByIdIncludingBlocked("artist-1")).thenReturn(Optional.of(buildArtist("artist-1")));
        var cmd = new UpdateArtistCommand("", "New Genre", "New description", null, null);

        assertThatThrownBy(() -> useCase.updateArtist("artist-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(artistWritePort, never()).updateAll(any());
    }

    @Test
    void updateArtist_400WhenBodyIdDiffersFromPathId() {
        when(artistRepo.findByIdIncludingBlocked("artist-1")).thenReturn(Optional.of(buildArtist("artist-1")));
        var cmd = new UpdateArtistCommand("New Name", "New Genre", "New description", null, "artist-2");

        assertThatThrownBy(() -> useCase.updateArtist("artist-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(artistWritePort, never()).updateAll(any());
    }

    @Test
    void updateArtist_fullEditUpdatesAllFields() {
        var stored = buildArtist("artist-1");
        when(artistRepo.findByIdIncludingBlocked("artist-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateArtistCommand("New Name", "New Genre", "New description", "https://example.com/img.jpg", null);

        Artist result = useCase.updateArtist("artist-1", cmd);

        assertThat(result.id()).isEqualTo("artist-1");
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.genre()).isEqualTo("New Genre");
        assertThat(result.description()).isEqualTo("New description");
        assertThat(result.imageUrl()).isEqualTo("https://example.com/img.jpg");

        ArgumentCaptor<Artist> captor = ArgumentCaptor.forClass(Artist.class);
        verify(artistWritePort).updateAll(captor.capture());
        assertThat(captor.getValue().updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void updateArtist_bumpsSyncMetaAndResolvesDq() {
        var stored = buildArtist("artist-1");
        when(artistRepo.findByIdIncludingBlocked("artist-1")).thenReturn(Optional.of(stored));
        var cmd = new UpdateArtistCommand("New Name", "New Genre", "New description", null, null);

        useCase.updateArtist("artist-1", cmd);

        verify(syncMeta).updateLastModified("artists", FIXED_NOW);
        verify(dataQualityWritePort).resolvePendingForField("artist", "artist-1", "genre", "New Genre", FIXED_NOW);
        verify(dataQualityWritePort).resolvePendingForField("artist", "artist-1", "description", "New description", FIXED_NOW);
        verify(txManager).commit(any());
    }

    // ── updateConcert ─────────────────────────────────────────────────────────

    @Test
    void updateConcert_404WhenMissing() {
        when(concertRepo.findByIdIncludingDeleted("concert-missing")).thenReturn(Optional.empty());
        var cmd = new UpdateConcertCommand("2026-09-01", "22:00", "20€", null, null, null);

        assertThatThrownBy(() -> useCase.updateConcert("concert-missing", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(concertWritePort, never()).upsert(any());
    }

    @Test
    void updateConcert_404WhenSoftDeleted() {
        Concert deleted = new Concert("concert-1", "sala-1", List.of("artist-1"),
            LocalDate.of(2026, 8, 1), "21:00", "15€", "manual", FIXED_NOW);
        when(concertRepo.findByIdIncludingDeleted("concert-1")).thenReturn(Optional.of(deleted));
        var cmd = new UpdateConcertCommand("2026-09-01", "22:00", "20€", null, null, null);

        assertThatThrownBy(() -> useCase.updateConcert("concert-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(concertWritePort, never()).upsert(any());
        verify(syncMeta, never()).updateLastModified(any(), any());
    }

    @Test
    void updateConcert_400WhenBodyIdDiffersFromPathId() {
        Concert stored = buildActiveConcert("concert-1");
        when(concertRepo.findByIdIncludingDeleted("concert-1")).thenReturn(Optional.of(stored));
        when(concertRepo.existsActiveById("concert-1")).thenReturn(true);
        var cmd = new UpdateConcertCommand("2026-09-01", "22:00", "20€", "concert-2", null, null);

        assertThatThrownBy(() -> useCase.updateConcert("concert-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(concertWritePort, never()).upsert(any());
    }

    @Test
    void updateConcert_updatesDateTimePriceOnly_FKsOmitted() {
        Concert stored = buildActiveConcert("concert-1");
        when(concertRepo.findByIdIncludingDeleted("concert-1")).thenReturn(Optional.of(stored));
        when(concertRepo.existsActiveById("concert-1")).thenReturn(true);
        var cmd = new UpdateConcertCommand("2026-09-01", "22:00", "20€", null, null, null);

        Concert result = useCase.updateConcert("concert-1", cmd);

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.time()).isEqualTo("22:00");
        assertThat(result.price()).isEqualTo("20€");
        assertThat(result.salaConciertoId()).isEqualTo("sala-1");
        assertThat(result.artistIds()).containsExactly("artist-1");

        ArgumentCaptor<Concert> captor = ArgumentCaptor.forClass(Concert.class);
        verify(concertWritePort).upsert(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("concert-1");
        assertThat(captor.getValue().salaConciertoId()).isEqualTo("sala-1");
        assertThat(captor.getValue().artistIds()).containsExactly("artist-1");
    }

    @Test
    void updateConcert_matchingFKFieldsAreAccepted() {
        Concert stored = buildActiveConcert("concert-1");
        when(concertRepo.findByIdIncludingDeleted("concert-1")).thenReturn(Optional.of(stored));
        when(concertRepo.existsActiveById("concert-1")).thenReturn(true);
        var cmd = new UpdateConcertCommand("2026-09-01", "22:00", "20€", null, "sala-1", List.of("artist-1"));

        Concert result = useCase.updateConcert("concert-1", cmd);

        assertThat(result.salaConciertoId()).isEqualTo("sala-1");
        assertThat(result.artistIds()).containsExactly("artist-1");
        verify(concertWritePort).upsert(any(Concert.class));
    }

    @Test
    void updateConcert_differentFKInBody_400NoWrite() {
        Concert stored = buildActiveConcert("concert-1");
        when(concertRepo.findByIdIncludingDeleted("concert-1")).thenReturn(Optional.of(stored));
        when(concertRepo.existsActiveById("concert-1")).thenReturn(true);
        var cmd = new UpdateConcertCommand("2026-09-01", "22:00", "20€", null, "sala-DIFFERENT", null);

        assertThatThrownBy(() -> useCase.updateConcert("concert-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(concertWritePort, never()).upsert(any());
        verify(syncMeta, never()).updateLastModified(any(), any());
        verify(dataQualityWritePort, never()).resolvePendingForField(any(), any(), any(), any(), any());
    }

    @Test
    void updateConcert_differentArtistIdsInBody_400NoWrite() {
        Concert stored = buildActiveConcert("concert-1");
        when(concertRepo.findByIdIncludingDeleted("concert-1")).thenReturn(Optional.of(stored));
        when(concertRepo.existsActiveById("concert-1")).thenReturn(true);
        var cmd = new UpdateConcertCommand("2026-09-01", "22:00", "20€", null, null, List.of("artist-DIFFERENT"));

        assertThatThrownBy(() -> useCase.updateConcert("concert-1", cmd))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(concertWritePort, never()).upsert(any());
    }

    @Test
    void updateConcert_bumpsSyncMetaInSameTransaction() {
        Concert stored = buildActiveConcert("concert-1");
        when(concertRepo.findByIdIncludingDeleted("concert-1")).thenReturn(Optional.of(stored));
        when(concertRepo.existsActiveById("concert-1")).thenReturn(true);
        var cmd = new UpdateConcertCommand("2026-09-01", "22:00", "20€", null, null, null);

        useCase.updateConcert("concert-1", cmd);

        verify(syncMeta).updateLastModified("concerts", FIXED_NOW);
        verify(txManager).commit(any());
        verify(dataQualityWritePort, never()).resolvePendingForField(any(), any(), any(), any(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static SalaConcierto buildSala(String id) {
        return new SalaConcierto(id, "Sala Test", "Calle 1", "Madrid", "Madrid",
            40.0, -3.7, null, null, "manual", FIXED_NOW);
    }

    private static Artist buildArtist(String id) {
        return new Artist(id, "Artist Test", "Rock", null, null, "manual", null, FIXED_NOW);
    }

    private static Concert buildActiveConcert(String id) {
        return new Concert(id, "sala-1", List.of("artist-1"),
            LocalDate.of(2026, 8, 1), "21:00", "15€", "manual", FIXED_NOW);
    }
}
