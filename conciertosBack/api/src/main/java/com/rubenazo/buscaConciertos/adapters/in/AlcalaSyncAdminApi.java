package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.application.ports.in.AlcalaSyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/sync/alcala")
@Tag(name = "Alcala Sync Admin")
public class AlcalaSyncAdminApi {

    private final SyncRunPort syncRunPort;
    private final AlcalaSyncInputPort alcalaSyncInputPort;

    public AlcalaSyncAdminApi(SyncRunPort syncRunPort, AlcalaSyncInputPort alcalaSyncInputPort) {
        this.syncRunPort = syncRunPort;
        this.alcalaSyncInputPort = alcalaSyncInputPort;
    }

    @Operation(
        summary = "Trigger async alcalaesmusica.org sync",
        responses = {
            @ApiResponse(responseCode = "202", description = "Alcala sync started, returns runId"),
            @ApiResponse(responseCode = "409", description = "Sync already running")
        }
    )
    @PostMapping
    public ResponseEntity<?> triggerSync(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        return syncRunPort.tryStart()
            .map(runId -> {
                if (from != null || to != null) {
                    alcalaSyncInputPort.execute(runId, from, to);
                } else {
                    alcalaSyncInputPort.execute(runId);
                }
                return ResponseEntity.accepted().body((Object) Map.of("runId", runId));
            })
            .orElseGet(() -> ResponseEntity.status(409).build());
    }
}
