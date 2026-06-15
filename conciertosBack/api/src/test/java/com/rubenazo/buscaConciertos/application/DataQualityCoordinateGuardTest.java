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
import com.rubenazo.buscaConciertos.application.ports.out.VenueLookupCandidate;
import com.rubenazo.buscaConciertos.application.ports.out.VenueMatch;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for Fix #1 (async try/catch guard) and Fix #4 (non-finite/out-of-range coordinate guard).
 */
@ExtendWith(MockitoExtension.class)
class DataQualityCoordinateGuardTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-08T10:00:00Z");
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
        lenient().when(artistRepository.findAll()).thenReturn(List.of());
        lenient().when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of());
        lenient().when(concertRepository.findAllIncludingBlocked()).thenReturn(List.of());
        lenient().when(entityEnrichmentPort.enrich(any())).thenReturn(EnrichedEntityResult.empty());
        useCase = new DataQualityUseCase(
            repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 20, artistRepository, salaRepository, 0.8,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase
        );
    }

    // =========================================================================
    // Fix #1 — async setup-phase exception must not propagate
    // =========================================================================

    @Test
    void onDataQualityCheck_repositoryFindByStatusThrows_doesNotPropagate() {
        when(repository.findByStatus("missing"))
            .thenThrow(new RuntimeException("DB connection lost"));

        assertThatNoException().isThrownBy(() ->
            useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of()))
        );
    }

    @Test
    void onDataQualityCheck_salaRepositoryFindAllThrows_doesNotPropagate() {
        // repository.findByStatus returns a non-empty list so execution continues past the early return,
        // then salaRepository.findAllIncludingBlocked() is called and throws.
        DataQuality row = new DataQuality(1L, "sala", "s1", "address", "missing", "severe",
            null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(row));
        when(salaRepository.findAllIncludingBlocked())
            .thenThrow(new RuntimeException("DB connection lost during sala lookup"));

        assertThatNoException().isThrownBy(() ->
            useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()))
        );
    }

    // =========================================================================
    // Fix #4 — geocodeSalaAddressIfNeeded: non-finite/out-of-range coords not persisted
    // =========================================================================

    @Test
    void approve_geocodeReturnsNaNLat_noCoordsWritten() {
        DataQuality dq = new DataQuality(1L, "sala", "s1", "address", "auto_found", "severe",
            "Calle Mayor 5", "tavily", 0.9, FIXED_NOW);
        SalaConcierto sala = new SalaConcierto("s1", "Sala Test", "Calle Mayor 5", "Madrid", "Madrid",
            null, null, null, null, null, FIXED_NOW);
        VenueMatch nanMatch = new VenueMatch(Double.NaN, 2.169, "Sala Test", 0.91, "name", "foursquare");

        when(repository.findById(1L)).thenReturn(Optional.of(dq));
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(sala));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(nanMatch));

        useCase.approve(1L);

        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lng"), anyString(), any());
        verify(writer, never()).upsertResolution(eq("sala"), eq("s1"), eq("lat"),
            anyString(), anyString(), anyString(), any(Double.class), any());
        verify(writer, never()).upsertResolution(eq("sala"), eq("s1"), eq("lng"),
            anyString(), anyString(), anyString(), any(Double.class), any());
    }

    @Test
    void approve_geocodeReturnsOutOfRangeLat_noCoordsWritten() {
        DataQuality dq = new DataQuality(2L, "sala", "s1", "address", "auto_found", "severe",
            "Calle Mayor 5", "tavily", 0.9, FIXED_NOW);
        SalaConcierto sala = new SalaConcierto("s1", "Sala Test", "Calle Mayor 5", "Madrid", "Madrid",
            null, null, null, null, null, FIXED_NOW);
        // lat=999 is out of [-90,90]
        VenueMatch outOfRangeMatch = new VenueMatch(999.0, -3.7, "Sala Test", 0.91, "name", "foursquare");

        when(repository.findById(2L)).thenReturn(Optional.of(dq));
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(sala));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(outOfRangeMatch));

        useCase.approve(2L);

        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lng"), anyString(), any());
        verify(writer, never()).upsertResolution(anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), any(Double.class), any());
    }

    @Test
    void approve_geocodeReturnsInfinityLng_noCoordsWritten() {
        DataQuality dq = new DataQuality(3L, "sala", "s1", "address", "auto_found", "severe",
            "Calle Mayor 5", "tavily", 0.9, FIXED_NOW);
        SalaConcierto sala = new SalaConcierto("s1", "Sala Test", "Calle Mayor 5", "Madrid", "Madrid",
            null, null, null, null, null, FIXED_NOW);
        VenueMatch infMatch = new VenueMatch(40.4, Double.POSITIVE_INFINITY, "Sala Test", 0.91, "name", "foursquare");

        when(repository.findById(3L)).thenReturn(Optional.of(dq));
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(sala));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(infMatch));

        useCase.approve(3L);

        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lng"), anyString(), any());
    }

    // =========================================================================
    // Fix #4 — fillMissingSalaCoords: non-finite/out-of-range coords not persisted
    // =========================================================================

    @Test
    void fillMissing_nanLatFromFoursquare_noCoordWritten() {
        SalaConcierto sala = new SalaConcierto("id1", "Sala Test", "addr", "Madrid", "Madrid",
            null, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate nanMatch = new VenueLookupCandidate(
            Double.NaN, 2.169, "Sala Test", "addr", 0.92, "name", "foursquare");

        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        lenient().when(repository.findByStatus("missing")).thenReturn(List.of());
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(nanMatch));

        useCase.fillMissingSalaCoords(false, 25);

        verify(salaConciertoWritePort, never()).updateField(eq("id1"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort, never()).updateField(eq("id1"), eq("lng"), anyString(), any());
    }

    @Test
    void fillMissing_outOfRangeLat999_noCoordWritten() {
        SalaConcierto sala = new SalaConcierto("id2", "Sala Test", "addr", "Madrid", "Madrid",
            null, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate outOfRangeMatch = new VenueLookupCandidate(
            999.0, -3.7, "Sala Test", "addr", 0.92, "name", "foursquare");

        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        lenient().when(repository.findByStatus("missing")).thenReturn(List.of());
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(outOfRangeMatch));

        useCase.fillMissingSalaCoords(false, 25);

        verify(salaConciertoWritePort, never()).updateField(eq("id2"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort, never()).updateField(eq("id2"), eq("lng"), anyString(), any());
    }
}
