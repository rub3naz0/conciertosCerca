package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.adapters.in.dto.SyncRunDto;
import com.rubenazo.buscaConciertos.application.ports.in.SyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.SyncRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/sync")
@Tag(name = "Sync Admin")
public class SyncAdminApi {

    private final SyncRunPort syncRunPort;
    private final SyncInputPort syncInputPort;

    public SyncAdminApi(SyncRunPort syncRunPort, SyncInputPort syncInputPort) {
        this.syncRunPort = syncRunPort;
        this.syncInputPort = syncInputPort;
    }

    @Operation(
        summary = "Trigger async sync",
        responses = {
            @ApiResponse(responseCode = "202", description = "Sync started, returns runId"),
            @ApiResponse(responseCode = "409", description = "Sync already running")
        }
    )
    @PostMapping
    public ResponseEntity<?> triggerSync(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return syncRunPort.tryStart()
            .map(runId -> {
                if (from != null && to != null) {
                    syncInputPort.execute(runId, from, to);
                } else {
                    syncInputPort.execute(runId);
                }
                return ResponseEntity.accepted().body((Object) Map.of("runId", runId));
            })
            .orElseGet(() -> ResponseEntity.status(409).build());
    }

    @Operation(
        summary = "Get latest sync run",
        responses = {
            @ApiResponse(responseCode = "200", description = "Latest sync run"),
            @ApiResponse(responseCode = "404", description = "No runs found")
        }
    )
    @GetMapping("/latest")
    public ResponseEntity<SyncRunDto> getLatest() {
        Optional<SyncRun> run = syncRunPort.findLatest();
        return run.map(r -> ResponseEntity.ok(SyncRunDto.from(r)))
            .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Get sync run by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Sync run for given ID"),
            @ApiResponse(responseCode = "404", description = "Run not found")
        }
    )
    @GetMapping("/{runId}")
    public ResponseEntity<SyncRunDto> getById(@PathVariable String runId) {
        Optional<SyncRun> run = syncRunPort.findById(runId);
        return run.map(r -> ResponseEntity.ok(SyncRunDto.from(r)))
            .orElse(ResponseEntity.notFound().build());
    }
}
