package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.application.ports.in.SyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import com.rubenazo.buscaConciertos.domain.SyncRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SyncAdminApiTest {

    @Mock private SyncRunPort syncRunPort;
    @Mock private SyncInputPort syncInputPort;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SyncAdminApi api = new SyncAdminApi(syncRunPort, syncInputPort);
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void postSync_returns202WithRunIdWhenIdle() throws Exception {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("new-run-id"));

        mockMvc.perform(post("/api/admin/sync").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.runId").value("new-run-id"));
    }

    @Test
    void postSync_returns409WhenAlreadyRunning() throws Exception {
        when(syncRunPort.tryStart()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/sync").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());

        verify(syncInputPort, never()).execute(anyString());
    }

    @Test
    void postSync_triggersAsyncExecute() throws Exception {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("run-abc"));

        mockMvc.perform(post("/api/admin/sync").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isAccepted());

        verify(syncInputPort).execute("run-abc");
    }

    @Test
    void getLatest_returns200WithRun() throws Exception {
        SyncRun run = new SyncRun(
            "run-1", "completed", Instant.parse("2026-05-30T10:00:00Z"),
            Instant.parse("2026-05-30T11:00:00Z"),
            5, 10, 20, 0, 3, null, Instant.parse("2026-05-30T10:00:00Z")
        );
        when(syncRunPort.findLatest()).thenReturn(Optional.of(run));

        mockMvc.perform(get("/api/admin/sync/latest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run-1"))
            .andExpect(jsonPath("$.status").value("completed"));
    }

    @Test
    void getLatest_returns404WhenNoRuns() throws Exception {
        when(syncRunPort.findLatest()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/sync/latest"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getById_returns200WithRun() throws Exception {
        SyncRun run = new SyncRun(
            "run-xyz", "failed", Instant.parse("2026-05-30T10:00:00Z"),
            Instant.parse("2026-05-30T10:05:00Z"),
            0, 0, 0, 1, 0, "Connection refused", Instant.parse("2026-05-30T10:00:00Z")
        );
        when(syncRunPort.findById("run-xyz")).thenReturn(Optional.of(run));

        mockMvc.perform(get("/api/admin/sync/run-xyz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run-xyz"))
            .andExpect(jsonPath("$.status").value("failed"))
            .andExpect(jsonPath("$.errorMessage").value("Connection refused"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(syncRunPort.findById("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/sync/nonexistent"))
            .andExpect(status().isNotFound());
    }
}
