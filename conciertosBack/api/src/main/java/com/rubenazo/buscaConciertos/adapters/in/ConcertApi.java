package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.adapters.in.dto.ConcertSyncResponseDto;
import com.rubenazo.buscaConciertos.application.ports.in.ConcertInputPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/concerts")
@Tag(name = "Concerts", description = "Concerts management and sync")
public class ConcertApi {

    private final ConcertInputPort inputPort;

    public ConcertApi(ConcertInputPort inputPort) {
        this.inputPort = inputPort;
    }

    @Operation(
        summary = "Check for concert changes",
        description = "Returns 200 if there are changes since the given timestamp, 204 if no changes",
        responses = {
            @ApiResponse(responseCode = "200", description = "Changes available, perform GET"),
            @ApiResponse(responseCode = "204", description = "No changes since the given timestamp")
        }
    )
    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(
            @Parameter(description = "ISO 8601 timestamp of last sync", example = "2026-05-20T15:30:00Z")
            @RequestParam(required = false) String since) {
        Instant sinceInstant = since != null ? Instant.parse(since) : null;
        return inputPort.hasChanges(sinceInstant)
            ? ResponseEntity.ok().build()
            : ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Download concerts",
        description = "Without 'since': returns all future concerts. With 'since': returns concerts modified/cancelled after that timestamp. Includes deleted_ids for cancelled concerts"
    )
    @GetMapping
    public ResponseEntity<ConcertSyncResponseDto> get(
            @Parameter(description = "ISO 8601 timestamp of last sync", example = "2026-05-20T15:30:00Z")
            @RequestParam(required = false) String since) {
        Instant sinceInstant = since != null ? Instant.parse(since) : null;
        return ResponseEntity.ok(ConcertSyncResponseDto.from(inputPort.getConcerts(sinceInstant)));
    }
}
