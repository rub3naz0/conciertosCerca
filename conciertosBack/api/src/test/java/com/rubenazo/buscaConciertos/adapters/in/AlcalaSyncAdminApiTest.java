package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.application.ports.in.AlcalaSyncInputPort;
import com.rubenazo.buscaConciertos.application.ports.out.SyncRunPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AlcalaSyncAdminApiTest {

    @Mock private SyncRunPort syncRunPort;
    @Mock private AlcalaSyncInputPort alcalaSyncInputPort;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AlcalaSyncAdminApi api = new AlcalaSyncAdminApi(syncRunPort, alcalaSyncInputPort);
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    @Test
    void postAlcalaSyncReturns202AndRunIdWhenIdle() throws Exception {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("run-1"));

        mockMvc.perform(post("/api/admin/sync/alcala").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.runId").value("run-1"));

        verify(alcalaSyncInputPort).execute("run-1");
    }

    @Test
    void postAlcalaSyncReturns409WhenRunAlreadyInFlight() throws Exception {
        when(syncRunPort.tryStart()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/sync/alcala").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());

        verify(alcalaSyncInputPort, never()).execute(anyString());
    }

    @Test
    void postAlcalaSyncForwardsFromToParams() throws Exception {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("run-1"));

        mockMvc.perform(post("/api/admin/sync/alcala")
                .param("from", "2026-07-01")
                .param("to", "2026-07-31")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.runId").value("run-1"));

        verify(alcalaSyncInputPort).execute(
            "run-1",
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        );
    }

    @Test
    void postAlcalaSyncWithOnlyFromCallsThreeArgOverload() throws Exception {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("run-1"));

        mockMvc.perform(post("/api/admin/sync/alcala")
                .param("from", "2026-07-01")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isAccepted());

        verify(alcalaSyncInputPort).execute(
            "run-1",
            LocalDate.of(2026, 7, 1),
            null
        );
        verify(alcalaSyncInputPort, never()).execute("run-1");
    }

    @Test
    void postAlcalaSyncWithoutParamsCallsSingleArgOverload() throws Exception {
        when(syncRunPort.tryStart()).thenReturn(Optional.of("run-1"));

        mockMvc.perform(post("/api/admin/sync/alcala").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isAccepted());

        verify(alcalaSyncInputPort).execute("run-1");
        verify(alcalaSyncInputPort, never()).execute(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
