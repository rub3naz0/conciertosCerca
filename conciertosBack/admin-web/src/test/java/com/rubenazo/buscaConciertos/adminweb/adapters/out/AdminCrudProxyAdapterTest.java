package com.rubenazo.buscaConciertos.adminweb.adapters.out;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ArtistProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ConcertProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.SalaConciertoProxyDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminCrudProxyAdapterTest {

    private WireMockServer wireMock;
    private AdminCrudProxyAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        String baseUrl = "http://localhost:" + wireMock.port();
        adapter = new AdminCrudProxyAdapter(new RestTemplate(), baseUrl);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    // --- deleteConcert: calls DELETE /api/admin/concerts/{id} ---

    @Test
    void deleteConcert_callsDeleteEndpoint() {
        stubFor(delete(urlEqualTo("/api/admin/concerts/id-X"))
            .willReturn(aResponse().withStatus(204)));

        assertThatCode(() -> adapter.deleteConcert("id-X"))
            .doesNotThrowAnyException();
    }

    @Test
    void deleteConcert_throws404WhenNotFound() {
        stubFor(delete(urlEqualTo("/api/admin/concerts/missing"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Concert not found\",\"status\":404}")));

        assertThatThrownBy(() -> adapter.deleteConcert("missing"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- createSala: POSTs to /api/admin/salas and returns SalaConciertoProxyDto ---

    @Test
    void createSala_postsAndReturnsSalaDto() {
        stubFor(post(urlEqualTo("/api/admin/salas"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"manual-sala-test\",\"name\":\"Test Sala\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}")));

        SalaConciertoProxyDto result = adapter.createSala(
            Map.of("name", "Test Sala", "address", "Calle 1", "city", "Madrid", "province", "Madrid"));

        assertThat(result.id()).isEqualTo("manual-sala-test");
        assertThat(result.name()).isEqualTo("Test Sala");
    }

    @Test
    void createSala_throws400OnValidationError() {
        stubFor(post(urlEqualTo("/api/admin/salas"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"name is required\",\"status\":400}")));

        assertThatThrownBy(() -> adapter.createSala(Map.of("name", "")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).contains("name is required");
            });
    }

    // --- createArtist: POSTs to /api/admin/artists and returns ArtistProxyDto ---

    @Test
    void createArtist_postsAndReturnsArtistDto() {
        stubFor(post(urlEqualTo("/api/admin/artists"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"manual-artist-foo\",\"name\":\"Foo Artist\"}")));

        ArtistProxyDto result = adapter.createArtist(Map.of("name", "Foo Artist"));

        assertThat(result.id()).isEqualTo("manual-artist-foo");
        assertThat(result.name()).isEqualTo("Foo Artist");
    }

    @Test
    void createArtist_throws400OnBlankName() {
        stubFor(post(urlEqualTo("/api/admin/artists"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"name is required\",\"status\":400}")));

        assertThatThrownBy(() -> adapter.createArtist(Map.of("name", "")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- createConcert: POSTs to /api/admin/concerts and returns ConcertProxyDto ---

    @Test
    void createConcert_postsAndReturnsConcertDto() {
        stubFor(post(urlEqualTo("/api/admin/concerts"))
            .willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"manual-concert-bar-2026-08-01\",\"salaConcierto_id\":\"manual-sala-bar\",\"artist_ids\":[\"manual-artist-baz\"],\"date\":\"2026-08-01\"}")));

        ConcertProxyDto result = adapter.createConcert(
            Map.of("salaConciertoId", "manual-sala-bar", "artistIds", List.of("manual-artist-baz"), "date", "2026-08-01"));

        assertThat(result.id()).startsWith("manual-");
    }

    @Test
    void createConcert_throws422OnMissingSala() {
        stubFor(post(urlEqualTo("/api/admin/concerts"))
            .willReturn(aResponse()
                .withStatus(422)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"sala S_MISSING not found\",\"status\":422}")));

        assertThatThrownBy(() -> adapter.createConcert(
            Map.of("salaConciertoId", "S_MISSING", "artistIds", List.of("a1"), "date", "2026-08-01")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).contains("S_MISSING");
            });
    }

    // --- listConcerts: GET /api/v1/concerts?since=epoch returns list ---

    @Test
    void listConcerts_returnsListFromApi() {
        stubFor(get(urlPathEqualTo("/api/v1/concerts"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"timestamp\":\"2026-06-01T00:00:00Z\",\"data\":[{\"id\":\"c1\",\"salaConcierto_id\":\"s1\",\"artist_ids\":[\"a1\"],\"date\":\"2026-06-10\"}]}")));

        List<ConcertProxyDto> concerts = adapter.listConcerts();

        assertThat(concerts).hasSize(1);
        assertThat(concerts.get(0).id()).isEqualTo("c1");
    }

    // --- listSalas: GET /api/v1/salas-concierto?since=epoch returns list ---

    @Test
    void listSalas_returnsListFromApi() {
        stubFor(get(urlPathEqualTo("/api/v1/salas-concierto"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"timestamp\":\"2026-06-01T00:00:00Z\",\"data\":[{\"id\":\"s1\",\"name\":\"Sala One\",\"address\":\"C/1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}]}")));

        List<SalaConciertoProxyDto> salas = adapter.listSalas();

        assertThat(salas).hasSize(1);
        assertThat(salas.get(0).name()).isEqualTo("Sala One");
    }

    // --- listArtists: GET /api/v1/artists?since=epoch returns list ---

    @Test
    void listArtists_returnsListFromApi() {
        stubFor(get(urlPathEqualTo("/api/v1/artists"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"timestamp\":\"2026-06-01T00:00:00Z\",\"data\":[{\"id\":\"a1\",\"name\":\"Artist One\"}]}")));

        List<ArtistProxyDto> artists = adapter.listArtists();

        assertThat(artists).hasSize(1);
        assertThat(artists.get(0).name()).isEqualTo("Artist One");
    }

    // --- updateSala: PUT /api/admin/salas/{id} returns updated SalaConciertoProxyDto ---

    @Test
    void updateSala_putsAndReturnsSalaDto() {
        stubFor(put(urlEqualTo("/api/admin/salas/sala-1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"sala-1\",\"name\":\"Sala Updated\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}")));

        SalaConciertoProxyDto result = adapter.updateSala("sala-1",
            Map.of("name", "Sala Updated", "address", "Calle 1", "city", "Madrid", "province", "Madrid"));

        assertThat(result.id()).isEqualTo("sala-1");
        assertThat(result.name()).isEqualTo("Sala Updated");
    }

    @Test
    void updateSala_throws404WhenMissing() {
        stubFor(put(urlEqualTo("/api/admin/salas/missing"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Sala not found\",\"status\":404}")));

        assertThatThrownBy(() -> adapter.updateSala("missing", Map.of("name", "X")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- updateArtist: PUT /api/admin/artists/{id} returns updated ArtistProxyDto ---

    @Test
    void updateArtist_putsAndReturnsArtistDto() {
        stubFor(put(urlEqualTo("/api/admin/artists/artist-1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"artist-1\",\"name\":\"Artist Updated\"}")));

        ArtistProxyDto result = adapter.updateArtist("artist-1", Map.of("name", "Artist Updated"));

        assertThat(result.id()).isEqualTo("artist-1");
        assertThat(result.name()).isEqualTo("Artist Updated");
    }

    @Test
    void updateArtist_throws400OnValidationError() {
        stubFor(put(urlEqualTo("/api/admin/artists/artist-1"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"name is required\",\"status\":400}")));

        assertThatThrownBy(() -> adapter.updateArtist("artist-1", Map.of("name", "")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).contains("name is required");
            });
    }

    // --- updateConcert: PUT /api/admin/concerts/{id} returns updated ConcertProxyDto ---

    @Test
    void updateConcert_putsAndReturnsConcertDto() {
        stubFor(put(urlEqualTo("/api/admin/concerts/concert-1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"concert-1\",\"salaConcierto_id\":\"sala-1\",\"artist_ids\":[\"artist-1\"],\"date\":\"2026-08-02\",\"time\":\"22:00\",\"price\":\"15\"}")));

        ConcertProxyDto result = adapter.updateConcert("concert-1",
            Map.of("date", "2026-08-02", "time", "22:00", "price", "15"));

        assertThat(result.id()).isEqualTo("concert-1");
        assertThat(result.date()).isEqualTo("2026-08-02");
    }

    @Test
    void updateConcert_throws400OnFkMismatch() {
        stubFor(put(urlEqualTo("/api/admin/concerts/concert-1"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"salaConciertoId does not match\",\"status\":400}")));

        assertThatThrownBy(() -> adapter.updateConcert("concert-1",
            Map.of("date", "2026-08-02", "salaConciertoId", "other-sala")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- getSala/getArtist/getConcert: GET /api/admin/{resource}/{id} pre-fill ---

    @Test
    void getSala_returnsCurrentValues() {
        stubFor(get(urlEqualTo("/api/admin/salas/sala-1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"sala-1\",\"name\":\"Sala One\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"}")));

        SalaConciertoProxyDto result = adapter.getSala("sala-1");

        assertThat(result.id()).isEqualTo("sala-1");
        assertThat(result.name()).isEqualTo("Sala One");
    }

    @Test
    void getSala_throws404WhenMissing() {
        stubFor(get(urlEqualTo("/api/admin/salas/missing"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Sala not found\",\"status\":404}")));

        assertThatThrownBy(() -> adapter.getSala("missing"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getArtist_returnsCurrentValues() {
        stubFor(get(urlEqualTo("/api/admin/artists/artist-1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"artist-1\",\"name\":\"Artist One\"}")));

        ArtistProxyDto result = adapter.getArtist("artist-1");

        assertThat(result.id()).isEqualTo("artist-1");
        assertThat(result.name()).isEqualTo("Artist One");
    }

    @Test
    void getConcert_returnsCurrentValues() {
        stubFor(get(urlEqualTo("/api/admin/concerts/concert-1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"concert-1\",\"salaConcierto_id\":\"sala-1\",\"artist_ids\":[\"artist-1\"],\"date\":\"2026-08-01\",\"time\":\"21:00\",\"price\":\"10\"}")));

        ConcertProxyDto result = adapter.getConcert("concert-1");

        assertThat(result.id()).isEqualTo("concert-1");
        assertThat(result.date()).isEqualTo("2026-08-01");
    }

    @Test
    void getConcert_throws404WhenSoftDeleted() {
        stubFor(get(urlEqualTo("/api/admin/concerts/concert-deleted"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":\"Concert not found\",\"status\":404}")));

        assertThatThrownBy(() -> adapter.getConcert("concert-deleted"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- list*IncludingBlocked: GET /api/admin/{resource} returns list including blocked entities ---

    @Test
    void listSalasIncludingBlocked_returnsListFromAdminEndpoint() {
        stubFor(get(urlEqualTo("/api/admin/salas"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"id\":\"sala-1\",\"name\":\"Sala One\",\"address\":\"Calle 1\",\"city\":\"Madrid\",\"province\":\"Madrid\"},{\"id\":\"sala-blocked\",\"name\":\"Blocked Sala\"}]")));

        List<SalaConciertoProxyDto> result = adapter.listSalasIncludingBlocked();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).id()).isEqualTo("sala-blocked");
    }

    @Test
    void listArtistsIncludingBlocked_returnsListFromAdminEndpoint() {
        stubFor(get(urlEqualTo("/api/admin/artists"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"id\":\"artist-1\",\"name\":\"Artist One\"},{\"id\":\"artist-blocked\",\"name\":\"Blocked Artist\"}]")));

        List<ArtistProxyDto> result = adapter.listArtistsIncludingBlocked();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).id()).isEqualTo("artist-blocked");
    }

    @Test
    void listConcertsIncludingBlocked_returnsListFromAdminEndpoint() {
        stubFor(get(urlEqualTo("/api/admin/concerts"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[{\"id\":\"concert-1\",\"salaConcierto_id\":\"sala-1\",\"artist_ids\":[\"artist-1\"],\"date\":\"2026-08-01\"},{\"id\":\"concert-blocked\",\"salaConcierto_id\":\"sala-blocked\",\"artist_ids\":[],\"date\":\"2026-08-05\"}]")));

        List<ConcertProxyDto> result = adapter.listConcertsIncludingBlocked();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).id()).isEqualTo("concert-blocked");
    }
}
