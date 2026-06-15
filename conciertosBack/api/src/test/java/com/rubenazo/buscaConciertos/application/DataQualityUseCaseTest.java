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
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;
import com.rubenazo.buscaConciertos.domain.EnrichedFieldValue;
import com.rubenazo.buscaConciertos.domain.EntityEnrichmentRequest;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.domain.SearchOptions;
import com.rubenazo.buscaConciertos.domain.TavilyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataQualityUseCaseTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-28T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock
    private DataQualityRepositoryPort repository;

    @Mock
    private DataQualityWritePort writer;

    @Mock
    private TavilySearchPort tavilySearch;

    @Mock
    private ArtistWritePort artistWritePort;

    @Mock
    private SalaConciertoWritePort salaConciertoWritePort;

    @Mock
    private SyncMetadataWritePort syncMetadataWritePort;

    @Mock
    private ArtistRepositoryPort artistRepository;

    @Mock
    private SalaConciertoRepositoryPort salaRepository;

    @Mock
    private EntityEnrichmentPort entityEnrichmentPort;

    @Mock
    private ConcertRepositoryPort concertRepository;

    @Mock
    private VenueGeocodingUseCase venueGeocodingUseCase;

    private DataQualityUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(artistRepository.findAll()).thenReturn(List.of());
        lenient().when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of());
        lenient().when(concertRepository.findAll()).thenReturn(List.of());
        lenient().when(concertRepository.findAllIncludingBlocked()).thenReturn(List.of());
        lenient().when(entityEnrichmentPort.enrich(any())).thenReturn(EnrichedEntityResult.empty());
        useCase = new DataQualityUseCase(repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 20, artistRepository, salaRepository, 0.8,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase);
    }

    // --- Existing tests migrated to new 2-arg tavilySearch.search(anyString(), any()) ---

    @Test
    void onDataQualityCheck_skipsConcertRows() {
        DataQuality concertRow = new DataQuality(1L, "concert", "c1", "price", "missing", "non_severe", null, null, null, FIXED_NOW);
        DataQuality artistRow = new DataQuality(2L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(concertRow, artistRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        // Only the artist entity triggers a search; concert is skipped
        verify(tavilySearch, times(1)).search(anyString(), any());
        verify(writer, never()).updateSuggestion(eq(1L), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void onDataQualityCheck_doesNothingWhenNoMissingRows() {
        when(repository.findByStatus("missing")).thenReturn(List.of());

        assertThatNoException().isThrownBy(() ->
            useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of()))
        );

        verifyNoInteractions(tavilySearch, writer);
    }

    @Test
    void onDataQualityCheck_noOpAdapterReturnsEmptyAndDoesNotUpdate() {
        DataQuality salaRow = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(salaRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        // entityEnrichmentPort already stubbed to return empty() in setUp

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verify(writer, never()).updateSuggestion(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void onDataQualityCheck_tavilyExceptionDoesNotPropagateAndAppContinues() {
        DataQuality salaRow1 = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality salaRow2 = new DataQuality(2L, "sala", "s2", "description", "missing", "non_severe", null, null, null, FIXED_NOW);
        TavilyResult good = new TavilyResult("Good", "https://good.com", "content", 0.9);

        when(repository.findByStatus("missing")).thenReturn(List.of(salaRow1, salaRow2));
        // Each entity gets its own enrich call; s1 search throws, s2 search succeeds
        // s1 and s2 are separate entities so they generate separate enrich calls
        when(tavilySearch.search(anyString(), any()))
            .thenThrow(new RuntimeException("Tavily 5xx error"))
            .thenReturn(List.of(good));
        // s2 enrich returns description value
        when(entityEnrichmentPort.enrich(argThat(req -> "s2".equals(req.entityId()))))
            .thenReturn(new EnrichedEntityResult(Map.of(
                "description", new EnrichedFieldValue("content", "https://good.com", 0.9)
            )));

        assertThatNoException().isThrownBy(() ->
            useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1", "s2"), List.of()))
        );

        // Second row (non_severe + confidence=0.9 >= 0.8) → auto_approved + entity write
        verify(writer).updateSuggestion(2L, "auto_approved", "content", "https://good.com", 0.9, FIXED_NOW);
    }

    // --- listIssues (existing) ---

    @Test
    void listIssues_returnsAllIssuesWhenStatusNull() {
        when(repository.findByStatus(anyString())).thenReturn(List.of());

        List<DataQuality> result = useCase.listIssues(null);

        assertThat(result).isEmpty();
        verify(repository, times(5)).findByStatus(anyString());
    }

    @Test
    void listIssues_throwsBadRequestForUnknownStatus() {
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call =
            () -> useCase.listIssues("bogus");
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.web.server.ResponseStatusException.class, call::call
        ).getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    // --- Ghost field tests (preserved) ---

    @Test
    void onDataQualityCheck_ghostField_phone_isSkipped() {
        DataQuality salaPhoneRow = new DataQuality(30L, "sala", "s1", "phone", "missing", "non_severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(salaPhoneRow));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verifyNoInteractions(tavilySearch);
        verifyNoInteractions(writer);
    }

    @Test
    void onDataQualityCheck_ghostField_salaWebsite_isSkipped() {
        DataQuality salaWebsiteRow = new DataQuality(31L, "sala", "s1", "website", "missing", "non_severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(salaWebsiteRow));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verifyNoInteractions(tavilySearch);
        verifyNoInteractions(writer);
    }

    @Test
    void onDataQualityCheck_concertField_isNeverEnriched() {
        DataQuality concertRow = new DataQuality(32L, "concert", "c1", "ticketUrl", "missing", "non_severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(concertRow));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of()));

        verifyNoInteractions(tavilySearch);
        verifyNoInteractions(writer);
    }

    @Test
    void onDataQualityCheck_salaCoordinatesAreSkippedByTavilyAndLlm() {
        DataQuality latRow = new DataQuality(38L, "sala", "s1", "lat", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality lngRow = new DataQuality(39L, "sala", "s1", "lng", "missing", "severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(latRow, lngRow));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verifyNoInteractions(tavilySearch);
        verifyNoInteractions(entityEnrichmentPort);
        verifyNoInteractions(writer);
    }

    @Test
    void onDataQualityCheck_salaImageUrl_isSkipped() {
        DataQuality salaImageRow = new DataQuality(40L, "sala", "s1", "image_url", "missing", "non_severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(salaImageRow));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verifyNoInteractions(tavilySearch);
        verifyNoInteractions(writer);
    }

    @Test
    void onDataQualityCheck_artistImageUrl_isSkipped() {
        DataQuality artistImageRow = new DataQuality(41L, "artist", "a1", "image_url", "missing", "non_severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(artistImageRow));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        verifyNoInteractions(tavilySearch);
        verifyNoInteractions(writer);
    }

    @Test
    void onDataQualityCheck_artistWebsite_isSkipped() {
        DataQuality artistWebsiteRow = new DataQuality(42L, "artist", "a1", "website", "missing", "non_severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(artistWebsiteRow));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        verifyNoInteractions(tavilySearch);
        verifyNoInteractions(writer);
    }

    // =====================================================================
    // WU-3 NEW TESTS — Task 3.2
    // =====================================================================

    // --- Batching: one enrich call per entity ---

    @Test
    void onDataQualityCheck_batchesAllFieldsOfOneEntity_callsEnrichOnce() {
        // sala s1 has TWO missing fields → 2 Tavily calls, 1 enrich call
        DataQuality addressRow = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality descRow = new DataQuality(2L, "sala", "s1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(addressRow, descRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        // enrich already stubbed to empty() in setUp

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verify(tavilySearch, times(2)).search(anyString(), any());
        verify(entityEnrichmentPort, times(1)).enrich(any());
    }

    @Test
    void onDataQualityCheck_twoEntities_callsEnrichTwice() {
        DataQuality salaRow = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality artistRow = new DataQuality(2L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(salaRow, artistRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of("a1")));

        verify(entityEnrichmentPort, times(2)).enrich(any());
    }

    // --- city / province passed into request for salas ---

    @Test
    void onDataQualityCheck_passesCityProvinceIntoRequest() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", "Carrer de la Nou de la Rambla 113",
            "Barcelona", "Barcelona", 41.375, 2.169, null, null, null, FIXED_NOW);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));

        DataQuality addressRow = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(addressRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        ArgumentCaptor<EntityEnrichmentRequest> captor = ArgumentCaptor.forClass(EntityEnrichmentRequest.class);
        verify(entityEnrichmentPort).enrich(captor.capture());
        EntityEnrichmentRequest req = captor.getValue();
        assertThat(req.city()).isEqualTo("Barcelona");
        assertThat(req.province()).isEqualTo("Barcelona");
    }

    // --- Artist has no city/province ---

    @Test
    void onDataQualityCheck_artistHasNoCityProvince_requestCityAndProvinceNull() {
        Artist artist = new Artist("a1", "Bad Bunny", "Reggaeton", null, null, null, null, FIXED_NOW);
        when(artistRepository.findAll()).thenReturn(List.of(artist));

        DataQuality genreRow = new DataQuality(1L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(genreRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        ArgumentCaptor<EntityEnrichmentRequest> captor = ArgumentCaptor.forClass(EntityEnrichmentRequest.class);
        verify(entityEnrichmentPort).enrich(captor.capture());
        EntityEnrichmentRequest req = captor.getValue();
        assertThat(req.city()).isNull();
        assertThat(req.province()).isNull();
    }

    // --- Artist contextHints: venue cities derived from concerts ---

    @Test
    void onDataQualityCheck_artistContextHints_carriesVenueCities() {
        Artist artist = new Artist("a1", "Vetusta Morla", null, null, null, null, null, FIXED_NOW);
        when(artistRepository.findAll()).thenReturn(List.of(artist));

        SalaConcierto madridSala = new SalaConcierto("s1", "Sala Madrid", null, "Madrid", "Madrid", null, null, null, null, null, FIXED_NOW);
        SalaConcierto granadaSala = new SalaConcierto("s2", "Sala Granada", null, "Granada", "Granada", null, null, null, null, null, FIXED_NOW);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(madridSala, granadaSala));

        // Two concerts featuring artist a1 at different venues
        Concert c1 = new Concert("c1", "s1", List.of("a1"), LocalDate.now(), "20:00", null, null, FIXED_NOW);
        Concert c2 = new Concert("c2", "s2", List.of("a1"), LocalDate.now(), "21:00", null, null, FIXED_NOW);
        when(concertRepository.findAllIncludingBlocked()).thenReturn(List.of(c1, c2));

        DataQuality genreRow = new DataQuality(1L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(genreRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        ArgumentCaptor<EntityEnrichmentRequest> captor = ArgumentCaptor.forClass(EntityEnrichmentRequest.class);
        verify(entityEnrichmentPort).enrich(captor.capture());
        EntityEnrichmentRequest req = captor.getValue();
        assertThat(req.contextHints()).containsKey("ciudades_donde_actua");
        String cities = req.contextHints().get("ciudades_donde_actua");
        assertThat(cities).contains("Madrid");
        assertThat(cities).contains("Granada");
    }

    @Test
    void onDataQualityCheck_salaContextHints_isEmpty() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Test", null, "Sevilla", "Sevilla", null, null, null, null, null, FIXED_NOW);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));

        DataQuality descRow = new DataQuality(1L, "sala", "s1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(descRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        ArgumentCaptor<EntityEnrichmentRequest> captor = ArgumentCaptor.forClass(EntityEnrichmentRequest.class);
        verify(entityEnrichmentPort).enrich(captor.capture());
        assertThat(captor.getValue().contextHints()).isEmpty();
    }

    // --- Confidence routing ---

    @Test
    void onDataQualityCheck_highConfidenceNonSevere_autoApproved() {
        DataQuality descRow = new DataQuality(1L, "sala", "s1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(descRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        when(entityEnrichmentPort.enrich(any())).thenReturn(new EnrichedEntityResult(Map.of(
            "description", new EnrichedFieldValue("A great venue", "https://source.com", 0.88)
        )));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verify(salaConciertoWritePort).updateField("s1", "description", "A great venue", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified(eq("salas-concierto"), eq(FIXED_NOW));
        verify(writer).updateSuggestion(1L, "auto_approved", "A great venue", "https://source.com", 0.88, FIXED_NOW);
    }

    @Test
    void onDataQualityCheck_highConfidenceSevere_autoFound() {
        // sala.address is severe → even at 0.92, no entity write
        DataQuality addressRow = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(addressRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        when(entityEnrichmentPort.enrich(any())).thenReturn(new EnrichedEntityResult(Map.of(
            "address", new EnrichedFieldValue("Carrer de la Nou 113", "https://source.com", 0.92)
        )));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verifyNoInteractions(salaConciertoWritePort);
        verify(writer).updateSuggestion(1L, "auto_found", "Carrer de la Nou 113", "https://source.com", 0.92, FIXED_NOW);
    }

    @Test
    void onDataQualityCheck_midConfidenceNonSevere_autoFound() {
        // 0.75 >= floor(0.7), < autoFill(0.8) → auto_found
        DataQuality descRow = new DataQuality(1L, "artist", "a1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(descRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        when(entityEnrichmentPort.enrich(any())).thenReturn(new EnrichedEntityResult(Map.of(
            "description", new EnrichedFieldValue("Artist bio text", "https://bio.com", 0.75)
        )));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        verifyNoInteractions(artistWritePort);
        verify(writer).updateSuggestion(1L, "auto_found", "Artist bio text", "https://bio.com", 0.75, FIXED_NOW);
    }

    @Test
    void onDataQualityCheck_confidenceBelowFloor_fieldRemainsMissing() {
        DataQuality descRow = new DataQuality(1L, "artist", "a1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(descRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        when(entityEnrichmentPort.enrich(any())).thenReturn(new EnrichedEntityResult(Map.of(
            "description", new EnrichedFieldValue("Some content", "https://low.com", 0.65)
        )));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        verify(writer, never()).updateSuggestion(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void onDataQualityCheck_nullValueFromEnrich_fieldRemainsMissing() {
        DataQuality descRow = new DataQuality(1L, "artist", "a1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(descRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        when(entityEnrichmentPort.enrich(any())).thenReturn(new EnrichedEntityResult(Map.of(
            "description", new EnrichedFieldValue(null, null, 0.0)
        )));

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        verify(writer, never()).updateSuggestion(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void onDataQualityCheck_emptyEnrichResult_fieldRemainsMissing() {
        DataQuality descRow = new DataQuality(1L, "sala", "s1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(descRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        // setUp already stubs entityEnrichmentPort.enrich to return empty()

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        verify(writer, never()).updateSuggestion(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    // --- Exception resilience ---

    @Test
    void onDataQualityCheck_enrichException_doesNotPropagate() {
        DataQuality addressRow = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(addressRow));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());
        when(entityEnrichmentPort.enrich(any())).thenThrow(new RuntimeException("LLM timeout"));

        assertThatNoException().isThrownBy(() ->
            useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()))
        );

        verify(writer, never()).updateSuggestion(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void onDataQualityCheck_tavilyExceptionPerField_partialEvidenceStillEnriches() {
        // Entity s1 has 2 missing fields; Tavily throws for address, succeeds for description
        DataQuality addressRow = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality descRow = new DataQuality(2L, "sala", "s1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(addressRow, descRow));
        TavilyResult good = new TavilyResult("Title", "https://src.com", "content", 0.9);
        when(tavilySearch.search(anyString(), any()))
            .thenThrow(new RuntimeException("Tavily error for address"))
            .thenReturn(List.of(good));
        // enrich is still called (with partial evidence)
        when(entityEnrichmentPort.enrich(any())).thenReturn(EnrichedEntityResult.empty());

        assertThatNoException().isThrownBy(() ->
            useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()))
        );

        // enrich was still called despite partial Tavily failure
        verify(entityEnrichmentPort, times(1)).enrich(any());
    }

    // --- LLM cap enforcement ---

    @Test
    void onDataQualityCheck_enforcesLlmCallCap() {
        useCase = new DataQualityUseCase(repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 2, artistRepository, salaRepository, 0.8,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase);

        // 3 distinct entities → cap=2 → only 2 enrich calls
        DataQuality s1 = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality s2 = new DataQuality(2L, "sala", "s2", "address", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality a1 = new DataQuality(3L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(s1, s2, a1));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1", "s2"), List.of("a1")));

        verify(entityEnrichmentPort, times(2)).enrich(any());
    }

    @Test
    void onDataQualityCheck_prioritizesHighestImpactSevereRowsFirst() {
        useCase = new DataQualityUseCase(repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 1, artistRepository, salaRepository, 0.8,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase);

        DataQuality lowImpact = new DataQuality(1L, "sala", "low-impact", "address", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality highImpact = new DataQuality(2L, "sala", "high-impact", "address", "missing", "severe", null, null, null, FIXED_NOW);
        SalaConcierto lowSala = new SalaConcierto("low-impact", "Low Impact", null, "Granada", "Granada", null, null, null, null, null, FIXED_NOW);
        SalaConcierto highSala = new SalaConcierto("high-impact", "High Impact", null, "Granada", "Granada", null, null, null, null, null, FIXED_NOW);
        Concert high1 = new Concert("c-high-1", "high-impact", List.of("a1"), LocalDate.parse("2099-01-01"), null, null, "https://source.com", FIXED_NOW);
        Concert high2 = new Concert("c-high-2", "high-impact", List.of("a2"), LocalDate.parse("2099-01-02"), null, null, "https://source.com", FIXED_NOW);
        Concert low1 = new Concert("c-low-1", "low-impact", List.of("a3"), LocalDate.parse("2099-01-03"), null, null, "https://source.com", FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(lowImpact, highImpact));
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(lowSala, highSala));
        when(concertRepository.findAllIncludingBlocked()).thenReturn(List.of(high1, high2, low1));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("low-impact", "high-impact"), List.of()));

        ArgumentCaptor<EntityEnrichmentRequest> captor = ArgumentCaptor.forClass(EntityEnrichmentRequest.class);
        verify(entityEnrichmentPort, times(1)).enrich(captor.capture());
        assertThat(captor.getValue().entityId()).isEqualTo("high-impact");
    }

    // --- Sala-first ordering preserved as tie-breaker ---

    @Test
    void onDataQualityCheck_prioritizesSalasOverArtists() {
        useCase = new DataQualityUseCase(repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 1, artistRepository, salaRepository, 0.8,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase);

        DataQuality salaRow = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        DataQuality artistRow = new DataQuality(2L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED_NOW);

        when(repository.findByStatus("missing")).thenReturn(List.of(artistRow, salaRow)); // artist first in input
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of("a1")));

        // cap=1: only sala is processed
        ArgumentCaptor<EntityEnrichmentRequest> captor = ArgumentCaptor.forClass(EntityEnrichmentRequest.class);
        verify(entityEnrichmentPort, times(1)).enrich(captor.capture());
        assertThat(captor.getValue().entityType()).isEqualTo("sala");
        verify(writer, never()).updateSuggestion(eq(2L), anyString(), anyString(), anyString(), any(), any());
    }

    // --- Spanish query terms ---

    @Test
    void onDataQualityCheck_spanishQueryTermForAddress() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", null, null, null, null, null, FIXED_NOW);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));

        DataQuality row = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(row));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(tavilySearch.search(queryCaptor.capture(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        assertThat(queryCaptor.getValue()).contains("dirección");
    }

    @Test
    void onDataQualityCheck_spanishQueryTermForDescription() {
        Artist artist = new Artist("a1", "Vetusta Morla", null, null, null, null, null, FIXED_NOW);
        when(artistRepository.findAll()).thenReturn(List.of(artist));

        DataQuality row = new DataQuality(1L, "artist", "a1", "description", "missing", "non_severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(row));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(tavilySearch.search(queryCaptor.capture(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        assertThat(queryCaptor.getValue()).contains("descripción");
    }

    @Test
    void onDataQualityCheck_spanishQueryTermForGenre() {
        Artist artist = new Artist("a1", "La Oreja de Van Gogh", null, null, null, null, null, FIXED_NOW);
        when(artistRepository.findAll()).thenReturn(List.of(artist));

        DataQuality row = new DataQuality(1L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(row));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(tavilySearch.search(queryCaptor.capture(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")));

        assertThat(queryCaptor.getValue()).contains("género musical");
    }

    @Test
    void onDataQualityCheck_queryFallbackWhenCityNull() {
        // artist has no city/province → no NPE in buildQuery
        Artist artist = new Artist("a1", "Rosalía", null, null, null, null, null, FIXED_NOW);
        when(artistRepository.findAll()).thenReturn(List.of(artist));

        DataQuality row = new DataQuality(1L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(row));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        assertThatNoException().isThrownBy(() ->
            useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of(), List.of("a1")))
        );
    }

    // --- knownFields contains all non-null entity fields ---

    @Test
    void onDataQualityCheck_knownFieldsContainsNonNullEntityFields_forSala() {
        SalaConcierto sala = new SalaConcierto("s1", "Sala Apolo", null, "Barcelona", "Barcelona", 41.375, 2.169, null, null, "https://src.com", FIXED_NOW);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));

        DataQuality row = new DataQuality(1L, "sala", "s1", "address", "missing", "severe", null, null, null, FIXED_NOW);
        when(repository.findByStatus("missing")).thenReturn(List.of(row));
        when(tavilySearch.search(anyString(), any())).thenReturn(List.of());

        useCase.onDataQualityCheck(new DataQualityCheckEvent(List.of("s1"), List.of()));

        ArgumentCaptor<EntityEnrichmentRequest> captor = ArgumentCaptor.forClass(EntityEnrichmentRequest.class);
        verify(entityEnrichmentPort).enrich(captor.capture());
        Map<String, String> known = captor.getValue().knownFields();
        assertThat(known).containsKey("name");
        assertThat(known.get("name")).isEqualTo("Sala Apolo");
        assertThat(known).containsKey("city");
        assertThat(known.get("city")).isEqualTo("Barcelona");
        // null fields must not be present
        assertThat(known).doesNotContainKey("address");
        assertThat(known).doesNotContainKey("imageUrl");
    }
}
