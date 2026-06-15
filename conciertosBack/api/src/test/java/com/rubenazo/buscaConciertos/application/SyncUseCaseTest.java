package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.ExistingDataReaderPort;
import com.rubenazo.buscaConciertos.scraper.application.usecases.ArtistEnrichmentUseCase;
import com.rubenazo.buscaConciertos.scraper.application.usecases.ConcertScraperUseCase;
import com.rubenazo.buscaConciertos.scraper.application.usecases.VenueScraperUseCase;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedArtist;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedConcert;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncUseCaseTest {

    @Mock private ExistingDataReaderPort existingDataReaderPort;
    @Mock private ConcertScraperUseCase concertScraperUseCase;
    @Mock private VenueScraperUseCase venueScraperUseCase;
    @Mock private ArtistEnrichmentUseCase artistEnrichmentUseCase;
    @Mock private SalaConciertoWritePort salaWriter;
    @Mock private ArtistWritePort artistWriter;
    @Mock private ConcertWritePort concertWriter;
    @Mock private SyncMetadataWritePort syncMetaWriter;
    @Mock private DataQualityWritePort qualityWriter;
    @Mock private SyncRunPort syncRunPort;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager txManager;
    @Mock private Clock clock;
    @Mock private VenueGeocodingUseCase venueGeocodingUseCase;

    private SyncUseCase syncUseCase;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(Instant.parse("2026-05-30T10:00:00Z"));
        when(clock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
        org.mockito.Mockito.lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        org.mockito.Mockito.lenient().when(venueGeocodingUseCase.geocodeIfNeeded(any(SalaConcierto.class), any(Instant.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        syncUseCase = new SyncUseCase(
            existingDataReaderPort, concertScraperUseCase, venueScraperUseCase,
            artistEnrichmentUseCase, salaWriter, artistWriter, concertWriter,
            syncMetaWriter, qualityWriter, syncRunPort, eventPublisher, txTemplate, clock,
            venueGeocodingUseCase, 3
        );
    }

    @Test
    void execute_completesRunOnSuccess() {
        String runId = "run-1";
        setupHappyPath();

        syncUseCase.execute(runId);

        verify(syncRunPort).complete(eq(runId), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void execute_upsertsSalasBeforeArtistsBeforeConcerts() {
        String runId = "run-1";
        setupHappyPath();

        syncUseCase.execute(runId);

        InOrder order = inOrder(salaWriter, artistWriter, concertWriter);
        order.verify(salaWriter).upsert(any(SalaConcierto.class));
        order.verify(artistWriter, atLeastOnce()).upsert(any());
        order.verify(concertWriter).upsert(any(Concert.class));
    }

    @Test
    void execute_deletesBeforeDateBeforeUpserts() {
        String runId = "run-1";
        setupHappyPath();

        syncUseCase.execute(runId);

        InOrder order = inOrder(concertWriter, salaWriter);
        order.verify(concertWriter).deleteBeforeDate(any(LocalDate.class));
        order.verify(salaWriter).upsert(any(SalaConcierto.class));
    }

    @Test
    void execute_usesConsistentTodayForDeleteWhenClockCrossesMidnight() {
        // doExecute must capture the wall clock ONCE. If it re-reads the clock for
        // deleteBeforeDate and the run happens to cross midnight, it would delete concerts
        // dated on the very day it just ingested (from == start-of-run day).
        setupHappyPath();
        when(clock.instant()).thenReturn(
            Instant.parse("2026-06-08T23:59:59Z"),   // captured at start of doExecute
            Instant.parse("2026-06-09T00:00:00Z"));  // any later read, now past midnight

        syncUseCase.execute("run-1", LocalDate.of(2026, 6, 8), LocalDate.of(2026, 9, 8));

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(concertWriter).deleteBeforeDate(captor.capture());
        assertThat(captor.getValue()).isEqualTo(LocalDate.of(2026, 6, 8));
    }

    @Test
    void execute_geocodesNewVenueBeforeUpsert() {
        String runId = "run-1";
        setupHappyPath();
        when(venueGeocodingUseCase.geocodeIfNeeded(any(SalaConcierto.class), any(Instant.class)))
            .thenAnswer(invocation -> {
                SalaConcierto sala = invocation.getArgument(0);
                Instant updatedAt = invocation.getArgument(1);
                return new SalaConcierto(
                    sala.id(), sala.name(), sala.address(), sala.city(), sala.province(),
                    41.3735, 2.1700, sala.imageUrl(), sala.description(), sala.sourceUrl(), updatedAt
                );
            });

        syncUseCase.execute(runId);

        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        SalaConcierto upserted = salaCaptor.getValue();
        assertThat(upserted.lat()).isEqualTo(41.3735);
        assertThat(upserted.lng()).isEqualTo(2.1700);
    }

    @Test
    void execute_failsRunOnScraperException() {
        String runId = "run-1";
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenThrow(new RuntimeException("Network error"));

        try {
            syncUseCase.execute(runId);
        } catch (RuntimeException ignored) {}

        verify(syncRunPort).fail(eq(runId), anyString());
        verify(syncRunPort, never()).complete(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void execute_swallowsEnrichmentErrors() {
        String runId = "run-1";
        setupHappyPath();
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenThrow(new RuntimeException("Enrichment failed"));

        // Should not throw
        syncUseCase.execute(runId);

        verify(syncRunPort).complete(eq(runId), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    // --- Recovery / robustness (Judgment Day 2026-06-08) ---

    @Test
    void execute_failThrowingIsSwallowedAndOriginalCausePropagates() {
        // doExecute throws ("Network error") AND marking the run failed also throws ("DB locked").
        // The hardened catch must swallow the secondary fail() failure so the ORIGINAL cause
        // still propagates — without it, "DB locked" would mask the real reason and the run
        // would be left 'running' silently in the @Async thread.
        String runId = "run-1";
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenThrow(new RuntimeException("Network error"));
        doThrow(new RuntimeException("DB locked")).when(syncRunPort).fail(anyString(), anyString());

        assertThatThrownBy(() -> syncUseCase.execute(runId))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Network error");

        verify(syncRunPort).fail(eq(runId), anyString());
        verify(syncRunPort, never()).complete(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void execute_writeFailureMidBatchFailsRunAndDoesNotComplete() {
        String runId = "run-1";
        setupHappyPath();
        doThrow(new RuntimeException("DB write boom"))
            .when(salaWriter).upsert(any(SalaConcierto.class));

        assertThatThrownBy(() -> syncUseCase.execute(runId))
            .isInstanceOf(RuntimeException.class);

        verify(syncRunPort).fail(eq(runId), anyString());
        verify(syncRunPort, never()).complete(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    // --- Phase 3.1 [RED]: buildQualityIssues assigns severity from FieldSeverity ---

    @Test
    void buildQualityIssues_assignsSeverityFromFieldSeverityMap() {
        String runId = "run-1";
        setupHappyPath();

        // Override: concert has missing price; price is intentionally ignored by data_quality.
        ScrapedConcert concert = new ScrapedConcert(
            "concert-1", "sala-1", List.of("artist-1"),
            LocalDate.of(2026, 6, 15), "20:00", null,
            "https://example.com/concert-1",
            "Sala One", "madrid", "Artist One", "rock",
            "https://img.jpg", "/madrid/locales/sala-one"
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(concert));

        // Override: venue returns one with missing address (severe); image_url is ignored by data_quality.
        ScrapedVenue venue = new ScrapedVenue(
            "madrid-sala-one", "Sala One", null,  // address=null -> severe
            "Madrid", "madrid", 40.4, -3.7, null, null,
            "https://example.com/madrid/locales/sala-one"
        );
        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of(venue));

        // Artist with genre but missing description -> description is non_severe.
        // image_url and website are ignored by data_quality.
        ScrapedArtist artist = new ScrapedArtist(
            "artist-one", "Artist One", "rock", null, null,
            "https://example.com/concert-1", null
        );
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of(artist));

        syncUseCase.execute(runId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DataQuality>> captor = ArgumentCaptor.forClass(List.class);
        verify(qualityWriter).saveAll(captor.capture());

        List<DataQuality> issues = captor.getValue();
        // sala missing address -> severe
        DataQuality addressIssue = issues.stream()
            .filter(i -> "sala".equals(i.entityType()) && "address".equals(i.field()))
            .findFirst().orElseThrow(() -> new AssertionError("No address issue found"));
        assertThat(addressIssue.severity()).isEqualTo("severe");

        // artist missing description -> non_severe
        DataQuality descriptionIssue = issues.stream()
            .filter(i -> "artist".equals(i.entityType()) && "description".equals(i.field()))
            .findFirst().orElseThrow(() -> new AssertionError("No description issue found"));
        assertThat(descriptionIssue.severity()).isEqualTo("non_severe");

        assertThat(issues)
            .noneMatch(i -> "image_url".equals(i.field()))
            .noneMatch(i -> "website".equals(i.field()))
            .noneMatch(i -> "price".equals(i.field()));
    }

    // --- Phase 3.3 [RED]: venue detail failure creates partial sala and concert ---

    @Test
    void venueDetailFailure_createsPartialSalaAndConcert() {
        String runId = "run-1";
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertWriter.deleteBeforeDate(any())).thenReturn(0);

        ScrapedConcert concert = new ScrapedConcert(
            "concert-1", "madrid-sala-failure", List.of("artist-1"),
            LocalDate.of(2026, 6, 15), "20:00", "20€",
            "https://example.com/concert-1",
            "Sala Failure", "madrid", "Artist One", "rock",
            "https://img.jpg", "/madrid/locales/sala-failure"
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(concert));

        // Venue detail scraping returns empty — simulates failure
        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of());

        ScrapedArtist artist = new ScrapedArtist(
            "artist-one", "Artist One", "rock", null, null,
            "https://example.com/concert-1", "A great artist"
        );
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of(artist));

        syncUseCase.execute(runId);

        // Partial sala was written via insertIfAbsent — never a blind upsert that
        // could overwrite an existing sala's fields with nulls
        verify(salaWriter, never()).upsert(any(SalaConcierto.class));
        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).insertIfAbsent(salaCaptor.capture());
        SalaConcierto partialSala = salaCaptor.getValue();
        assertThat(partialSala.name()).isEqualTo("Sala Failure");
        assertThat(partialSala.province()).isEqualTo("madrid");
        assertThat(partialSala.address()).isNull();
        assertThat(partialSala.lat()).isNull();
        assertThat(partialSala.lng()).isNull();

        // Severe data quality rows for address, lat, lng
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DataQuality>> qualityCaptor = ArgumentCaptor.forClass(List.class);
        verify(qualityWriter).saveAll(qualityCaptor.capture());
        List<DataQuality> issues = qualityCaptor.getValue();
        assertThat(issues).anySatisfy(dq ->
            assertThat(dq.entityType()).isEqualTo("sala")
        );
        List<DataQuality> severeIssues = issues.stream()
            .filter(i -> "sala".equals(i.entityType()) && "severe".equals(i.severity()))
            .toList();
        assertThat(severeIssues).extracting(DataQuality::field)
            .contains("address", "lat", "lng");

        // Concert was persisted
        verify(concertWriter).upsert(any(Concert.class));

        // Run completed
        verify(syncRunPort).complete(eq(runId), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    // --- Partial-sala overwrite fix [RED]: an unresolvable venue href whose
    // province+name slug matches an EXISTING venue must link the concert to that
    // venue — never create a partial sala that would overwrite it with nulls ---

    @Test
    void unresolvableVenueHref_nameMatchesExistingVenue_linksConcertWithoutTouchingSala() {
        String runId = "run-1";
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of("granada-lemon-rock"));
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertWriter.deleteBeforeDate(any())).thenReturn(0);

        // venueHref null → resolveVenueId cannot resolve, but the venue exists in DB
        ScrapedConcert concert = new ScrapedConcert(
            "concert-117435", null, List.of("pacifica"),
            LocalDate.of(2026, 6, 20), "21:00", "15€",
            "https://www.conciertosengranada.es/conciertos/117435-pacifica",
            "Lemon Rock", "granada", "Pacifica", "rock",
            null, null
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(concert));
        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of());
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of());

        syncUseCase.execute(runId);

        // The existing sala must not be written at all
        verify(salaWriter, never()).upsert(any(SalaConcierto.class));
        verify(salaWriter, never()).insertIfAbsent(any(SalaConcierto.class));

        // The concert links to the existing venue id
        ArgumentCaptor<Concert> concertCaptor = ArgumentCaptor.forClass(Concert.class);
        verify(concertWriter).upsert(concertCaptor.capture());
        assertThat(concertCaptor.getValue().salaConciertoId()).isEqualTo("granada-lemon-rock");

        // And no severe sala issues were emitted for it
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DataQuality>> qualityCaptor = ArgumentCaptor.forClass(List.class);
        verify(qualityWriter).saveAll(qualityCaptor.capture());
        assertThat(qualityCaptor.getValue())
            .noneMatch(i -> "sala".equals(i.entityType()) && "granada-lemon-rock".equals(i.entityId()));
    }

    // --- Full-sala coords gap [RED]: a fully scraped sala without lat/lng must get
    // severe quality rows too — otherwise it stays invisible to the admin pipeline
    // while being served broken (Le Bukowski case) ---

    @Test
    void fullSalaWithoutCoords_getsSevereLatLngQualityIssues() {
        String runId = "run-1";
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertWriter.deleteBeforeDate(any())).thenReturn(0);

        ScrapedConcert concert = new ScrapedConcert(
            "concert-1", "gipuzkoa-le-bukowski", List.of("artist-1"),
            LocalDate.of(2026, 6, 15), "20:00", "20€",
            "https://example.com/concert-1",
            "Le Bukowski", "gipuzkoa", "Artist One", "rock",
            "https://img.jpg", "/gipuzkoa/locales/le-bukowski"
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(concert));

        // Venue scraped fine, with address — but no extractable coordinates
        ScrapedVenue venue = new ScrapedVenue(
            "gipuzkoa-le-bukowski", "Le Bukowski", "Egia Kalea, 18",
            "Donostia", "gipuzkoa", null, null, null, null,
            "https://example.com/gipuzkoa/locales/le-bukowski"
        );
        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of(venue));
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of());

        syncUseCase.execute(runId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DataQuality>> captor = ArgumentCaptor.forClass(List.class);
        verify(qualityWriter).saveAll(captor.capture());
        List<DataQuality> issues = captor.getValue();

        List<DataQuality> salaIssues = issues.stream()
            .filter(i -> "sala".equals(i.entityType()) && "gipuzkoa-le-bukowski".equals(i.entityId()))
            .toList();
        assertThat(salaIssues).extracting(DataQuality::field)
            .contains("lat", "lng")
            .doesNotContain("address");
        assertThat(salaIssues).allSatisfy(dq ->
            assertThat(dq.severity()).isEqualTo("severe"));
    }

    // --- Phase 3.5 [RED]: stub artist gets severe data_quality for genre ---

    @Test
    void stubArtist_getsSevereDataQualityForGenre() {
        String runId = "run-1";
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertWriter.deleteBeforeDate(any())).thenReturn(0);

        ScrapedConcert concert = new ScrapedConcert(
            "concert-1", "sala-1", List.of("banda-x"),
            LocalDate.of(2026, 6, 15), "20:00", "20€",
            "https://example.com/concert-1",
            "Sala One", "madrid", "Banda X", "rock",
            "https://img.jpg", "/madrid/locales/sala-one"
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(concert));

        ScrapedVenue venue = new ScrapedVenue(
            "madrid-sala-one", "Sala One", "Calle Mayor 1",
            "Madrid", "madrid", 40.4, -3.7, null, null,
            "https://example.com/madrid/locales/sala-one"
        );
        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of(venue));

        // Enrichment fails — banda-x becomes a stub artist
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of());

        syncUseCase.execute(runId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DataQuality>> captor = ArgumentCaptor.forClass(List.class);
        verify(qualityWriter).saveAll(captor.capture());
        List<DataQuality> issues = captor.getValue();

        DataQuality genreIssue = issues.stream()
            .filter(i -> "artist".equals(i.entityType()) && "banda-x".equals(i.entityId()) && "genre".equals(i.field()))
            .findFirst().orElseThrow(() -> new AssertionError("No genre issue for banda-x"));
        assertThat(genreIssue.severity()).isEqualTo("severe");
    }

    // --- Phase 3.7 [RED]: sync run counter includes partial sala concerts ---

    @Test
    void syncRunCounter_includesPartialSalaConcerts() {
        String runId = "run-1";
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertWriter.deleteBeforeDate(any())).thenReturn(0);

        ScrapedConcert concert = new ScrapedConcert(
            "concert-partial", "madrid-sala-fail", List.of("artist-1"),
            LocalDate.of(2026, 6, 15), "20:00", "20€",
            "https://example.com/concert-partial",
            "Sala Fail", "madrid", "Artist One", "rock",
            "https://img.jpg", "/madrid/locales/sala-fail"
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(concert));

        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of());

        ScrapedArtist artist = new ScrapedArtist(
            "artist-one", "Artist One", "rock", null, null,
            "https://example.com/concert-partial", "A great artist"
        );
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of(artist));

        syncUseCase.execute(runId);

        // The count passed to complete() must include the partial sala concert
        ArgumentCaptor<Integer> concertsCountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(syncRunPort).complete(eq(runId), anyInt(), anyInt(), concertsCountCaptor.capture(), anyInt(), anyInt());
        assertThat(concertsCountCaptor.getValue()).isEqualTo(1);
    }

    // --- A1: null-date scraped concert must be skipped, not crash the sync ---

    @Test
    void execute_nullDateConcert_isSkippedAndSyncCompletes() {
        String runId = "run-null-date";
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertWriter.deleteBeforeDate(any())).thenReturn(0);

        // One concert with null date, one with a valid date
        ScrapedConcert nullDateConcert = new ScrapedConcert(
            "concert-null-date", null, List.of("artist-1"),
            null, null, null,
            "/madrid/conciertos/concert-null-date/999",
            "Sala Bad", "madrid", "Artist Bad", null,
            null, "/madrid/locales/sala-bad"
        );
        ScrapedConcert validConcert = new ScrapedConcert(
            "concert-valid", null, List.of("artist-1"),
            LocalDate.of(2026, 6, 15), "20:00", "20€",
            "/madrid/conciertos/concert-valid/1000",
            "Sala Good", "madrid", "Artist Good", "rock",
            null, "/madrid/locales/sala-good"
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(nullDateConcert, validConcert));

        ScrapedVenue venue = new ScrapedVenue(
            "madrid-sala-good", "Sala Good", "Calle 1",
            "Madrid", "madrid", 40.4, -3.7, null, null,
            "https://example.com/madrid/locales/sala-good"
        );
        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of(venue));
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of());

        // Must not throw; run must complete
        syncUseCase.execute(runId);

        verify(syncRunPort).complete(eq(runId), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        // The null-date concert must NEVER be passed to concertWriter.upsert
        ArgumentCaptor<Concert> concertCaptor = ArgumentCaptor.forClass(Concert.class);
        verify(concertWriter, org.mockito.Mockito.atMost(1)).upsert(concertCaptor.capture());
        concertCaptor.getAllValues().forEach(c ->
            assertThat(c.id()).isNotEqualTo("concert-null-date")
        );
    }

    // --- B1: resolveVenueId must slugify, not return raw URL segments ---

    @Test
    void execute_nonCleanVenueHref_resolvesToSlugifiedVenueId() {
        String runId = "run-slug";
        // The "existing" venue has a slugified id (as stored in DB / as scraped)
        String existingVenueId = "madrid-sala-espanol"; // slugify("Madrid") + "-" + slugify("Sala Español")
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of(existingVenueId));
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertWriter.deleteBeforeDate(any())).thenReturn(0);

        // Concert whose venueHref has non-clean segments (uppercase province, accented venue name)
        ScrapedConcert concert = new ScrapedConcert(
            "concert-slug", null, List.of("artist-1"),
            LocalDate.of(2026, 6, 20), "21:00", "15€",
            "/Madrid/conciertos/concert-slug/2000",
            "Sala Español", "Madrid", "Artist One", "flamenco",
            null, "/Madrid/locales/Sala-Espanol"
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(concert));
        // Venue scraper returns nothing (the venue is already known by its slugified id)
        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of());
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of());

        syncUseCase.execute(runId);

        // The concert must be persisted (venue matched the existing slugified id)
        verify(concertWriter).upsert(any(Concert.class));
        verify(syncRunPort).complete(eq(runId), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    // Helper to set up a minimal happy path with one concert, venue, and artist
    private void setupHappyPath() {
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.enrichedArtistIds()).thenReturn(Set.of());
        when(concertWriter.deleteBeforeDate(any())).thenReturn(0);

        ScrapedConcert concert = new ScrapedConcert(
            "concert-1", "sala-1", List.of("artist-1"),
            LocalDate.of(2026, 6, 15), "20:00", "20€",
            "https://example.com/concert-1",
            "Sala One", "madrid", "Artist One", "rock",
            "https://img.jpg", "/madrid/locales/sala-one"
        );
        when(concertScraperUseCase.scrape(any(), any(), anyList()))
            .thenReturn(List.of(concert));

        ScrapedVenue venue = new ScrapedVenue(
            "madrid-sala-one", "Sala One", "Calle Mayor 1",
            "Madrid", "madrid", 40.4, -3.7, null, null,
            "https://example.com/madrid/locales/sala-one"
        );
        when(venueScraperUseCase.scrapeByUrls(anyList(), anyList()))
            .thenReturn(List.of(venue));

        ScrapedArtist artist = new ScrapedArtist(
            "artist-one", "Artist One", "rock", null, null,
            "https://example.com/concert-1", "A great artist"
        );
        when(artistEnrichmentUseCase.enrich(anyList(), anySet(), anyList()))
            .thenReturn(List.of(artist));
    }
}
