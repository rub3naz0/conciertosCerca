package com.rubenazo.buscaConciertos.adminweb.adapters.in;

import com.rubenazo.buscaConciertos.adminweb.adapters.in.dto.SevereIssueDto;
import com.rubenazo.buscaConciertos.adminweb.application.AdminQualityUseCase;
import com.rubenazo.buscaConciertos.adminweb.application.SevereIssue;
import com.rubenazo.buscaConciertos.adminweb.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminQualityApi.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.admin.username=admin",
    "app.admin.password=testpass"
})
class AdminQualityApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminQualityUseCase useCase;

    private static final Instant FIXED = Instant.parse("2026-06-01T00:00:00Z");

    // --- GET /admin/quality/severe returns JSON array with expected fields ---

    @Test
    void getSevere_returnsJsonArrayWithExpectedFields() throws Exception {
        when(useCase.listUnresolvedSevere()).thenReturn(List.of(
            new SevereIssue(1L, "sala", "s1", "address", "missing", "severe",
                "Calle Mayor 5", null, 0.95, FIXED, 12)
        ));

        mockMvc.perform(get("/admin/quality/severe")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            // SevereIssueDto fields — verify DTO projection, not raw domain record
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].entityType").value("sala"))
            .andExpect(jsonPath("$[0].entityId").value("s1"))
            .andExpect(jsonPath("$[0].field").value("address"))
            .andExpect(jsonPath("$[0].status").value("missing"))
            .andExpect(jsonPath("$[0].suggested").value("Calle Mayor 5"))
            .andExpect(jsonPath("$[0].score").value(0.95))
            .andExpect(jsonPath("$[0].severity").value("severe"))
            .andExpect(jsonPath("$[0].blockedConcertCount").value(12));
    }

    // --- GET /admin/quality/severe returns empty array when no issues ---

    @Test
    void getSevere_returnsEmptyArrayWhenNoIssues() throws Exception {
        when(useCase.listUnresolvedSevere()).thenReturn(List.of());

        mockMvc.perform(get("/admin/quality/severe")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    // --- POST /admin/quality/{id}/fill with valid body → 200 ---

    @Test
    void fill_returns200OnSuccess() throws Exception {
        doNothing().when(useCase).fill(7L, "Calle Mayor 5");

        mockMvc.perform(post("/admin/quality/7/fill")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"Calle Mayor 5\"}"))
            .andExpect(status().isOk());

        verify(useCase).fill(7L, "Calle Mayor 5");
    }
}
