package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.out.AdminArea;
import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSnapshot;
import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSourcePort;
import com.rubenazo.buscaConciertos.application.ports.out.ArtistWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.ConcertWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.DataQualityWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.ReverseGeocodingPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoRepositoryPort;
import com.rubenazo.buscaConciertos.application.ports.out.SalaConciertoWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncMetadataWritePort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.ExistingDataReaderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlcalaSyncUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-05T10:00:00Z");
    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 7, 15);

    @Mock private AlcalaSourcePort alcalaSourcePort;
    @Mock private ReverseGeocodingPort reverseGeocodingPort;
    @Mock private SalaConciertoWritePort salaWriter;
    @Mock private ArtistWritePort artistWriter;
    @Mock private ConcertWritePort concertWriter;
    @Mock private SyncMetadataWritePort syncMetaWriter;
    @Mock private SyncRunPort syncRunPort;
    @Mock private DataQualityWritePort qualityWriter;
    @Mock private ExistingDataReaderPort existingDataReaderPort;
    @Mock private SalaConciertoRepositoryPort salaRepositoryPort;
    @Mock private ConcertRepositoryPort concertRepositoryPort;
    @Mock private PlatformTransactionManager txManager;
    @Mock private Clock clock;

    private AlcalaSyncUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        useCase = new AlcalaSyncUseCase(
            alcalaSourcePort,
            reverseGeocodingPort,
            new CrossSourceMatcher(),
            salaWriter,
            artistWriter,
            concertWriter,
            syncMetaWriter,
            syncRunPort,
            qualityWriter,
            existingDataReaderPort,
            salaRepositoryPort,
            concertRepositoryPort,
            new TransactionTemplate(txManager),
            clock
        );
    }

    @Test
    void executeWithValidRunIdDoesNotCallTryStartAndFetchesSnapshot() {
        setupEmptyExistingData();
        when(alcalaSourcePort.fetch(null, null)).thenReturn(happySnapshot());
        when(reverseGeocodingPort.reverse(40.4051, -3.7068))
            .thenReturn(Optional.of(new AdminArea("Alcalá de Henares", "Madrid")));

        useCase.execute("run-1");

        verify(syncRunPort, never()).tryStart();
        verify(alcalaSourcePort).fetch(null, null);
        verify(syncRunPort).complete(eq("run-1"), anyInt(), anyInt(), anyInt(), eq(0), eq(0));
    }

    @Test
    void executeUpsertsSalasBeforeArtistsBeforeConcerts() {
        setupHappyPath();

        useCase.execute("run-1");

        InOrder order = inOrder(salaWriter, artistWriter, concertWriter);
        order.verify(salaWriter).upsert(any(SalaConcierto.class));
        order.verify(artistWriter).upsert(any(Artist.class));
        order.verify(concertWriter).upsert(any(Concert.class));
    }

    @Test
    void executeCallsReverseOncePerNewVenue() {
        setupEmptyExistingData();
        SalaConcierto venue = venue("aem-venue-42", "Sala Riviera", 40.4051, -3.7068);
        AlcalaSnapshot snapshot = new AlcalaSnapshot(
            List.of(venue, venue),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(
                concert("aem-event-1", "aem-venue-42", EVENT_DATE, List.of("aem-band-101")),
                concert("aem-event-2", "aem-venue-42", EVENT_DATE.plusDays(1), List.of("aem-band-101"))
            )
        );
        when(alcalaSourcePort.fetch(null, null)).thenReturn(snapshot);
        when(reverseGeocodingPort.reverse(40.4051, -3.7068))
            .thenReturn(Optional.of(new AdminArea("Alcalá de Henares", "Madrid")));

        useCase.execute("run-1");

        verify(reverseGeocodingPort, times(1)).reverse(40.4051, -3.7068);
    }

    @Test
    void reverseMissWritesSevereDataQualityForNullProvince() {
        setupEmptyExistingData();
        when(alcalaSourcePort.fetch(null, null)).thenReturn(happySnapshot());
        when(reverseGeocodingPort.reverse(40.4051, -3.7068)).thenReturn(Optional.empty());

        useCase.execute("run-1");

        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        assertThat(salaCaptor.getValue().province()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DataQuality>> qualityCaptor = ArgumentCaptor.forClass(List.class);
        verify(qualityWriter).saveAll(qualityCaptor.capture());
        assertThat(qualityCaptor.getValue()).singleElement().satisfies(issue -> {
            assertThat(issue.entityType()).isEqualTo("sala");
            assertThat(issue.entityId()).isEqualTo("aem-venue-42");
            assertThat(issue.field()).isEqualTo("province");
            assertThat(issue.status()).isEqualTo("missing");
            assertThat(issue.severity()).isEqualTo("severe");
        });
    }

    @Test
    void reverseMissFallsBackToCityAndProvinceFromAddress() {
        setupEmptyExistingData();
        SalaConcierto venue = new SalaConcierto(
            "aem-venue-42",
            "Sala Address",
            "C/ Goya 6, 28807 Alcalá de Henares, Madrid",
            null,
            null,
            40.4051,
            -3.7068,
            null,
            null,
            null,
            NOW
        );
        when(alcalaSourcePort.fetch(null, null)).thenReturn(new AlcalaSnapshot(
            List.of(venue),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(concert("aem-event-abc", "aem-venue-42", EVENT_DATE, List.of("aem-band-101")))
        ));
        when(reverseGeocodingPort.reverse(40.4051, -3.7068)).thenReturn(Optional.empty());

        useCase.execute("run-1");

        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        assertThat(salaCaptor.getValue().city()).isEqualTo("Alcalá de Henares");
        assertThat(salaCaptor.getValue().province()).isEqualTo("Madrid");
    }

    @Test
    void reverseMissInfersProvinceForAlcalaAddressWithoutProvinceSegment() {
        setupEmptyExistingData();
        SalaConcierto venue = new SalaConcierto(
            "aem-venue-74",
            "EGO EVENTS",
            "C/Zaragoza 8, Alcalá de Henares",
            null,
            null,
            40.4051,
            -3.7068,
            null,
            null,
            null,
            NOW
        );
        when(alcalaSourcePort.fetch(null, null)).thenReturn(new AlcalaSnapshot(
            List.of(venue),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(concert("aem-event-abc", "aem-venue-74", EVENT_DATE, List.of("aem-band-101")))
        ));
        when(reverseGeocodingPort.reverse(40.4051, -3.7068)).thenReturn(Optional.empty());

        useCase.execute("run-1");

        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        assertThat(salaCaptor.getValue().address()).isEqualTo("C/Zaragoza 8, Alcalá de Henares");
        assertThat(salaCaptor.getValue().city()).isEqualTo("Alcalá de Henares");
        assertThat(salaCaptor.getValue().province()).isEqualTo("Madrid");
    }

    @Test
    void reverseMissAndNoAddressFallbackWritesNullProvinceAndNullCity() {
        setupEmptyExistingData();
        // Venue with no address and no geocoding result — province and city must be null
        SalaConcierto venue = new SalaConcierto(
            "aem-venue-99",
            "Sala Sin Datos",
            null,
            null,
            null,
            40.4051,
            -3.7068,
            null,
            null,
            null,
            NOW
        );
        when(alcalaSourcePort.fetch(null, null)).thenReturn(new AlcalaSnapshot(
            List.of(venue),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(concert("aem-event-abc", "aem-venue-99", EVENT_DATE, List.of("aem-band-101")))
        ));
        when(reverseGeocodingPort.reverse(40.4051, -3.7068)).thenReturn(Optional.empty());

        useCase.execute("run-1");

        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        assertThat(salaCaptor.getValue().city()).isNull();
        assertThat(salaCaptor.getValue().province()).isNull();
    }

    @Test
    void addressWithStreetAndProvinceOnlyWritesNullCity() {
        setupEmptyExistingData();
        // "Calle Mayor 1, Madrid" — previous="Calle Mayor 1" (address-like), last="Madrid" (known province)
        // isAddressLike(previous) is true → city must be null, NOT "Madrid"
        SalaConcierto venue = new SalaConcierto(
            "aem-venue-50",
            "Sala Solo Calle",
            "Calle Mayor 1, Madrid",
            null,
            null,
            40.4051,
            -3.7068,
            null,
            null,
            null,
            NOW
        );
        when(alcalaSourcePort.fetch(null, null)).thenReturn(new AlcalaSnapshot(
            List.of(venue),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(concert("aem-event-abc", "aem-venue-50", EVENT_DATE, List.of("aem-band-101")))
        ));
        when(reverseGeocodingPort.reverse(40.4051, -3.7068)).thenReturn(Optional.empty());

        useCase.execute("run-1");

        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        assertThat(salaCaptor.getValue().city()).isNull();
        assertThat(salaCaptor.getValue().province()).isEqualTo("Madrid");
    }

    @Test
    void executeFiltersConcertsOutsideDateWindow() {
        setupEmptyExistingData();
        AlcalaSnapshot snapshot = new AlcalaSnapshot(
            List.of(venue("aem-venue-42", "Sala Riviera", 40.4051, -3.7068)),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(
                concert("aem-event-inside", "aem-venue-42", LocalDate.of(2026, 7, 10), List.of("aem-band-101")),
                concert("aem-event-outside", "aem-venue-42", LocalDate.of(2026, 8, 1), List.of("aem-band-101"))
            )
        );
        when(alcalaSourcePort.fetch(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))).thenReturn(snapshot);
        when(reverseGeocodingPort.reverse(40.4051, -3.7068))
            .thenReturn(Optional.of(new AdminArea("Alcalá de Henares", "Madrid")));

        useCase.execute("run-1", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        ArgumentCaptor<Concert> concertCaptor = ArgumentCaptor.forClass(Concert.class);
        verify(concertWriter).upsert(concertCaptor.capture());
        assertThat(concertCaptor.getValue().id()).isEqualTo("aem-event-inside");
    }

    @Test
    void apiFailureFailsRunAndDoesNotWrite() {
        when(alcalaSourcePort.fetch(null, null)).thenThrow(new RuntimeException("AEM unavailable"));

        useCase.execute("run-1");

        verify(syncRunPort).fail(eq("run-1"), eq("AEM unavailable"));
        verifyNoInteractions(salaWriter, artistWriter, concertWriter, syncMetaWriter, qualityWriter);
        verify(syncRunPort, never()).complete(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void matchedVenueIsStillWrittenForOverwrite() {
        SalaConcierto existing = venue("madrid-sala-riviera", "Sala Riviera", 40.4048, -3.7069);
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of("madrid-sala-riviera"));
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(salaRepositoryPort.findAll()).thenReturn(List.of(existing));
        when(concertRepositoryPort.findAll()).thenReturn(List.of());
        when(alcalaSourcePort.fetch(null, null)).thenReturn(happySnapshot());

        useCase.execute("run-1");

        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        assertThat(salaCaptor.getValue().id()).isEqualTo("madrid-sala-riviera");
        verifyNoInteractions(reverseGeocodingPort);
    }

    @Test
    void nullDateConcertIsSkippedAndValidConcertIsWrittenWhenNoWindowGiven() {
        setupEmptyExistingData();
        SalaConcierto venue = venue("aem-venue-42", "Sala Riviera", 40.4051, -3.7068);
        Concert nullDateConcert = concert("aem-event-null-date", "aem-venue-42", null, List.of("aem-band-101"));
        Concert validConcert = concert("aem-event-valid", "aem-venue-42", EVENT_DATE, List.of("aem-band-101"));
        AlcalaSnapshot snapshot = new AlcalaSnapshot(
            List.of(venue),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(nullDateConcert, validConcert)
        );
        when(alcalaSourcePort.fetch(null, null)).thenReturn(snapshot);
        when(reverseGeocodingPort.reverse(40.4051, -3.7068))
            .thenReturn(Optional.of(new AdminArea("Alcalá de Henares", "Madrid")));

        useCase.execute("run-1");

        ArgumentCaptor<Concert> concertCaptor = ArgumentCaptor.forClass(Concert.class);
        verify(concertWriter).upsert(concertCaptor.capture());
        assertThat(concertCaptor.getValue().id()).isEqualTo("aem-event-valid");
        verify(concertWriter, never()).upsert(
            argThat(c -> "aem-event-null-date".equals(c.id()))
        );
    }

    @Test
    void matchedVenuePreservesExistingCityAndProvinceNotHeuristicOutput() {
        // Existing venue in DB has good city/province from conciertos.club
        SalaConcierto existingVenue = new SalaConcierto(
            "madrid-sala-riviera", "Sala Riviera", null,
            "Madrid", "Madrid",
            40.4048, -3.7069, null, null, null, NOW
        );
        // AEM venue has an address that the heuristic WOULD parse (e.g. "Calle Mayor 1, Madrid")
        // but city/province are null (AEM doesn't provide them)
        SalaConcierto aemVenue = new SalaConcierto(
            "aem-venue-42", "Sala Riviera",
            "Calle Mayor 1, Madrid",
            null, null,
            40.4048, -3.7069, null, null, null, NOW
        );
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of("madrid-sala-riviera"));
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(salaRepositoryPort.findAll()).thenReturn(List.of(existingVenue));
        when(concertRepositoryPort.findAll()).thenReturn(List.of());
        when(alcalaSourcePort.fetch(null, null)).thenReturn(new AlcalaSnapshot(
            List.of(aemVenue),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(concert("aem-event-abc", "aem-venue-42", EVENT_DATE, List.of("aem-band-101")))
        ));

        useCase.execute("run-1");

        // Matched venue must use the EXISTING city/province — not the heuristic
        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        SalaConcierto written = salaCaptor.getValue();
        assertThat(written.id()).isEqualTo("madrid-sala-riviera");
        assertThat(written.city()).isEqualTo("Madrid");
        assertThat(written.province()).isEqualTo("Madrid");
        // No province SEVERE issue for matched venues
        verify(qualityWriter, never()).saveAll(any());
        // Reverse geocoding must not be called for matched venues
        verifyNoInteractions(reverseGeocodingPort);
    }

    @Test
    void matchedVenueWithBlankExistingCityAndProvinceFallsBackToAemValues() {
        // Existing venue has no city/province (blank existing row)
        SalaConcierto existingVenue = new SalaConcierto(
            "madrid-sala-riviera", "Sala Riviera", null,
            null, null,
            40.4048, -3.7069, null, null, null, NOW
        );
        // AEM venue also has null city/province but a parseable address is irrelevant —
        // the point is: no geocoding, and province stays null (no SEVERE since it's matched)
        SalaConcierto aemVenue = new SalaConcierto(
            "aem-venue-42", "Sala Riviera",
            "Calle Mayor 1, Madrid",
            null, null,
            40.4048, -3.7069, null, null, null, NOW
        );
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of("madrid-sala-riviera"));
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(salaRepositoryPort.findAll()).thenReturn(List.of(existingVenue));
        when(concertRepositoryPort.findAll()).thenReturn(List.of());
        when(alcalaSourcePort.fetch(null, null)).thenReturn(new AlcalaSnapshot(
            List.of(aemVenue),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(concert("aem-event-abc", "aem-venue-42", EVENT_DATE, List.of("aem-band-101")))
        ));

        useCase.execute("run-1");

        ArgumentCaptor<SalaConcierto> salaCaptor = ArgumentCaptor.forClass(SalaConcierto.class);
        verify(salaWriter).upsert(salaCaptor.capture());
        SalaConcierto written = salaCaptor.getValue();
        assertThat(written.id()).isEqualTo("madrid-sala-riviera");
        // city/province fallback: existing is blank, aemVenue is also null → stays null
        assertThat(written.province()).isNull();
        // No SEVERE for matched venues even when province is null
        verify(qualityWriter, never()).saveAll(any());
        verifyNoInteractions(reverseGeocodingPort);
    }

    // --- Recovery / robustness (Judgment Day 2026-06-08) ---

    @Test
    void writeFailureMidBatchFailsRunAndDoesNotComplete() {
        setupHappyPath();
        org.mockito.Mockito.doThrow(new RuntimeException("DB write boom"))
            .when(salaWriter).upsert(any(SalaConcierto.class));

        useCase.execute("run-1");

        verify(syncRunPort).fail(eq("run-1"), eq("DB write boom"));
        verify(syncRunPort, never()).complete(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void failThrowingIsSwallowedAndDoesNotPropagate() {
        // doExecute throws (fetch fails) AND marking the run failed also throws.
        // The hardened catch must swallow the secondary failure so it never propagates out
        // of execute() (which would be lost in Spring's @Async handler in production).
        when(alcalaSourcePort.fetch(null, null)).thenThrow(new RuntimeException("AEM unavailable"));
        org.mockito.Mockito.doThrow(new RuntimeException("DB locked"))
            .when(syncRunPort).fail(anyString(), anyString());

        assertThatCode(() -> useCase.execute("run-1")).doesNotThrowAnyException();

        verify(syncRunPort).fail(eq("run-1"), eq("AEM unavailable"));
        verify(syncRunPort, never()).complete(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void dateWindowIsInclusiveOnBothBoundaries() {
        setupEmptyExistingData();
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        AlcalaSnapshot snapshot = new AlcalaSnapshot(
            List.of(venue("aem-venue-42", "Sala Riviera", 40.4051, -3.7068)),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(
                concert("aem-event-from", "aem-venue-42", from, List.of("aem-band-101")),
                concert("aem-event-to", "aem-venue-42", to, List.of("aem-band-101")),
                concert("aem-event-before", "aem-venue-42", from.minusDays(1), List.of("aem-band-101")),
                concert("aem-event-after", "aem-venue-42", to.plusDays(1), List.of("aem-band-101"))
            )
        );
        when(alcalaSourcePort.fetch(from, to)).thenReturn(snapshot);
        when(reverseGeocodingPort.reverse(40.4051, -3.7068))
            .thenReturn(Optional.of(new AdminArea("Alcalá de Henares", "Madrid")));

        useCase.execute("run-1", from, to);

        ArgumentCaptor<Concert> concertCaptor = ArgumentCaptor.forClass(Concert.class);
        verify(concertWriter, times(2)).upsert(concertCaptor.capture());
        assertThat(concertCaptor.getAllValues()).extracting(Concert::id)
            .containsExactlyInAnyOrder("aem-event-from", "aem-event-to");
    }

    @Test
    void nullSnapshotListsDoNotThrowAndStillCompleteRun() {
        setupEmptyExistingData();
        when(alcalaSourcePort.fetch(null, null)).thenReturn(new AlcalaSnapshot(null, null, null));

        useCase.execute("run-1");

        verify(syncRunPort).complete(eq("run-1"), eq(0), eq(0), eq(0), eq(0), eq(0));
        verifyNoInteractions(salaWriter, artistWriter, concertWriter);
    }

    private void setupHappyPath() {
        setupEmptyExistingData();
        when(alcalaSourcePort.fetch(null, null)).thenReturn(happySnapshot());
        when(reverseGeocodingPort.reverse(40.4051, -3.7068))
            .thenReturn(Optional.of(new AdminArea("Alcalá de Henares", "Madrid")));
    }

    private void setupEmptyExistingData() {
        when(existingDataReaderPort.existingVenueIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingArtistIds()).thenReturn(Set.of());
        when(existingDataReaderPort.existingConcertIds()).thenReturn(Set.of());
        when(salaRepositoryPort.findAll()).thenReturn(List.of());
        when(concertRepositoryPort.findAll()).thenReturn(List.of());
    }

    private static AlcalaSnapshot happySnapshot() {
        return new AlcalaSnapshot(
            List.of(venue("aem-venue-42", "Sala Riviera", 40.4051, -3.7068)),
            List.of(artist("aem-band-101", "Vetusta Morla")),
            List.of(concert("aem-event-abc", "aem-venue-42", EVENT_DATE, List.of("aem-band-101")))
        );
    }

    private static SalaConcierto venue(String id, String name, Double lat, Double lng) {
        return new SalaConcierto(id, name, null, null, null, lat, lng, null, null, null, NOW);
    }

    private static Artist artist(String id, String name) {
        return new Artist(id, name, null, null, null, null, null, NOW);
    }

    private static Concert concert(String id, String venueId, LocalDate date, List<String> artistIds) {
        return new Concert(id, venueId, artistIds, date, "21:00", "15€", "https://example.test/" + id, NOW);
    }
}
