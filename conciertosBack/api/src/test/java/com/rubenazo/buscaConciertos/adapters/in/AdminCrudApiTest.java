package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.application.ports.in.ManualCrudInputPort;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCrudApiTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-09T12:00:00Z");

    @Mock
    private ManualCrudInputPort manualCrudInputPort;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminCrudApi api = new AdminCrudApi(manualCrudInputPort);
        mockMvc = MockMvcBuilders
            .standaloneSetup(api)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    // ── DELETE /api/admin/concerts/{id} ───────────────────────────────────────

    @Test
    void deleteConcert_returns204() throws Exception {
        doNothing().when(manualCrudInputPort).deleteConcert("c1");

        mockMvc.perform(delete("/api/admin/concerts/c1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteConcert_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Concert not found: c-missing"))
            .when(manualCrudInputPort).deleteConcert("c-missing");

        mockMvc.perform(delete("/api/admin/concerts/c-missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ── POST /api/admin/salas ─────────────────────────────────────────────────

    @Test
    void createSala_returns201() throws Exception {
        SalaConcierto sala = new SalaConcierto(
            "manual-sala-test-madrid", "Sala Test", "Calle 1", "Madrid", "Madrid",
            40.0, -3.7, null, null, "manual", FIXED_NOW
        );
        when(manualCrudInputPort.createSala(any())).thenReturn(sala);

        mockMvc.perform(post("/api/admin/salas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sala Test\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("manual-sala-test-madrid"));
    }

    @Test
    void createSala_returns400OnValidationFailure() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required"))
            .when(manualCrudInputPort).createSala(any());

        mockMvc.perform(post("/api/admin/salas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("name is required"))
            .andExpect(jsonPath("$.status").value(400));
    }

    // ── POST /api/admin/artists ───────────────────────────────────────────────

    @Test
    void createArtist_returns201() throws Exception {
        Artist artist = new Artist(
            "manual-artist-artista-test", "Artista Test", null, null, null,
            "manual", null, FIXED_NOW
        );
        when(manualCrudInputPort.createArtist(any())).thenReturn(artist);

        mockMvc.perform(post("/api/admin/artists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Artista Test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("manual-artist-artista-test"));
    }

    @Test
    void createArtist_returns400OnBlankName() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required"))
            .when(manualCrudInputPort).createArtist(any());

        mockMvc.perform(post("/api/admin/artists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    // ── POST /api/admin/concerts ──────────────────────────────────────────────

    @Test
    void createConcert_returns201() throws Exception {
        Concert concert = new Concert(
            "manual-sala1-2026-08-01", "sala1", List.of("a1"),
            LocalDate.of(2026, 8, 1), null, null,
            "manual", FIXED_NOW
        );
        when(manualCrudInputPort.createConcert(any())).thenReturn(concert);

        mockMvc.perform(post("/api/admin/concerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salaConciertoId\":\"sala1\",\"artistIds\":[\"a1\"],\"date\":\"2026-08-01\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("manual-sala1-2026-08-01"));
    }

    @Test
    void createConcert_returns400OnMissingDate() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required"))
            .when(manualCrudInputPort).createConcert(any());

        mockMvc.perform(post("/api/admin/concerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salaConciertoId\":\"sala1\",\"artistIds\":[\"a1\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createConcert_returns422OnMissingSala() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Sala not found: S_MISSING"))
            .when(manualCrudInputPort).createConcert(any());

        mockMvc.perform(post("/api/admin/concerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"salaConciertoId\":\"S_MISSING\",\"artistIds\":[\"a1\"],\"date\":\"2026-08-01\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error").value("Sala not found: S_MISSING"))
            .andExpect(jsonPath("$.status").value(422));
    }

    // ── PUT /api/admin/salas/{id} ─────────────────────────────────────────────

    @Test
    void updateSala_returns200WithUpdatedSala() throws Exception {
        SalaConcierto updated = new SalaConcierto(
            "sala-1", "New Name", "New Address", "New City", "New Province",
            41.5, -2.5, "https://example.com/img.jpg", "New description", "manual", FIXED_NOW
        );
        when(manualCrudInputPort.updateSala(eq("sala-1"), any())).thenReturn(updated);

        mockMvc.perform(put("/api/admin/salas/sala-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\",\"address\":\"New Address\",\"city\":\"New City\","
                    + "\"province\":\"New Province\",\"lat\":41.5,\"lng\":-2.5,"
                    + "\"image_url\":\"https://example.com/img.jpg\",\"description\":\"New description\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("sala-1"))
            .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateSala_returns400OnBlankName() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required"))
            .when(manualCrudInputPort).updateSala(eq("sala-1"), any());

        mockMvc.perform(put("/api/admin/salas/sala-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateSala_returns400OnIdMismatch() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "id in body (sala-2) does not match id in path (sala-1)"))
            .when(manualCrudInputPort).updateSala(eq("sala-1"), any());

        mockMvc.perform(put("/api/admin/salas/sala-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"sala-2\",\"name\":\"New Name\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateSala_returns404WhenMissing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala not found: sala-missing"))
            .when(manualCrudInputPort).updateSala(eq("sala-missing"), any());

        mockMvc.perform(put("/api/admin/salas/sala-missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ── PUT /api/admin/artists/{id} ───────────────────────────────────────────

    @Test
    void updateArtist_returns200WithUpdatedArtist() throws Exception {
        Artist updated = new Artist(
            "artist-1", "New Name", "New Genre", "https://example.com/img.jpg", null,
            "manual", "New description", FIXED_NOW
        );
        when(manualCrudInputPort.updateArtist(eq("artist-1"), any())).thenReturn(updated);

        mockMvc.perform(put("/api/admin/artists/artist-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\",\"genre\":\"New Genre\",\"description\":\"New description\","
                    + "\"image_url\":\"https://example.com/img.jpg\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("artist-1"))
            .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateArtist_returns400OnBlankName() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required"))
            .when(manualCrudInputPort).updateArtist(eq("artist-1"), any());

        mockMvc.perform(put("/api/admin/artists/artist-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateArtist_returns404WhenMissing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found: artist-missing"))
            .when(manualCrudInputPort).updateArtist(eq("artist-missing"), any());

        mockMvc.perform(put("/api/admin/artists/artist-missing")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ── PUT /api/admin/concerts/{id} ──────────────────────────────────────────

    @Test
    void updateConcert_returns200WithUpdatedConcert() throws Exception {
        Concert updated = new Concert(
            "concert-1", "sala-1", List.of("artist-1"),
            LocalDate.of(2026, 9, 1), "22:00", "20€", "manual", FIXED_NOW
        );
        when(manualCrudInputPort.updateConcert(eq("concert-1"), any())).thenReturn(updated);

        mockMvc.perform(put("/api/admin/concerts/concert-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-09-01\",\"time\":\"22:00\",\"price\":\"20€\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("concert-1"))
            .andExpect(jsonPath("$.date").value("2026-09-01"));
    }

    @Test
    void updateConcert_returns400OnFKMismatch() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "salaConciertoId cannot be changed via edit"))
            .when(manualCrudInputPort).updateConcert(eq("concert-1"), any());

        mockMvc.perform(put("/api/admin/concerts/concert-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-09-01\",\"time\":\"22:00\",\"price\":\"20€\",\"salaConciertoId\":\"sala-DIFFERENT\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateConcert_returns404WhenMissingOrDeleted() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Concert not found: concert-1"))
            .when(manualCrudInputPort).updateConcert(eq("concert-1"), any());

        mockMvc.perform(put("/api/admin/concerts/concert-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-09-01\",\"time\":\"22:00\",\"price\":\"20€\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/admin/salas/{id}, /artists/{id}, /concerts/{id} ──────────────

    @Test
    void getSala_returns200WithCurrentValues() throws Exception {
        SalaConcierto sala = new SalaConcierto(
            "sala-1", "Sala Test", "Calle 1", "Madrid", "Madrid",
            40.0, -3.7, null, null, "manual", FIXED_NOW
        );
        when(manualCrudInputPort.getSala("sala-1")).thenReturn(sala);

        mockMvc.perform(get("/api/admin/salas/sala-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("sala-1"))
            .andExpect(jsonPath("$.name").value("Sala Test"));
    }

    @Test
    void getSala_returns404WhenMissing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala not found: sala-missing"))
            .when(manualCrudInputPort).getSala("sala-missing");

        mockMvc.perform(get("/api/admin/salas/sala-missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getArtist_returns200WithCurrentValues() throws Exception {
        Artist artist = new Artist(
            "artist-1", "Artist Test", "Rock", null, null, "manual", null, FIXED_NOW
        );
        when(manualCrudInputPort.getArtist("artist-1")).thenReturn(artist);

        mockMvc.perform(get("/api/admin/artists/artist-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("artist-1"))
            .andExpect(jsonPath("$.name").value("Artist Test"));
    }

    @Test
    void getArtist_returns404WhenMissing() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found: artist-missing"))
            .when(manualCrudInputPort).getArtist("artist-missing");

        mockMvc.perform(get("/api/admin/artists/artist-missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getConcert_returns200WithCurrentValues() throws Exception {
        Concert concert = new Concert(
            "concert-1", "sala-1", List.of("artist-1"),
            LocalDate.of(2026, 8, 1), "21:00", "15€", "manual", FIXED_NOW
        );
        when(manualCrudInputPort.getConcert("concert-1")).thenReturn(concert);

        mockMvc.perform(get("/api/admin/concerts/concert-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("concert-1"))
            .andExpect(jsonPath("$.date").value("2026-08-01"));
    }

    @Test
    void getConcert_returns404WhenMissingOrDeleted() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Concert not found: concert-missing"))
            .when(manualCrudInputPort).getConcert("concert-missing");

        mockMvc.perform(get("/api/admin/concerts/concert-missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/admin/salas, /artists, /concerts (admin list, including blocked) ─

    @Test
    void listSalas_returns200WithAllSalasIncludingBlocked() throws Exception {
        SalaConcierto blocked = new SalaConcierto(
            "sala-1", "Sala Test", "Calle 1", "Madrid", "Madrid",
            null, null, null, null, "manual", FIXED_NOW
        );
        when(manualCrudInputPort.listSalas()).thenReturn(List.of(blocked));

        mockMvc.perform(get("/api/admin/salas"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("sala-1"));
    }

    @Test
    void listArtists_returns200WithAllArtistsIncludingBlocked() throws Exception {
        Artist artist = new Artist(
            "artist-1", "Artist Test", "Rock", null, null, "manual", null, FIXED_NOW
        );
        when(manualCrudInputPort.listArtists()).thenReturn(List.of(artist));

        mockMvc.perform(get("/api/admin/artists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("artist-1"));
    }

    @Test
    void listConcerts_returns200ExcludingDeleted() throws Exception {
        Concert concert = new Concert(
            "concert-1", "sala-1", List.of("artist-1"),
            LocalDate.of(2026, 8, 1), "21:00", "15€", "manual", FIXED_NOW
        );
        when(manualCrudInputPort.listConcerts()).thenReturn(List.of(concert));

        mockMvc.perform(get("/api/admin/concerts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("concert-1"));
    }
}
