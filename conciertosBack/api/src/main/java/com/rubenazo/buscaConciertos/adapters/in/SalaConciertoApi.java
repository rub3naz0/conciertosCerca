package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.adapters.in.dto.SalaConciertoDto;
import com.rubenazo.buscaConciertos.adapters.in.dto.SyncResponseDto;
import com.rubenazo.buscaConciertos.application.ports.in.SalaConciertoInputPort;
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
@RequestMapping("/api/v1/salas-concierto")
@Tag(name = "Salas de Concierto", description = "Concert venues management and sync")
public class SalaConciertoApi {

    private final SalaConciertoInputPort inputPort;

    public SalaConciertoApi(SalaConciertoInputPort inputPort) {
        this.inputPort = inputPort;
    }

    @Operation(
        summary = "Check for venue changes",
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
        summary = "Download venues",
        description = "Without 'since': returns all venues. With 'since': returns only venues modified after that timestamp"
    )
    @GetMapping
    public ResponseEntity<SyncResponseDto<SalaConciertoDto>> get(
            @Parameter(description = "ISO 8601 timestamp of last sync", example = "2026-05-20T15:30:00Z")
            @RequestParam(required = false) String since) {
        Instant sinceInstant = since != null ? Instant.parse(since) : null;
        return ResponseEntity.ok(
            SyncResponseDto.from(inputPort.getSalas(sinceInstant), SalaConciertoDto::from)
        );
    }
}
