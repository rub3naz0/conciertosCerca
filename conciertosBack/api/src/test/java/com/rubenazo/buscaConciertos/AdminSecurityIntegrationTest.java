package com.rubenazo.buscaConciertos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicSyncEndpoints_accessibleWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/concerts")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/artists")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/salas-concierto")).andExpect(status().isOk());
    }

    @Test
    void adminQuality_401WithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/admin/quality"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminDeleteConcert_401WithoutCredentials() throws Exception {
        mockMvc.perform(delete("/api/admin/concerts/some-id"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminSyncLatest_401WithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/admin/sync/latest"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_401WithWrongPassword() throws Exception {
        mockMvc.perform(get("/api/admin/quality").with(httpBasic("test-admin", "wrong")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_reachesControllerWithValidCredentials() throws Exception {
        // 404 (not 401/403) proves the request passed the security filter into the controller
        mockMvc.perform(delete("/api/admin/concerts/missing-id")
                .with(httpBasic("test-admin", "test-pass"))
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNotFound());
    }

    // ── CSRF hardening: mutations must not be CORS "simple requests" ─────────

    @Test
    void adminMutation_403WithCredsButWithoutCsrfHeader() throws Exception {
        mockMvc.perform(post("/api/admin/quality/99999/approve")
                .with(httpBasic("test-admin", "test-pass")))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminMutation_passesWithCsrfHeader() throws Exception {
        mockMvc.perform(post("/api/admin/quality/99999/approve")
                .with(httpBasic("test-admin", "test-pass"))
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminMutation_passesWithJsonContentType() throws Exception {
        // JSON content type already forces a CORS preflight — no header needed
        mockMvc.perform(post("/api/admin/salas")
                .with(httpBasic("test-admin", "test-pass"))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminGet_doesNotRequireCsrfHeader() throws Exception {
        mockMvc.perform(get("/api/admin/quality")
                .with(httpBasic("test-admin", "test-pass")))
            .andExpect(status().isOk());
    }

    // ── POST /api/admin/geocoding/fill-from-scraper ─────────────────────────

    @Test
    void fillFromScraper_401WithoutCredentials() throws Exception {
        mockMvc.perform(post("/api/admin/geocoding/fill-from-scraper?dryRun=true&limit=10"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void fillFromScraper_403WithCredsButWithoutCsrfHeader() throws Exception {
        mockMvc.perform(post("/api/admin/geocoding/fill-from-scraper?dryRun=true&limit=10")
                .with(httpBasic("test-admin", "test-pass")))
            .andExpect(status().isForbidden());
    }

    @Test
    void fillFromScraper_dryRun_returns200WithReportShape() throws Exception {
        mockMvc.perform(post("/api/admin/geocoding/fill-from-scraper?dryRun=true&limit=10")
                .with(httpBasic("test-admin", "test-pass"))
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dryRun").value(true))
            .andExpect(jsonPath("$.limit").value(10))
            .andExpect(jsonPath("$.scanned").exists())
            .andExpect(jsonPath("$.written").exists())
            .andExpect(jsonPath("$.wouldWrite").exists())
            .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void fillFromScraper_limitZero_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/geocoding/fill-from-scraper?dryRun=true&limit=0")
                .with(httpBasic("test-admin", "test-pass"))
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isBadRequest());
    }
}
