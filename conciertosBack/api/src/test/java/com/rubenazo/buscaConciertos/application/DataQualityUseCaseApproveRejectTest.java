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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataQualityUseCaseApproveRejectTest {

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
        lenient().when(salaRepository.findByIdIncludingBlocked(any())).thenReturn(Optional.empty());
        lenient().when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.empty());
        useCase = new DataQualityUseCase(
            repository, writer, tavilySearch,
            artistWritePort, salaConciertoWritePort, syncMetadataWritePort,
            FIXED_CLOCK, 20, artistRepository, salaRepository, 0.8,
            entityEnrichmentPort, concertRepository, "basic", 5, venueGeocodingUseCase
        );
    }

    // --- approve: 404 when not found ---

    @Test
    void approve_throws404WhenIdNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.approve(999L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- approve: 409 when already approved ---

    @Test
    void approve_throws409WhenAlreadyApproved() {
        DataQuality dq = new DataQuality(2L, "sala", "s1", "phone", "approved",
                "non_severe", "+34 91 000", "https://src.com", null, FIXED_NOW);
        when(repository.findById(2L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.approve(2L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- approve: 409 when already rejected ---

    @Test
    void approve_throws409WhenAlreadyRejected() {
        DataQuality dq = new DataQuality(3L, "artist", "a1", "genre", "rejected",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(3L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.approve(3L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- approve: 409 when auto_approved (terminal) ---

    @Test
    void guardNotTerminal_rejectsAutoApproved_with409() {
        DataQuality dq = new DataQuality(10L, "sala", "s1", "description", "auto_approved",
                "non_severe", "Great venue", "https://tavily.com", 0.92, FIXED_NOW);
        when(repository.findById(10L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.approve(10L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void reject_throws409WhenAutoApproved() {
        DataQuality dq = new DataQuality(11L, "sala", "s1", "description", "auto_approved",
                "non_severe", "Great venue", "https://tavily.com", 0.92, FIXED_NOW);
        when(repository.findById(11L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.reject(11L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- listIssues accepts auto_approved status ---

    @Test
    void listIssues_acceptsAutoApprovedStatus() {
        when(repository.findByStatus("auto_approved")).thenReturn(List.of());

        // Must not throw
        List<DataQuality> result = useCase.listIssues("auto_approved");
        assertThat(result).isEmpty();
    }

    // --- VALID_STATUSES includes auto_approved (verified via listIssues(null)) ---

    @Test
    void listIssues_nullStatus_callsFindByStatusFiveTimes() {
        when(repository.findByStatus(anyString())).thenReturn(List.of());

        useCase.listIssues(null);

        // 5 valid statuses: missing, auto_found, approved, auto_approved, rejected
        verify(repository, times(5)).findByStatus(anyString());
    }

    // --- approve: sala phone — writes field + bumps sync ---

    @Test
    void approve_salaPhone_writesFieldAndBumpsSyncMeta() {
        DataQuality dq = new DataQuality(1L, "sala", "s1", "phone", "auto_found",
                "non_severe", "+34 91 123 4567", "https://src.com", null, FIXED_NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(dq));

        useCase.approve(1L);

        verify(salaConciertoWritePort).updateField("s1", "phone", "+34 91 123 4567", FIXED_NOW);
        verify(writer).updateStatus(1L, "approved", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified(eq("salas-concierto"), eq(FIXED_NOW));
        verifyNoInteractions(artistWritePort);
    }

    // --- approve: artist genre — writes field + bumps sync ---

    @Test
    void approve_artistGenre_writesFieldAndBumpsSyncMeta() {
        DataQuality dq = new DataQuality(5L, "artist", "a1", "genre", "auto_found",
                "severe", "Indie Rock", "https://src.com", null, FIXED_NOW);
        when(repository.findById(5L)).thenReturn(Optional.of(dq));

        useCase.approve(5L);

        verify(artistWritePort).updateField("a1", "genre", "Indie Rock", FIXED_NOW);
        verify(writer).updateStatus(5L, "approved", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified(eq("artists"), eq(FIXED_NOW));
        verify(syncMetadataWritePort).updateLastModified(eq("concerts"), eq(FIXED_NOW));
        verifyNoInteractions(salaConciertoWritePort);
    }

    @Test
    void approve_salaAddress_geocodesAndMarksLatLngAutoApprovedWithRealProviderAndScore() {
        // Updated for venue-geocoding-by-name: geocode() now returns Optional<VenueMatch> with real confidence/provider
        DataQuality dq = new DataQuality(12L, "sala", "s1", "address", "auto_found",
                "severe", "Calle Mayor 5", "https://src.com", 0.92, FIXED_NOW);
        when(repository.findById(12L)).thenReturn(Optional.of(dq));
        when(salaRepository.findByIdIncludingBlocked("s1")).thenReturn(Optional.of(
            new SalaConcierto("s1", "Sala Test", "Calle Mayor 5", "Madrid", "Madrid",
                null, null, null, null, null, FIXED_NOW)
        ));
        // High-confidence LocationIQ result (above threshold 0.8)
        when(venueGeocodingUseCase.geocode(any())).thenReturn(Optional.of(
            new VenueMatch(40.4168, -3.7038, "Calle Mayor 5", 0.85, "address", "locationiq")
        ));

        useCase.approve(12L);

        verify(salaConciertoWritePort).updateField("s1", "address", "Calle Mayor 5", FIXED_NOW);
        verify(salaConciertoWritePort).updateField("s1", "lat", "40.4168", FIXED_NOW);
        verify(salaConciertoWritePort).updateField("s1", "lng", "-3.7038", FIXED_NOW);
        // Real provider and score — not hardcoded "locationiq"/1.0
        verify(writer).upsertResolution("sala", "s1", "lat", "auto_approved", "40.4168", "locationiq", 0.85, FIXED_NOW);
        verify(writer).upsertResolution("sala", "s1", "lng", "auto_approved", "-3.7038", "locationiq", 0.85, FIXED_NOW);
        verify(writer).updateStatus(12L, "approved", FIXED_NOW);
    }

    // --- approve: sync bump fires even if value was already equal ---

    @Test
    void approve_alwaysBumpsSyncEvenIfValueUnchanged() {
        DataQuality dq = new DataQuality(6L, "sala", "s2", "phone", "missing",
                "non_severe", "+34 91 000 0000", "https://src.com", null, FIXED_NOW);
        when(repository.findById(6L)).thenReturn(Optional.of(dq));

        useCase.approve(6L);

        verify(syncMetadataWritePort).updateLastModified(eq("salas-concierto"), any(Instant.class));
    }

    // --- reject: 404 when not found ---

    @Test
    void reject_throws404WhenIdNotFound() {
        when(repository.findById(888L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.reject(888L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- reject: 409 when already approved ---

    @Test
    void reject_throws409WhenAlreadyApproved() {
        DataQuality dq = new DataQuality(7L, "sala", "s1", "phone", "approved",
                "non_severe", "+34 91 000", "https://src.com", null, FIXED_NOW);
        when(repository.findById(7L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.reject(7L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- reject: 409 when already rejected ---

    @Test
    void reject_throws409WhenAlreadyRejected() {
        DataQuality dq = new DataQuality(8L, "artist", "a1", "genre", "rejected",
                "severe", null, null, null, FIXED_NOW);
        when(repository.findById(8L)).thenReturn(Optional.of(dq));

        assertThatThrownBy(() -> useCase.reject(8L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- reject: happy path — only updateStatus, NO entity write, NO sync bump ---

    @Test
    void reject_updatesStatusOnlyAndDoesNotTouchEntityOrSync() {
        DataQuality dq = new DataQuality(3L, "sala", "s1", "phone", "auto_found",
                "non_severe", "+34 91 000", "https://src.com", null, FIXED_NOW);
        when(repository.findById(3L)).thenReturn(Optional.of(dq));

        useCase.reject(3L);

        verify(writer).updateStatus(3L, "rejected", FIXED_NOW);
        verifyNoInteractions(salaConciertoWritePort, artistWritePort, syncMetadataWritePort);
    }

    @Test
    void reject_severeRowBumpsConcertSyncForClientDeletion() {
        DataQuality dq = new DataQuality(4L, "sala", "s1", "lng", "auto_found",
                "severe", "-36015.0", "https://src.com", null, FIXED_NOW);
        when(repository.findById(4L)).thenReturn(Optional.of(dq));

        useCase.reject(4L);

        verify(writer).updateStatus(4L, "rejected", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified(eq("concerts"), eq(FIXED_NOW));
        verifyNoInteractions(salaConciertoWritePort, artistWritePort);
    }

    // --- approveAll ---

    @Test
    void approveAll_approvesAutoFoundRowsAtOrAboveMinScore() {
        DataQuality dq1 = new DataQuality(1L, "artist", "a1", "website", "auto_found",
                "non_severe", "https://artist.com", "https://src.com", 0.92, FIXED_NOW);
        DataQuality dq2 = new DataQuality(2L, "sala", "s1", "description", "auto_found",
                "non_severe", "Great venue", "https://src.com", 0.85, FIXED_NOW);
        when(repository.findByStatusAndScore("auto_found", 0.8, null)).thenReturn(List.of(dq1, dq2));

        int count = useCase.approveAll(0.8, null);

        assertThat(count).isEqualTo(2);
        verify(writer).updateStatus(1L, "approved", FIXED_NOW);
        verify(writer).updateStatus(2L, "approved", FIXED_NOW);
    }

    @Test
    void approveAll_filteredBySeverity_onlyMatchingRows() {
        DataQuality dq1 = new DataQuality(1L, "sala", "s1", "description", "auto_found",
                "non_severe", "Nice place", "https://src.com", 0.88, FIXED_NOW);
        when(repository.findByStatusAndScore("auto_found", 0.8, "non_severe")).thenReturn(List.of(dq1));

        int count = useCase.approveAll(0.8, "non_severe");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void approveAll_noMatchingRows_returnsZero() {
        when(repository.findByStatusAndScore("auto_found", 0.95, null)).thenReturn(List.of());

        int count = useCase.approveAll(0.95, null);

        assertThat(count).isEqualTo(0);
        verifyNoInteractions(writer);
    }

    @Test
    void approveAll_callsApplyResolutionPerApprovedRow() {
        DataQuality dq = new DataQuality(5L, "artist", "a1", "website", "auto_found",
                "non_severe", "https://artist.com", "https://src.com", 0.9, FIXED_NOW);
        when(repository.findByStatusAndScore("auto_found", 0.8, null)).thenReturn(List.of(dq));

        useCase.approveAll(0.8, null);

        verify(artistWritePort).updateField("a1", "website", "https://artist.com", FIXED_NOW);
        verify(syncMetadataWritePort).updateLastModified(eq("artists"), eq(FIXED_NOW));
        verify(writer).updateStatus(5L, "approved", FIXED_NOW);
    }
}
