package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.ArtistRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.EntityEnrichmentPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.TavilySearchPort;
import com.rubenazo.buscaConciertos.application.ports.out.VenueMatch;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQualityUseCaseFillTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-02T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock private DataQualityRepositoryPort repository;
    @Mock private DataQualityWritePort writer;
    @Mock private TavilySearchPort tavilySearch;
    @Mock private ArtistWritePort artistWritePort;
    @Mock private SalaConciertoWritePort salaConciertoWritePort;
    @Mock private SyncMetadataWritePort syncMetadataWritePort;
    @Mock private ArtistRepositoryPort artistRepository;
    @Mock private SalaConciertoRepositoryPort salaRepository;
    @Mock private EntityEnrichmentPort entityEnrichmentPort;
    @Mock private ConcertRepositoryPort concertRepository;
    @Mock private VenueGeocodingUseCase venueGeocodingUseCase;

    private DataQualityUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(salaRepository.findByIdIncludingBlocked(any())).thenReturn(Optional.empty());
        lenient().when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.empty());
        useCase = new DataQualityUseCase(
            repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 20, artistRepository, salaRepository, 0.8,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase
        );
    }

    // --- 404: id not found ---

    @Test
    void fill_throws404WhenIdNotFound() {
        when(repository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.fill(9999L, "some value"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- 409: terminal status ---

    @Test
    void fill_throws409WhenStatusIsApproved() {
        DataQuality dq = new DataQuality(1L, "sala", "s1", "address", "approved",
                "severe", "Calle Mayor", null, null, FIXED_NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(1L, "Nueva Calle 5"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void fill_throws409WhenStatusIsAutoApproved() {
        DataQuality dq = new DataQuality(2L, "artist", "a1", "genre", "auto_approved",
                "severe", "Rock", null, 0.9, FIXED_NOW);
        when(repository.findById(2L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(2L, "Pop"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- 422: concert entity type is not fillable ---

    @Test
    void fill_throws422WhenEntityTypeIsConcert() {
        DataQuality dq = new DataQuality(3L, "concert", "c1", "sala_concierto_id", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(3L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(3L, "sala-foo"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // --- 422: name field is not fillable for sala ---

    @Test
    void fill_throws422WhenFieldIsNameForSala() {
        DataQuality dq = new DataQuality(4L, "sala", "s1", "name", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(4L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(4L, "Sala Apolo"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // --- 422: name field is not fillable for artist ---

    @Test
    void fill_throws422WhenFieldIsNameForArtist() {
        DataQuality dq = new DataQuality(5L, "artist", "a1", "name", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(5L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(5L, "Radiohead"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // --- 400: blank value ---

    @Test
    void fill_throws400WhenValueIsBlank() {
        DataQuality dq = new DataQuality(6L, "sala", "s1", "address", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(6L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(6L, "   "))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fill_throws400WhenValueIsNull() {
        DataQuality dq = new DataQuality(7L, "sala", "s1", "address", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(7L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(7L, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- 400: non-numeric lat ---

    @Test
    void fill_throws400WhenLatIsNotNumeric() {
        DataQuality dq = new DataQuality(8L, "sala", "s1", "lat", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(8L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(8L, "not-a-number"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- 400: non-numeric lng ---

    @Test
    void fill_throws400WhenLngIsNotNumeric() {
        DataQuality dq = new DataQuality(9L, "sala", "s1", "lng", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(9L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(9L, "abc"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fill_throws400WhenLatIsOutOfRange() {
        DataQuality dq = new DataQuality(21L, "sala", "s1", "lat", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(21L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(21L, "123.0"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fill_throws400WhenLngIsOutOfRange() {
        DataQuality dq = new DataQuality(22L, "sala", "s1", "lng", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(22L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(22L, "-36015.0"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- 200: sala address ok ---

    @Test
    void fill_salaAddress_appliesResolutionAndSetsApproved() {
        DataQuality dq = new DataQuality(10L, "sala", "s1", "address", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(10L)).thenReturn(Optional.of(dq));

        useCase.fill(10L, "Calle Mayor 5");

        verify(salaConciertoWritePort).updateField("s1", "address", "Calle Mayor 5", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified(eq("salas-concierto"), eq(FIXED_NOW));
        verify(syncMetadataWritePort).updateLastModified(eq("concerts"), eq(FIXED_NOW));
        verify(writer).updateStatus(10L, "approved", FIXED_NOW);
    }

    @Test
    void fill_salaAddress_geocodesAndMarksLatLngAutoApprovedWithRealProviderAndScore() {
        // Updated for venue-geocoding-by-name: geocode() now returns Optional<VenueMatch>
        DataQuality dq = new DataQuality(30L, "sala", "s1", "address", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(30L)).thenReturn(Optional.of(dq));
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(
            new SalaConcierto("s1", "Sala Test", "Calle Mayor 5", "Madrid", "Madrid",
                null, null, null, null, null, FIXED_NOW)
        ));
        // High-confidence match (above threshold 0.8) — real provider and score
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(
            new VenueMatch(40.4168, -3.7038, "Calle Mayor 5", 0.85, "address", "locationiq")
        ));

        useCase.fill(30L, "Calle Mayor 5");

        verify(salaConciertoWritePort).updateField("s1", "lat", "40.4168", FIXED_NOW);
        verify(salaConciertoWritePort).updateField("s1", "lng", "-3.7038", FIXED_NOW);
        // Real provider and score — not hardcoded "locationiq"/1.0
        verify(writer).upsertResolution("sala", "s1", "lat", "auto_approved", "40.4168", "locationiq", 0.85, FIXED_NOW);
        verify(writer).upsertResolution("sala", "s1", "lng", "auto_approved", "-3.7038", "locationiq", 0.85, FIXED_NOW);
    }

    // --- 200: artist genre ok ---

    @Test
    void fill_artistGenre_appliesResolutionAndSetsApproved() {
        DataQuality dq = new DataQuality(11L, "artist", "a1", "genre", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(11L)).thenReturn(Optional.of(dq));

        useCase.fill(11L, "Indie Rock");

        verify(artistWritePort).updateField("a1", "genre", "Indie Rock", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified(eq("artists"), eq(FIXED_NOW));
        verify(syncMetadataWritePort).updateLastModified(eq("concerts"), eq(FIXED_NOW));
        verify(writer).updateStatus(11L, "approved", FIXED_NOW);
    }

    // --- 200: numeric lat accepted ---

    @Test
    void fill_latWithNumericValue_appliesResolution() {
        DataQuality dq = new DataQuality(12L, "sala", "s1", "lat", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(12L)).thenReturn(Optional.of(dq));

        useCase.fill(12L, "40.4168");

        verify(salaConciertoWritePort).updateField("s1", "lat", "40.4168", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified(eq("concerts"), eq(FIXED_NOW));
        verify(writer).updateStatus(12L, "approved", FIXED_NOW);
    }

    // --- concert entity: no write must occur ---

    @Test
    void fill_concert_noWriteHappens() {
        DataQuality dq = new DataQuality(20L, "concert", "c1", "sala_concierto_id", "missing",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(20L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.fill(20L, "sala-foo"))
            .isInstanceOf(ResponseStatusException.class);

        verify(salaConciertoWritePort, never()).updateField(any(), any(), any(), any());
        verify(artistWritePort, never()).updateField(any(), any(), any(), any());
        verify(writer, never()).updateStatus(any(), any(), any());
    }

}
