package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.adapters.in.dto.DataQualityIssueDto;
import com.rubenazo.buscaConciertos.application.DataQualityCheckEvent;
import com.rubenazo.buscaConciertos.application.ports.in.DataQualityInputPort;
import com.rubenazo.buscaConciertos.domain.DataQuality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataQualityAdminApiTest {

    private static final Instant FIXED = Instant.parse("2026-05-28T10:00:00Z");

    @Mock
    private DataQualityInputPort dataQualityInputPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DataQualityAdminApi api;

    @BeforeEach
    void setUp() {
        api = new DataQualityAdminApi(dataQualityInputPort, eventPublisher);
    }

    // --- Scenario: list all issues (no filter) ---

    @Test
    void listIssues_returns200WithAllIssuesWhenNoFilter() {
        List<DataQuality> issues = List.of(
            new DataQuality(1L, "sala", "s1", "phone", "auto_found", "non_severe", "+34 91 000", "https://src.com", null, FIXED),
            new DataQuality(2L, "artist", "a1", "genre", "missing", "severe", null, null, null, FIXED)
        );
        when(dataQualityInputPort.listIssues(null, null)).thenReturn(issues);

        ResponseEntity<List<DataQualityIssueDto>> response = api.listIssues(null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(2);
    }

    // --- Scenario: filter by status ---

    @Test
    void listIssues_returns200WithFilteredIssuesWhenStatusProvided() {
        DataQuality dq = new DataQuality(1L, "sala", "s1", "phone", "auto_found",
                "non_severe", "+34 91 000", "https://src.com", null, FIXED);
        when(dataQualityInputPort.listIssues("auto_found", null)).thenReturn(List.of(dq));

        ResponseEntity<List<DataQualityIssueDto>> response = api.listIssues("auto_found", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).status()).isEqualTo("auto_found");
    }

    // --- Scenario: unknown status → 400 ---

    @Test
    void listIssues_propagates400WhenInputPortThrowsBadRequest() {
        when(dataQualityInputPort.listIssues("invalid_value", null))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: invalid_value"));

        assertThatThrownBy(() -> api.listIssues("invalid_value", null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- Scenario: listIssues with minScore delegates to port ---

    @Test
    void listIssues_withMinScore_delegates() {
        DataQuality dq = new DataQuality(1L, "sala", "s1", "description", "auto_found",
                "non_severe", "Great venue", "https://src.com", 0.91, FIXED);
        when(dataQualityInputPort.listIssues(null, 0.8)).thenReturn(List.of(dq));

        ResponseEntity<List<DataQualityIssueDto>> response = api.listIssues(null, 0.8);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).hasSize(1);
        verify(dataQualityInputPort).listIssues(null, 0.8);
    }

    // --- Scenario: approve — 200 on valid transition ---

    @Test
    void approve_returns200WhenApprovalSucceeds() {
        doNothing().when(dataQualityInputPort).approve(1L);

        ResponseEntity<Void> response = api.approve(1L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(dataQualityInputPort).approve(1L);
    }

    // --- Scenario: approve — 404 on missing id ---

    @Test
    void approve_propagates404WhenIdNotFound() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"))
            .when(dataQualityInputPort).approve(999L);

        assertThatThrownBy(() -> api.approve(999L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- Scenario: approve — 409 on invalid state transition ---

    @Test
    void approve_propagates409WhenAlreadyApproved() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Already approved"))
            .when(dataQualityInputPort).approve(2L);

        assertThatThrownBy(() -> api.approve(2L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- Scenario: reject — 200 on valid transition ---

    @Test
    void reject_returns200WhenRejectionSucceeds() {
        doNothing().when(dataQualityInputPort).reject(3L);

        ResponseEntity<Void> response = api.reject(3L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(dataQualityInputPort).reject(3L);
    }

    // --- Scenario: reject — 404 on missing id ---

    @Test
    void reject_propagates404WhenIdNotFound() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"))
            .when(dataQualityInputPort).reject(888L);

        assertThatThrownBy(() -> api.reject(888L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- Scenario: reject — 409 on invalid state transition ---

    @Test
    void reject_propagates409WhenAlreadyRejected() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Already rejected"))
            .when(dataQualityInputPort).reject(5L);

        assertThatThrownBy(() -> api.reject(5L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT));
    }

    // --- Scenario: DTO mapping — from(DataQuality) is called for each item ---

    @Test
    void listIssues_mapsDomainToDto() {
        DataQuality dq = new DataQuality(7L, "artist", "a2", "website", "auto_found",
                "non_severe", "https://artist.com", "https://tavily.com", 0.91, FIXED);
        when(dataQualityInputPort.listIssues(null, null)).thenReturn(List.of(dq));

        ResponseEntity<List<DataQualityIssueDto>> response = api.listIssues(null, null);

        DataQualityIssueDto dto = response.getBody().get(0);
        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.entityType()).isEqualTo("artist");
        assertThat(dto.entityId()).isEqualTo("a2");
        assertThat(dto.field()).isEqualTo("website");
        assertThat(dto.suggested()).isEqualTo("https://artist.com");
        assertThat(dto.source()).isEqualTo("https://tavily.com");
        assertThat(dto.score()).isEqualTo(0.91);
        assertThat(dto.updatedAt()).isEqualTo(FIXED);
    }

    // --- Scenario: sync bump fires — confirmed by approve delegating to port ---

    @Test
    void approve_delegatesToInputPortWhichHandlesSyncBump() {
        doNothing().when(dataQualityInputPort).approve(10L);

        api.approve(10L);

        // The controller delegates; the sync bump is the port's responsibility
        verify(dataQualityInputPort, times(1)).approve(10L);
    }

    // --- Scenario: approve-all returns 200 with count ---

    @Test
    void approveAll_returns200WithCount() {
        when(dataQualityInputPort.approveAll(0.8, null)).thenReturn(3);

        ResponseEntity<Map<String, Integer>> response = api.approveAll(0.8, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("approved", 3);
    }

    @Test
    void approveAll_withSeverityFilter_delegates() {
        when(dataQualityInputPort.approveAll(0.8, "non_severe")).thenReturn(2);

        ResponseEntity<Map<String, Integer>> response = api.approveAll(0.8, "non_severe");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("approved", 2);
        verify(dataQualityInputPort).approveAll(0.8, "non_severe");
    }

    @Test
    void approveAll_noMatchingRows_returns0() {
        when(dataQualityInputPort.approveAll(0.95, null)).thenReturn(0);

        ResponseEntity<Map<String, Integer>> response = api.approveAll(0.95, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("approved", 0);
    }

    // --- Scenario: trigger autofill publishes event and returns 202 ---

    @Test
    void triggerAutofill_returns202AndPublishesEvent() {
        ResponseEntity<Void> response = api.triggerAutofill();

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(eventPublisher).publishEvent(any(DataQualityCheckEvent.class));
    }
}
