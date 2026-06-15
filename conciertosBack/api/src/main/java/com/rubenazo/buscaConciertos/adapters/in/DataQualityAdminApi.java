package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.adapters.in.dto.DataQualityIssueDto;
import com.rubenazo.buscaConciertos.application.DataQualityCheckEvent;
import com.rubenazo.buscaConciertos.application.ports.in.DataQualityInputPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/quality")
@Tag(name = "Admin — Data Quality")
public class DataQualityAdminApi {

    public record FillRequest(String value) {}

    private final DataQualityInputPort dataQualityInputPort;
    private final ApplicationEventPublisher eventPublisher;

    public DataQualityAdminApi(DataQualityInputPort dataQualityInputPort, ApplicationEventPublisher eventPublisher) {
        this.dataQualityInputPort = dataQualityInputPort;
        this.eventPublisher = eventPublisher;
    }

    @Operation(
        summary = "List data quality issues",
        description = "Returns all data quality issues. Optionally filter by status and/or minScore."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Issues returned"),
        @ApiResponse(responseCode = "400", description = "Unknown status value or invalid minScore")
    })
    @GetMapping
    public ResponseEntity<List<DataQualityIssueDto>> listIssues(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minScore) {
        List<DataQualityIssueDto> dtos = dataQualityInputPort.listIssues(status, minScore)
                .stream()
                .map(DataQualityIssueDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Operation(
        summary = "Approve a data quality suggestion",
        description = "Approves the suggestion, writes the value back to the entity, and bumps sync metadata."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suggestion approved"),
        @ApiResponse(responseCode = "404", description = "Issue not found"),
        @ApiResponse(responseCode = "409", description = "Issue already approved, auto_approved, or rejected")
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        dataQualityInputPort.approve(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Reject a data quality suggestion",
        description = "Rejects the suggestion. The source entity is not modified."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suggestion rejected"),
        @ApiResponse(responseCode = "404", description = "Issue not found"),
        @ApiResponse(responseCode = "409", description = "Issue already approved, auto_approved, or rejected")
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id) {
        dataQualityInputPort.reject(id);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Batch approve auto_found rows at or above a minimum score",
        description = "Approves every auto_found row where score >= minScore, optionally filtered by severity. Returns the count of approved rows."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Batch approval complete"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid minScore parameter")
    })
    @PostMapping("/approve-all")
    public ResponseEntity<Map<String, Integer>> approveAll(
            @RequestParam double minScore,
            @RequestParam(required = false) String severity) {
        int approved = dataQualityInputPort.approveAll(minScore, severity);
        return ResponseEntity.ok(Map.of("approved", approved));
    }

    @Operation(
        summary = "Manually fill a SEVERE data quality row with a human-supplied value",
        description = "Applies the supplied value to the entity column and sets the data_quality row status to 'approved'."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Value applied, status set to approved"),
        @ApiResponse(responseCode = "400", description = "Blank value or non-numeric lat/lng"),
        @ApiResponse(responseCode = "404", description = "Issue not found"),
        @ApiResponse(responseCode = "409", description = "Issue already in terminal status"),
        @ApiResponse(responseCode = "422", description = "Field is not manually fillable (concert entity or name field)")
    })
    @PostMapping("/{id}/fill")
    public ResponseEntity<Void> fill(@PathVariable Long id, @RequestBody FillRequest request) {
        dataQualityInputPort.fill(id, request.value());
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Trigger RAG autofill over current 'missing' rows (no re-scrape)",
        description = "Publishes a DataQualityCheckEvent so the async RAG enrichment runs over existing 'missing' data-quality rows without re-scraping. Returns 202 immediately."
    )
    @ApiResponse(responseCode = "202", description = "Autofill triggered asynchronously")
    @PostMapping("/autofill")
    public ResponseEntity<Void> triggerAutofill() {
        eventPublisher.publishEvent(new DataQualityCheckEvent(List.of(), List.of()));
        return ResponseEntity.accepted().build();
    }
}
