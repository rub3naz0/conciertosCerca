package com.rubenazo.buscaConciertos.adminweb.adapters.in;

import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ArtistProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ConcertProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.SalaConciertoProxyDto;
import com.rubenazo.buscaConciertos.adminweb.application.ports.out.AdminCrudProxyPort;
import com.rubenazo.buscaConciertos.adminweb.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCrudWebApi.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "app.admin.username=admin",
    "app.admin.password=testpass"
})
class AdminCrudWebApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminCrudProxyPort proxyPort;

    private static final SalaConciertoProxyDto SALA_DTO =
        new SalaConciertoProxyDto("manual-sala-test", "Test Sala", "Calle 1", "Madrid", "Madrid",
            null, null, null, null, null);

    private static final ArtistProxyDto ARTIST_DTO =
        new ArtistProxyDto("manual-artist-foo", "Foo Artist", null, null, null, null, null);

    private static final ConcertProxyDto CONCERT_DTO =
        new ConcertProxyDto("manual-concert-bar-2026-08-01", "manual-sala-bar",
            List.of("manual-artist-baz"), "2026-08-01", null, null, null, null);

    // admin mutations need the CSRF hardening header (see CsrfHardeningFilter)
    private static MockHttpServletRequestBuilder adminPost(String url) {
        return post(url).header("X-Requested-With", "XMLHttpRequest");
    }

    private static MockHttpServletRequestBuilder adminPut(String url) {
        return put(url).header("X-Requested-With", "XMLHttpRequest");
    }

    // --- GET /admin/concerts → 200 with concert list ---

    @Test
    void getConcerts_returns200WithList() throws Exception {
        when(proxyPort.listConcerts()).thenReturn(List.of(CONCERT_DTO));
        when(proxyPort.listSalas()).thenReturn(List.of(SALA_DTO));
        when(proxyPort.listArtists()).thenReturn(List.of(ARTIST_DTO));

        mockMvc.perform(get("/admin/concerts")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("manual-concert-bar-2026-08-01")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-08-01")));
    }

    // --- GET /admin/concerts unauthenticated → 401 ---

    @Test
    void getConcerts_returns401WithNoCredentials() throws Exception {
        mockMvc.perform(get("/admin/concerts"))
            .andExpect(status().isUnauthorized());
    }

    // --- POST /admin/concerts/{id}/delete → redirect to /admin/concerts ---

    @Test
    void deleteConcert_redirectsAfterSuccess() throws Exception {
        doNothing().when(proxyPort).deleteConcert("c-123");

        mockMvc.perform(adminPost("/admin/concerts/c-123/delete")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/admin/concerts")));
    }

    // --- POST /admin/salas → redirect with minted id shown ---

    @Test
    void createSala_redirectsAfterSuccess() throws Exception {
        when(proxyPort.createSala(any())).thenReturn(SALA_DTO);

        mockMvc.perform(adminPost("/admin/salas")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Test Sala")
                .param("address", "Calle 1")
                .param("city", "Madrid")
                .param("province", "Madrid"))
            .andExpect(status().is3xxRedirection());
    }

    // --- POST /admin/artists → redirect after success ---

    @Test
    void createArtist_redirectsAfterSuccess() throws Exception {
        when(proxyPort.createArtist(any())).thenReturn(ARTIST_DTO);

        mockMvc.perform(adminPost("/admin/artists")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Foo Artist"))
            .andExpect(status().is3xxRedirection());
    }

    // --- POST /admin/concerts → redirect after success ---

    @Test
    void createConcert_redirectsAfterSuccess() throws Exception {
        when(proxyPort.createConcert(any())).thenReturn(CONCERT_DTO);

        mockMvc.perform(adminPost("/admin/concerts")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("salaConciertoId", "manual-sala-bar")
                .param("artistIds", "manual-artist-baz")
                .param("date", "2026-08-01"))
            .andExpect(status().is3xxRedirection());
    }

    // --- POST /admin/salas api-error → shows error message to operator ---

    @Test
    void createSala_showsErrorOnApiValidationFailure() throws Exception {
        when(proxyPort.createSala(any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required"));
        when(proxyPort.listSalas()).thenReturn(List.of());
        when(proxyPort.listArtists()).thenReturn(List.of());

        mockMvc.perform(adminPost("/admin/salas")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name is required")));
    }

    // --- GET /admin/salas/{id}, /admin/artists/{id}, /admin/concerts/{id} → pre-fill ---

    @Test
    void getSala_returns200WithCurrentValues() throws Exception {
        when(proxyPort.getSala("manual-sala-test")).thenReturn(SALA_DTO);

        mockMvc.perform(get("/admin/salas/manual-sala-test")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("manual-sala-test"))
            .andExpect(jsonPath("$.name").value("Test Sala"));
    }

    @Test
    void getArtist_returns200WithCurrentValues() throws Exception {
        when(proxyPort.getArtist("manual-artist-foo")).thenReturn(ARTIST_DTO);

        mockMvc.perform(get("/admin/artists/manual-artist-foo")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("manual-artist-foo"))
            .andExpect(jsonPath("$.name").value("Foo Artist"));
    }

    @Test
    void getConcert_returns200WithCurrentValues() throws Exception {
        when(proxyPort.getConcert("manual-concert-bar-2026-08-01")).thenReturn(CONCERT_DTO);

        mockMvc.perform(get("/admin/concerts/manual-concert-bar-2026-08-01")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("manual-concert-bar-2026-08-01"))
            .andExpect(jsonPath("$.date").value("2026-08-01"));
    }

    @Test
    void getSala_propagates404WhenMissing() throws Exception {
        when(proxyPort.getSala("missing"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala not found"));

        mockMvc.perform(get("/admin/salas/missing")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isNotFound());
    }

    // --- GET /admin/salas-all, /admin/artists-all, /admin/concerts-all → including-blocked lists ---

    @Test
    void getSalasAll_returns200WithIncludingBlockedList() throws Exception {
        SalaConciertoProxyDto blocked = new SalaConciertoProxyDto(
            "sala-blocked", "Blocked Sala", null, null, null, null, null, null, null, null);
        when(proxyPort.listSalasIncludingBlocked()).thenReturn(List.of(SALA_DTO, blocked));

        mockMvc.perform(get("/admin/salas-all")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("sala-blocked")));
    }

    @Test
    void getArtistsAll_returns200WithIncludingBlockedList() throws Exception {
        ArtistProxyDto blocked = new ArtistProxyDto("artist-blocked", "Blocked Artist", null, null, null, null, null);
        when(proxyPort.listArtistsIncludingBlocked()).thenReturn(List.of(ARTIST_DTO, blocked));

        mockMvc.perform(get("/admin/artists-all")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("artist-blocked")));
    }

    @Test
    void getConcertsAll_returns200WithIncludingBlockedList() throws Exception {
        ConcertProxyDto blocked = new ConcertProxyDto(
            "concert-blocked", "sala-blocked", List.of(), "2026-08-05", null, null, null, null);
        when(proxyPort.listConcertsIncludingBlocked()).thenReturn(List.of(CONCERT_DTO, blocked));

        mockMvc.perform(get("/admin/concerts-all")
                .with(httpBasic("admin", "testpass")))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("concert-blocked")));
    }

    // --- PUT /admin/salas/{id} → save edited sala ---

    @Test
    void updateSala_returns200OnSuccess() throws Exception {
        SalaConciertoProxyDto updated = new SalaConciertoProxyDto(
            "manual-sala-test", "Updated Sala", "Calle 1", "Madrid", "Madrid", null, null, null, "Nice venue", null);
        when(proxyPort.updateSala(eq("manual-sala-test"), any())).thenReturn(updated);

        mockMvc.perform(adminPut("/admin/salas/manual-sala-test")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Updated Sala")
                .param("address", "Calle 1")
                .param("city", "Madrid")
                .param("province", "Madrid")
                .param("description", "Nice venue"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Sala"));
    }

    @Test
    void updateSala_showsErrorOnApiValidationFailure() throws Exception {
        when(proxyPort.updateSala(eq("manual-sala-test"), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required"));

        mockMvc.perform(adminPut("/admin/salas/manual-sala-test")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "")
                .param("address", "Calle 1")
                .param("city", "Madrid")
                .param("province", "Madrid"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name is required")));
    }

    @Test
    void updateSala_showsErrorOnApi404() throws Exception {
        when(proxyPort.updateSala(eq("sala-missing"), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala not found"));

        mockMvc.perform(adminPut("/admin/salas/sala-missing")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "X")
                .param("address", "Calle 1")
                .param("city", "Madrid")
                .param("province", "Madrid"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Sala not found")));
    }

    // --- PUT /admin/artists/{id} → save edited artist ---

    @Test
    void updateArtist_returns200OnSuccess() throws Exception {
        ArtistProxyDto updated = new ArtistProxyDto(
            "manual-artist-foo", "Updated Artist", "rock", null, null, null, null);
        when(proxyPort.updateArtist(eq("manual-artist-foo"), any())).thenReturn(updated);

        mockMvc.perform(adminPut("/admin/artists/manual-artist-foo")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "Updated Artist")
                .param("genre", "rock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Artist"));
    }

    @Test
    void updateArtist_showsErrorOnApiValidationFailure() throws Exception {
        when(proxyPort.updateArtist(eq("manual-artist-foo"), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required"));

        mockMvc.perform(adminPut("/admin/artists/manual-artist-foo")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", ""))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name is required")));
    }

    // --- PUT /admin/concerts/{id} → save edited concert (date/time/price only) ---

    @Test
    void updateConcert_returns200OnSuccess() throws Exception {
        ConcertProxyDto updated = new ConcertProxyDto(
            "manual-concert-bar-2026-08-01", "manual-sala-bar", List.of("manual-artist-baz"),
            "2026-08-02", "22:00", "15", null, null);
        when(proxyPort.updateConcert(eq("manual-concert-bar-2026-08-01"), any())).thenReturn(updated);

        mockMvc.perform(adminPut("/admin/concerts/manual-concert-bar-2026-08-01")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("date", "2026-08-02")
                .param("time", "22:00")
                .param("price", "15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.date").value("2026-08-02"));
    }

    @Test
    void updateConcert_showsErrorOnApiFkMismatch() throws Exception {
        when(proxyPort.updateConcert(eq("manual-concert-bar-2026-08-01"), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "salaConciertoId does not match"));

        mockMvc.perform(adminPut("/admin/concerts/manual-concert-bar-2026-08-01")
                .with(httpBasic("admin", "testpass"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("date", "2026-08-02"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("salaConciertoId does not match")));
    }
}
