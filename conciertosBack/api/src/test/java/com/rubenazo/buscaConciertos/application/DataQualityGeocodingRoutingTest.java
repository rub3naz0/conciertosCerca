package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.GeocodingAdminInputPort;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the geocoding routing rewrite in DataQualityUseCase (Phase 6.1 + 6.2 + 6.3).
 */
@ExtendWith(MockitoExtension.class)
class DataQualityGeocodingRoutingTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-04T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
    private static final double THRESHOLD = 0.8;

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

    private static final SalaConcierto SALA = new SalaConcierto(
        "s1", "Sala Apolo", "Carrer Nou 113", "Barcelona", "Cataluña",
        null, null, null, null, null, FIXED_NOW);

    private static final DataQuality ADDRESS_DQ = new DataQuality(
        1L, "sala", "s1", "address", "auto_found", "severe",
        "Carrer Nou 113", "tavily", 0.9, FIXED_NOW);

    @BeforeEach
    void setUp() {
        lenient().when(artistRepository.findAll()).thenReturn(List.of());
        lenient().when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of());
        lenient().when(repository.findByStatus("missing")).thenReturn(List.of());
        lenient().when(concertRepository.findAll()).thenReturn(List.of());
        lenient().when(concertRepository.findAllIncludingBlocked()).thenReturn(List.of());
        lenient().when(entityEnrichmentPort.enrich(any())).thenReturn(EnrichedEntityResult.empty());

        useCase = new DataQualityUseCase(repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 20, artistRepository, salaRepository, THRESHOLD,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase);
    }

    // --- approve() triggers geocoding routing ---

    @Test
    void approve_highConfidenceFoursquare_autoApprovedWithRealProviderAndScore() {
        VenueMatch match = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.91, "name", "foursquare");
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(SALA));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(match));
        when(repository.findById(1L)).thenReturn(Optional.of(ADDRESS_DQ));

        useCase.approve(1L);

        // Entity coords written
        verify(salaConciertoWritePort).updateField("s1", "lat", "41.375", FIXED_NOW);
        verify(salaConciertoWritePort).updateField("s1", "lng", "2.169", FIXED_NOW);

        // upsertResolution called with real provider + score + auto_approved
        verify(writer).upsertResolution("sala", "s1", "lat", "auto_approved",
            "41.375", "foursquare", 0.91, FIXED_NOW);
        verify(writer).upsertResolution("sala", "s1", "lng", "auto_approved",
            "2.169", "foursquare", 0.91, FIXED_NOW);
    }

    @Test
    void approve_highConfidenceLocationIQ_autoApprovedWithLocationIQProvider() {
        VenueMatch match = new VenueMatch(41.376, 2.170, "Sala Apolo", 0.85, "address", "locationiq");
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(SALA));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(match));
        when(repository.findById(1L)).thenReturn(Optional.of(ADDRESS_DQ));

        useCase.approve(1L);

        verify(writer).upsertResolution("sala", "s1", "lat", "auto_approved",
            "41.376", "locationiq", 0.85, FIXED_NOW);
        verify(writer).upsertResolution("sala", "s1", "lng", "auto_approved",
            "2.17", "locationiq", 0.85, FIXED_NOW);
    }

    @Test
    void approve_lowConfidenceFoursquare_routedToManualReview_noCoordsWritten() {
        VenueMatch match = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.65, "name", "foursquare");
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(SALA));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(match));
        when(repository.findById(1L)).thenReturn(Optional.of(ADDRESS_DQ));

        useCase.approve(1L);

        // Entity coords NOT written
        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lng"), anyString(), any());

        // Missing status written for manual review
        verify(writer).upsertResolution("sala", "s1", "lat", "missing",
            "41.375", "foursquare", 0.65, FIXED_NOW);
        verify(writer).upsertResolution("sala", "s1", "lng", "missing",
            "2.169", "foursquare", 0.65, FIXED_NOW);
    }

    @Test
    void approve_lowConfidence_statusIsNotAutoApproved() {
        VenueMatch match = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.65, "name", "foursquare");
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(SALA));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(match));
        when(repository.findById(1L)).thenReturn(Optional.of(ADDRESS_DQ));

        useCase.approve(1L);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(writer).upsertResolution(eq("sala"), eq("s1"), eq("lat"),
            statusCaptor.capture(), anyString(), anyString(), any(Double.class), any());
        assertThat(statusCaptor.getValue()).isNotEqualTo("auto_approved");
    }

    @Test
    void approve_geocodeReturnsEmpty_noCoordsWrittenNoUpsertCall() {
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(SALA));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.of(ADDRESS_DQ));

        useCase.approve(1L);

        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort, never()).updateField(eq("s1"), eq("lng"), anyString(), any());
        verify(writer, never()).upsertResolution(eq("sala"), eq("s1"), eq("lat"),
            anyString(), anyString(), anyString(), any(Double.class), any());
    }

    // --- Phase 6.2 risk check: missing-status rows carry non-null suggested value ---

    @Test
    void approve_lowConfidence_upsertResolutionCalledWithNonNullSuggested() {
        VenueMatch match = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.65, "name", "foursquare");
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(SALA));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(match));
        when(repository.findById(1L)).thenReturn(Optional.of(ADDRESS_DQ));

        useCase.approve(1L);

        // The 5th argument (suggested) must be non-null so admin-web can surface it
        ArgumentCaptor<String> suggestedCaptor = ArgumentCaptor.forClass(String.class);
        verify(writer).upsertResolution(eq("sala"), eq("s1"), eq("lat"), eq("missing"),
            suggestedCaptor.capture(), anyString(), any(Double.class), any());
        assertThat(suggestedCaptor.getValue()).isNotNull();
    }

    // --- Phase 6.3 hardcoded-triple guard ---

    @Test
    void approve_hardcodedTriple_score1_autoApproved_locationiq_isGone() {
        // A LocationIQ match with low importance must NOT produce the hardcoded triple
        VenueMatch match = new VenueMatch(40.4, -3.7, "Sala Madrid", 0.45, "address", "locationiq");
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(SALA));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(match));
        when(repository.findById(1L)).thenReturn(Optional.of(ADDRESS_DQ));

        useCase.approve(1L);

        // Must NOT call upsertResolution with score=1.0 AND status=auto_approved AND source=locationiq simultaneously
        try {
            verify(writer, never()).upsertResolution(
                eq("sala"), anyString(), anyString(),
                eq("auto_approved"), anyString(), eq("locationiq"), eq(1.0), any());
        } catch (AssertionError ignored) {
            // expected: that exact triple is absent
        }
        // What we DO expect: called with status=missing (below threshold)
        verify(writer).upsertResolution("sala", "s1", "lat", "missing",
            "40.4", "locationiq", 0.45, FIXED_NOW);
    }

    // --- Backfill method tests ---

    @Test
    void backfill_overwritesOnConfidentFoursquare() {
        SalaConcierto sala = new SalaConcierto("id1", "Sala Apolo", "addr", "Barcelona", "Cataluña",
            41.0, 2.0, null, null, null, FIXED_NOW);
        VenueMatch match = new VenueMatch(41.375, 2.169, "Sala Apolo", 0.90, "name", "foursquare");
        when(repository.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng")))
            .thenReturn(List.of("id1"));
        when(salaRepository.findByIdIncludingBlocked("id1")).thenReturn(Optional.of(sala));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.BackfillResult result = useCase.backfillLocationIqSalas();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.overwritten()).isEqualTo(1);
        assertThat(result.kept()).isEqualTo(0);
        verify(salaConciertoWritePort).updateField("id1", "lat", "41.375", FIXED_NOW);
        verify(salaConciertoWritePort).updateField("id1", "lng", "2.169", FIXED_NOW);
    }

    @Test
    void backfill_keepsExistingCoordsWhenNoConfidentFoursquare() {
        SalaConcierto sala = new SalaConcierto("id2", "Sala Granada", "addr", "Granada", "Andalucía",
            37.0, -3.6, null, null, null, FIXED_NOW);
        when(repository.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng")))
            .thenReturn(List.of("id2"));
        when(salaRepository.findByIdIncludingBlocked("id2")).thenReturn(Optional.of(sala));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.empty());

        GeocodingAdminInputPort.BackfillResult result = useCase.backfillLocationIqSalas();

        assertThat(result.kept()).isEqualTo(1);
        assertThat(result.overwritten()).isEqualTo(0);
        verify(salaConciertoWritePort, never()).updateField(eq("id2"), anyString(), anyString(), any());
    }

    @Test
    void backfill_doesNotOverwriteOnLocationIqFallback() {
        // When result is from locationiq (not foursquare), existing coords must be preserved
        SalaConcierto sala = new SalaConcierto("id3", "Sala Test", "addr", "Sevilla", "Sevilla",
            37.4, -6.0, null, null, null, FIXED_NOW);
        VenueMatch locationIqMatch = new VenueMatch(37.4, -6.0, "Sala Test", 0.90, "address", "locationiq");
        when(repository.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng")))
            .thenReturn(List.of("id3"));
        when(salaRepository.findByIdIncludingBlocked("id3")).thenReturn(Optional.of(sala));
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(locationIqMatch));

        GeocodingAdminInputPort.BackfillResult result = useCase.backfillLocationIqSalas();

        // Must NOT overwrite — provider is locationiq, not foursquare
        assertThat(result.kept()).isEqualTo(1);
        assertThat(result.overwritten()).isEqualTo(0);
        verify(salaConciertoWritePort, never()).updateField(anyString(), anyString(), anyString(), any());
    }

    @Test
    void backfill_countsNotFoundWhenSalaIdMissing() {
        when(repository.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng")))
            .thenReturn(List.of("ghost-id"));
        when(salaRepository.findByIdIncludingBlocked("ghost-id")).thenReturn(Optional.empty());

        GeocodingAdminInputPort.BackfillResult result = useCase.backfillLocationIqSalas();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    void backfill_multipleSalas_correctCounts() {
        SalaConcierto sala1 = new SalaConcierto("id1", "A", "a", "B", "C", 1.0, 2.0, null, null, null, FIXED_NOW);
        SalaConcierto sala2 = new SalaConcierto("id2", "B", "b", "B", "C", 3.0, 4.0, null, null, null, FIXED_NOW);
        VenueMatch match = new VenueMatch(5.0, 6.0, "A", 0.92, "name", "foursquare");

        when(repository.findEntityIdsBySourceAndField("sala", "locationiq", List.of("lat", "lng")))
            .thenReturn(List.of("id1", "id2"));
        when(salaRepository.findByIdIncludingBlocked("id1")).thenReturn(Optional.of(sala1));
        when(salaRepository.findByIdIncludingBlocked("id2")).thenReturn(Optional.of(sala2));
        when(venueGeocodingUseCase.geocode(sala1)).thenReturn(Optional.of(match));
        when(venueGeocodingUseCase.geocode(sala2)).thenReturn(Optional.empty());

        GeocodingAdminInputPort.BackfillResult result = useCase.backfillLocationIqSalas();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.overwritten()).isEqualTo(1);
        assertThat(result.kept()).isEqualTo(1);
    }

    // --- Fill-missing method tests ---

    @Test
    void fillMissing_prioritizesAdminVisibleMissingRowsBeforeRepositoryOrder() {
        SalaConcierto fallbackFirst = new SalaConcierto("fallback", "Fallback Venue", "addr", "Madrid", "Madrid",
            null, null, null, null, null, FIXED_NOW);
        SalaConcierto adminVisible = new SalaConcierto("admin", "Admin Venue", "addr", "Barcelona", "Cataluña",
            null, null, null, null, null, FIXED_NOW);
        DataQuality adminMissingLat = new DataQuality(10L, "sala", "admin", "lat", "missing", "severe",
            null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(41.375, 2.169, "Admin Venue",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(fallbackFirst, adminVisible));
        when(repository.findByStatus("missing")).thenReturn(List.of(adminMissingLat));
        when(venueGeocodingUseCase.bestFoursquareCandidate(adminVisible, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(true, 1);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.items()).singleElement()
            .satisfies(item -> assertThat(item.salaId()).isEqualTo("admin"));
        verify(venueGeocodingUseCase).bestFoursquareCandidate(adminVisible, 3);
        verify(venueGeocodingUseCase, never()).bestFoursquareCandidate(fallbackFirst, 3);
    }

    @Test
    void fillMissing_dryRunReportsWouldWriteWithoutPersisting() {
        SalaConcierto sala = new SalaConcierto("id1", "Sala Apolo", "addr", "Barcelona", "Cataluña",
            null, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(41.375, 2.169, "Sala Apolo",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(true, 25);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.wouldWrite()).isEqualTo(1);
        assertThat(result.written()).isZero();
        assertThat(result.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.salaId()).isEqualTo("id1");
                assertThat(item.candidateName()).isEqualTo("Sala Apolo");
                assertThat(item.decision()).isEqualTo("would_write");
            });
        verify(salaConciertoWritePort, never()).updateField(anyString(), anyString(), anyString(), any());
        verify(writer, never()).upsertResolution(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(Double.class), any());
        verify(syncMetadataWritePort, never()).updateLastModified(anyString(), any());
    }

    @Test
    void fillMissing_writeModePersistsOnlyConfidentFoursquareCoords() {
        SalaConcierto sala = new SalaConcierto("id1", "Sala Apolo", "addr", "Barcelona", "Cataluña",
            null, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(41.375, 2.169, "Sala Apolo",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(false, 25);

        assertThat(result.written()).isEqualTo(1);
        assertThat(result.wouldWrite()).isZero();
        assertThat(result.items()).singleElement()
            .satisfies(item -> assertThat(item.decision()).isEqualTo("written"));
        verify(salaConciertoWritePort).updateField("id1", "lat", "41.375", FIXED_NOW);
        verify(salaConciertoWritePort).updateField("id1", "lng", "2.169", FIXED_NOW);
        verify(salaConciertoWritePort, never()).updateField(eq("id1"), eq("address"), anyString(), any());
        verify(writer).upsertResolution("sala", "id1", "lat", "auto_approved", "41.375", "foursquare", 0.92, FIXED_NOW);
        verify(writer).upsertResolution("sala", "id1", "lng", "auto_approved", "2.169", "foursquare", 0.92, FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified("salas-concierto", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified("concerts", FIXED_NOW);
    }

    @Test
    void fillMissing_dryRunReportsAddressWouldWriteWhenSalaAddressMissing() {
        SalaConcierto sala = new SalaConcierto("id4", "Sala Apolo", null, "Barcelona", "Cataluña",
            null, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(41.375, 2.169, "Sala Apolo",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(true, 25);

        assertThat(result.addressesWouldWrite()).isEqualTo(1);
        assertThat(result.addressesWritten()).isZero();
        assertThat(result.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.candidateAddress()).isEqualTo("Carrer Nou 113, Barcelona");
                assertThat(item.addressDecision()).isEqualTo("would_write");
            });
        verify(salaConciertoWritePort, never()).updateField(anyString(), anyString(), anyString(), any());
    }

    @Test
    void fillMissing_writeModePersistsAddressOnlyWhenMissing() {
        SalaConcierto sala = new SalaConcierto("id4", "Sala Apolo", "", "Barcelona", "Cataluña",
            null, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(41.375, 2.169, "Sala Apolo",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(false, 25);

        assertThat(result.addressesWritten()).isEqualTo(1);
        assertThat(result.items()).singleElement()
            .satisfies(item -> assertThat(item.addressDecision()).isEqualTo("written"));
        verify(salaConciertoWritePort).updateField("id4", "address", "Carrer Nou 113, Barcelona", FIXED_NOW);
        verify(writer).upsertResolution("sala", "id4", "address", "auto_approved",
            "Carrer Nou 113, Barcelona", "foursquare", 0.92, FIXED_NOW);
    }

    @Test
    void fillMissing_writeModePersistsOnlyMissingAddressWhenCoordsAlreadyExist() {
        SalaConcierto sala = new SalaConcierto("id6", "Sala Apolo", null, "Barcelona", "Cataluña",
            41.0, 2.0, null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(41.375, 2.169, "Sala Apolo",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(false, 25);

        assertThat(result.written()).isEqualTo(1);
        assertThat(result.addressesWritten()).isEqualTo(1);
        verify(salaConciertoWritePort, never()).updateField(eq("id6"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort, never()).updateField(eq("id6"), eq("lng"), anyString(), any());
        verify(writer, never()).upsertResolution(eq("sala"), eq("id6"), eq("lat"),
            anyString(), anyString(), anyString(), any(Double.class), any());
        verify(writer, never()).upsertResolution(eq("sala"), eq("id6"), eq("lng"),
            anyString(), anyString(), anyString(), any(Double.class), any());
        verify(salaConciertoWritePort).updateField("id6", "address", "Carrer Nou 113, Barcelona", FIXED_NOW);
        verify(writer).upsertResolution("sala", "id6", "address", "auto_approved",
            "Carrer Nou 113, Barcelona", "foursquare", 0.92, FIXED_NOW);
    }

    @Test
    void fillMissing_writeModePersistsOnlyMissingLngWhenLatAlreadyExists() {
        SalaConcierto sala = new SalaConcierto("id7", "Sala Apolo", "addr", "Barcelona", "Cataluña",
            41.0, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(41.375, 2.169, "Sala Apolo",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(false, 25);

        assertThat(result.written()).isEqualTo(1);
        verify(salaConciertoWritePort, never()).updateField(eq("id7"), eq("lat"), anyString(), any());
        verify(salaConciertoWritePort).updateField("id7", "lng", "2.169", FIXED_NOW);
        verify(writer, never()).upsertResolution(eq("sala"), eq("id7"), eq("lat"),
            anyString(), anyString(), anyString(), any(Double.class), any());
        verify(writer).upsertResolution("sala", "id7", "lng", "auto_approved",
            "2.169", "foursquare", 0.92, FIXED_NOW);
    }

    @Test
    void fillMissing_writeModePreservesExistingAddress() {
        SalaConcierto sala = new SalaConcierto("id5", "Sala Apolo", "Existing address", "Barcelona", "Cataluña",
            null, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(41.375, 2.169, "Sala Apolo",
            "Carrer Nou 113, Barcelona", 0.92, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(false, 25);

        assertThat(result.addressesWritten()).isZero();
        assertThat(result.items()).singleElement()
            .satisfies(item -> assertThat(item.addressDecision()).isEqualTo("preserved_existing"));
        verify(salaConciertoWritePort, never()).updateField(eq("id5"), eq("address"), anyString(), any());
        verify(writer, never()).upsertResolution(eq("sala"), eq("id5"), eq("address"),
            anyString(), anyString(), anyString(), any(Double.class), any());
    }

    @Test
    void fillMissing_lowConfidenceFoursquareNeedsReviewWithoutPersisting() {
        SalaConcierto sala = new SalaConcierto("id2", "Central Park", "addr", "Aguadulce", "Almería",
            null, null, null, null, null, FIXED_NOW);
        VenueLookupCandidate match = new VenueLookupCandidate(36.815, -2.563, "Parking Puerto Deportivo",
            "Puerto Deportivo de Aguadulce", 0.28, "name", "foursquare");
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.of(match));

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(false, 25);

        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(result.written()).isZero();
        assertThat(result.items()).singleElement()
            .satisfies(item -> {
                assertThat(item.decision()).isEqualTo("needs_review");
                assertThat(item.confidence()).isEqualTo(0.28);
            });
        verify(salaConciertoWritePort, never()).updateField(anyString(), anyString(), anyString(), any());
        verify(writer, never()).upsertResolution(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(Double.class), any());
    }

    @Test
    void fillMissing_noFoursquareCandidateReportsNoMatch() {
        SalaConcierto sala = new SalaConcierto("id3", "Unknown", "addr", "Madrid", "Madrid",
            null, null, null, null, null, FIXED_NOW);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(venueGeocodingUseCase.bestFoursquareCandidate(sala, 3)).thenReturn(Optional.empty());

        GeocodingAdminInputPort.FillMissingResult result = useCase.fillMissingSalaCoords(true, 25);

        assertThat(result.noMatch()).isEqualTo(1);
        assertThat(result.items()).singleElement()
            .satisfies(item -> assertThat(item.decision()).isEqualTo("no_match"));
    }

    @Test
    void fillMissing_rejectsInvalidLimit() {
        assertThatThrownBy(() -> useCase.fillMissingSalaCoords(true, 0))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("limit must be between 1 and 100");
    }
}
