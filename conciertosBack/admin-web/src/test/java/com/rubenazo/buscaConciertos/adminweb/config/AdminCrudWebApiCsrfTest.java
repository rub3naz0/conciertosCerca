package com.rubenazo.buscaConciertos.adminweb.config;

import com.rubenazo.buscaConciertos.adminweb.adapters.in.AdminCrudWebApi;
import com.rubenazo.buscaConciertos.adminweb.application.ports.out.AdminCrudProxyPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCrudWebApi.class)
@Import({SecurityConfig.class, CsrfHardeningFilter.class})
@TestPropertySource(properties = {
    "app.admin.username=admin",
    "app.admin.password=testpass"
})
class AdminCrudWebApiCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminCrudProxyPort proxyPort;

    // --- Mutations without the CSRF header are rejected even when authenticated ---

    @Test
    void deleteConcert_403WithCredsButWithoutCsrfHeader() throws Exception {
        mockMvc.perform(post("/admin/concerts/c1/delete")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isForbidden());
    }

    @Test
    void createSala_403WithCredsButWithoutCsrfHeader() throws Exception {
        mockMvc.perform(post("/admin/salas")
                .with(httpBasic("admin", "testpass"))
                .param("name", "Sala X"))
            .andExpect(status().isForbidden());
    }

    // --- Mutations with the CSRF header pass through to the controller ---

    @Test
    void deleteConcert_passesWithCsrfHeader() throws Exception {
        mockMvc.perform(post("/admin/concerts/c1/delete")
                .with(httpBasic("admin", "testpass"))
                .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isSeeOther());
    }

    // --- Reads stay header-free ---

    @Test
    void listConcerts_doesNotRequireCsrfHeader() throws Exception {
        mockMvc.perform(get("/admin/concerts")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk());
    }

    // --- PUT edit routes without the CSRF header are rejected even when authenticated ---

    @Test
    void updateSala_403WithCredsButWithoutCsrfHeader() throws Exception {
        mockMvc.perform(put("/admin/salas/sala-1")
                .with(httpBasic("admin", "testpass"))
                .param("name", "Sala X"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateArtist_403WithCredsButWithoutCsrfHeader() throws Exception {
        mockMvc.perform(put("/admin/artists/artist-1")
                .with(httpBasic("admin", "testpass"))
                .param("name", "Artist X"))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateConcert_403WithCredsButWithoutCsrfHeader() throws Exception {
        mockMvc.perform(put("/admin/concerts/concert-1")
                .with(httpBasic("admin", "testpass"))
                .param("date", "2026-08-02"))
            .andExpect(status().isForbidden());
    }

    // --- GET pre-fill routes stay header-free ---

    @Test
    void getSala_doesNotRequireCsrfHeader() throws Exception {
        mockMvc.perform(get("/admin/salas/sala-1")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk());
    }
}
