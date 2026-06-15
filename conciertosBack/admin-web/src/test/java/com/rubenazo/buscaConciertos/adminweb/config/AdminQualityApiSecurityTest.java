package com.rubenazo.buscaConciertos.adminweb.config;

import com.rubenazo.buscaConciertos.adminweb.adapters.in.AdminQualityApi;
import com.rubenazo.buscaConciertos.adminweb.application.AdminQualityUseCase;
import com.rubenazo.buscaConciertos.adminweb.application.SevereIssue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminQualityApi.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.admin.username=admin",
    "app.admin.password=testpass"
})
class AdminQualityApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminQualityUseCase useCase;

    // --- No credentials → 401 ---

    @Test
    void get_returns401WithNoCredentials() throws Exception {
        mockMvc.perform(get("/admin/quality/severe"))
            .andExpect(status().isUnauthorized());
    }

    // --- Valid Basic credentials → 200 ---

    @Test
    void get_returns200WithValidBasicCredentials() throws Exception {
        when(useCase.listUnresolvedSevere()).thenReturn(List.of(
            new SevereIssue(1L, "sala", "s1", "address", "missing", "severe",
                null, null, null, Instant.parse("2026-06-01T00:00:00Z"), 0)
        ));

        mockMvc.perform(get("/admin/quality/severe")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk());
    }

    // --- Wrong credentials → 401 ---

    @Test
    void get_returns401WithWrongCredentials() throws Exception {
        mockMvc.perform(get("/admin/quality/severe")
                .with(httpBasic("admin", "wrongpass")))
            .andExpect(status().isUnauthorized());
    }
}
