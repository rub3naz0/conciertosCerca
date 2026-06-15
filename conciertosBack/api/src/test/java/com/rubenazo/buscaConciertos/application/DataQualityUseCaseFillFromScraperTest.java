package com.rubenazo.buscaConciertos.application;

import com.rubenazo.buscaConciertos.application.ports.in.GeocodingAdminInputPort.FillFromScraperItem;
import com.rubenazo.buscaConciertos.application.ports.in.GeocodingAdminInputPort.FillFromScraperResult;
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
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.scraper.application.parsers.VenueDetailParser;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataQualityUseCaseFillFromScraperTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-11T10:00:00Z");
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
    @Mock private HtmlFetchPort htmlFetchPort;
    @Mock private VenueDetailParser venueDetailParser;

    private DataQualityUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(repository.findEntityIdsBySourceAndField("sala", "manual", List.of("lat", "lng")))
            .thenReturn(List.of());
        useCase = new DataQualityUseCase(
            repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 20, artistRepository, salaRepository, 0.8,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase,
            htmlFetchPort, venueDetailParser
        );
    }

    private SalaConcierto sala(String id, String sourceUrl, Double lat, Double lng) {
        return new SalaConcierto(id, "Sala " + id, "Calle Test 1", "Barcelona", "Barcelona",
            lat, lng, null, null, sourceUrl, FIXED_NOW);
    }

    // --- Out-of-range limit -> 400 ---

    @Test
    void fillSalaCoordsFromScraper_limitZero_throws400() {
        assertThatThrownBy(() -> useCase.fillSalaCoordsFromScraper(true, 0))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fillSalaCoordsFromScraper_limitAboveMax_throws400() {
        assertThatThrownBy(() -> useCase.fillSalaCoordsFromScraper(true, 601))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void fillSalaCoordsFromScraper_limitUpTo600_accepted() {
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of());

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(true, 600);

        assertThat(result.scanned()).isZero();
    }

    // --- Target selection: manual-/alcala excluded, conciertos.club included ---

    @Test
    void fillSalaCoordsFromScraper_excludesManualSourceUrl() throws Exception {
        SalaConcierto manualSala = sala("manual-sala-x", "manual", null, null);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(manualSala));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(true, 50);

        assertThat(result.scanned()).isZero();
        assertThat(result.items()).isEmpty();
        verifyNoInteractions(htmlFetchPort);
    }

    @Test
    void fillSalaCoordsFromScraper_excludesAlcalaSourceUrl() throws Exception {
        SalaConcierto alcalaSala = sala("alcala-sala-x", "https://alcalaesmusica.org/sala-x", null, null);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(alcalaSala));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(true, 50);

        assertThat(result.scanned()).isZero();
        assertThat(result.items()).isEmpty();
        verifyNoInteractions(htmlFetchPort);
    }

    @Test
    void fillSalaCoordsFromScraper_includesAlreadyGeocodedConciertosClubSala() throws Exception {
        SalaConcierto geocoded = sala("s1", "https://conciertos.club/barcelona/locales/sala-apolo", 41.0, 2.0);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(geocoded));
        when(htmlFetchPort.fetch(geocoded.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any())).thenReturn(java.util.Optional.empty());

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(true, 50);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void fillSalaCoordsFromScraper_limitTruncatesTargets() throws Exception {
        SalaConcierto s1 = sala("s1", "https://conciertos.club/barcelona/locales/s1", null, null);
        SalaConcierto s2 = sala("s2", "https://conciertos.club/barcelona/locales/s2", null, null);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(s1, s2));
        when(htmlFetchPort.fetch(anyString())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any())).thenReturn(java.util.Optional.empty());

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(true, 1);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
    }

    // --- Dry run: no writes ---

    @Test
    void fillSalaCoordsFromScraper_dryRun_reportsWouldWrite_withoutWriting() throws Exception {
        SalaConcierto noCoords = sala("s1", "https://conciertos.club/barcelona/locales/s1", null, null);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(noCoords));
        when(htmlFetchPort.fetch(noCoords.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Optional.of(scrapedVenue("s1", 41.3735, 2.1700)));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(true, 10);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.wouldWrite()).isEqualTo(1);
        assertThat(result.written()).isZero();
        verify(salaConciertoWritePort, never()).updateField(any(), any(), any(), any());
        verify(writer, never()).upsertResolution(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- Write mode: NULL coords -> written + upsertResolution(auto_approved, conciertos-club-page, 1.0) ---

    @Test
    void fillSalaCoordsFromScraper_writeMode_nullCoords_writesLatLngAndResolvesDataQuality() throws Exception {
        SalaConcierto noCoords = sala("s1", "https://conciertos.club/barcelona/locales/s1", null, null);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(noCoords));
        when(htmlFetchPort.fetch(noCoords.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Optional.of(scrapedVenue("s1", 41.3735, 2.1700)));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(false, 10);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.written()).isEqualTo(1);
        assertThat(result.items().get(0).decision()).isEqualTo("written");

        verify(salaConciertoWritePort).updateField(eq("s1"), eq("lat"), eq("41.3735"), eq(FIXED_NOW));
        verify(salaConciertoWritePort).updateField(eq("s1"), eq("lng"), eq("2.17"), eq(FIXED_NOW));
        verify(writer).upsertResolution(eq("sala"), eq("s1"), eq("lat"), eq("auto_approved"),
            eq("41.3735"), eq("conciertos-club-page"), eq(1.0), eq(FIXED_NOW));
        verify(writer).upsertResolution(eq("sala"), eq("s1"), eq("lng"), eq("auto_approved"),
            eq("2.17"), eq("conciertos-club-page"), eq(1.0), eq(FIXED_NOW));
        verify(syncMetadataWritePort).updateLastModified("salas-concierto", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified("concerts", FIXED_NOW);
    }

    // --- Geocoding-provenance, scraped disagrees -> written (page pin wins) ---

    @Test
    void fillSalaCoordsFromScraper_geocodingProvenance_disagreement_overwritesWithScrapedPin() throws Exception {
        SalaConcierto geocoded = sala("s1", "https://conciertos.club/barcelona/locales/s1", 41.0, 2.0);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(geocoded));
        when(htmlFetchPort.fetch(geocoded.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Optional.of(scrapedVenue("s1", 41.3735, 2.1700)));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(false, 10);

        assertThat(result.written()).isEqualTo(1);
        assertThat(result.items().get(0).decision()).isEqualTo("written");
        verify(salaConciertoWritePort).updateField(eq("s1"), eq("lat"), eq("41.3735"), eq(FIXED_NOW));
        verify(salaConciertoWritePort).updateField(eq("s1"), eq("lng"), eq("2.17"), eq(FIXED_NOW));
    }

    // --- Manual-provenance sala (in findEntityIdsBySourceAndField "manual" stub) -> kept-manual ---

    @Test
    void fillSalaCoordsFromScraper_manualProvenanceSala_keptManual_noWrite() throws Exception {
        SalaConcierto manualCoords = sala("s1", "https://conciertos.club/barcelona/locales/s1", 41.0, 2.0);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(manualCoords));
        when(repository.findEntityIdsBySourceAndField("sala", "manual", List.of("lat", "lng")))
            .thenReturn(List.of("s1"));
        when(htmlFetchPort.fetch(manualCoords.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Optional.of(scrapedVenue("s1", 41.3735, 2.1700)));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(false, 10);

        assertThat(result.keptManual()).isEqualTo(1);
        assertThat(result.written()).isZero();
        assertThat(result.items().get(0).decision()).isEqualTo("kept-manual");
        verify(salaConciertoWritePort, never()).updateField(any(), any(), any(), any());
        verify(writer, never()).upsertResolution(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- Coords equal current (within epsilon) -> kept-no-change, no write ---

    @Test
    void fillSalaCoordsFromScraper_coordsEqualCurrent_keptNoChange_noWrite() throws Exception {
        SalaConcierto sameCoords = sala("s1", "https://conciertos.club/barcelona/locales/s1", 41.3735, 2.1700);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sameCoords));
        when(htmlFetchPort.fetch(sameCoords.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Optional.of(scrapedVenue("s1", 41.3735, 2.1700)));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(false, 10);

        assertThat(result.keptNoChange()).isEqualTo(1);
        assertThat(result.written()).isZero();
        assertThat(result.items().get(0).decision()).isEqualTo("kept-no-change");
        verify(salaConciertoWritePort, never()).updateField(any(), any(), any(), any());
        verify(writer, never()).upsertResolution(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- No iframe / invalid pin -> no-coords, no write, untouched ---

    @Test
    void fillSalaCoordsFromScraper_noIframeCoords_reportsNoCoords_noWrite() throws Exception {
        SalaConcierto sala = sala("s1", "https://conciertos.club/barcelona/locales/s1", null, null);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(sala));
        when(htmlFetchPort.fetch(sala.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Optional.of(scrapedVenue("s1", null, null)));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(false, 10);

        assertThat(result.noCoords()).isEqualTo(1);
        assertThat(result.written()).isZero();
        assertThat(result.items().get(0).decision()).isEqualTo("no-coords");
        verify(salaConciertoWritePort, never()).updateField(any(), any(), any(), any());
        verify(writer, never()).upsertResolution(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // --- Fetch failure -> treated as no-coords, loop continues ---

    @Test
    void fillSalaCoordsFromScraper_fetchFailure_treatedAsNoCoords_continuesLoop() throws Exception {
        SalaConcierto failing = sala("s1", "https://conciertos.club/barcelona/locales/s1", null, null);
        SalaConcierto ok = sala("s2", "https://conciertos.club/barcelona/locales/s2", null, null);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(failing, ok));
        when(htmlFetchPort.fetch(failing.sourceUrl())).thenThrow(new HtmlFetchException(failing.sourceUrl(), -1, "boom"));
        when(htmlFetchPort.fetch(ok.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Optional.of(scrapedVenue("s2", 41.3735, 2.1700)));

        FillFromScraperResult result = useCase.fillSalaCoordsFromScraper(true, 10);

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.noCoords()).isEqualTo(1);
        assertThat(result.wouldWrite()).isEqualTo(1);
    }

    // --- data_quality repair: open severe/missing row resolved to auto_approved ---

    @Test
    void fillSalaCoordsFromScraper_writeMode_alwaysCallsUpsertResolutionRegardlessOfPriorState() throws Exception {
        // Covers ADR-9: upsertResolution uniformly repairs (a) open severe/missing rows,
        // (b) salas with no data_quality row, and (c) anomalous resolved-without-value rows —
        // no special-case branching needed, the same call handles all three.
        SalaConcierto noCoords = sala("s1", "https://conciertos.club/barcelona/locales/s1", null, null);
        when(salaRepository.findAllIncludingBlocked()).thenReturn(List.of(noCoords));
        when(htmlFetchPort.fetch(noCoords.sourceUrl())).thenReturn("<html></html>");
        when(venueDetailParser.parse(any(), any(), any(), any(), any()))
            .thenReturn(java.util.Optional.of(scrapedVenue("s1", 41.3735, 2.1700)));

        useCase.fillSalaCoordsFromScraper(false, 10);

        verify(writer).upsertResolution(eq("sala"), eq("s1"), eq("lat"), eq("auto_approved"),
            anyString(), eq("conciertos-club-page"), eq(1.0), eq(FIXED_NOW));
        verify(writer).upsertResolution(eq("sala"), eq("s1"), eq("lng"), eq("auto_approved"),
            anyString(), eq("conciertos-club-page"), eq(1.0), eq(FIXED_NOW));
    }

    private com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue scrapedVenue(String id, Double lat, Double lng) {
        return new com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue(
            id, "Sala " + id, "Calle Test 1", "Barcelona", "Barcelona",
            lat, lng, null, null, "https://conciertos.club/barcelona/locales/" + id);
    }
}
