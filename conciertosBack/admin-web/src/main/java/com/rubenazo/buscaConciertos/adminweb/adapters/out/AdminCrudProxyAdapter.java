package com.rubenazo.buscaConciertos.adminweb.adapters.out;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ArtistProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ConcertProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.SalaConciertoProxyDto;
import com.rubenazo.buscaConciertos.adminweb.application.ports.out.AdminCrudProxyPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Component
public class AdminCrudProxyAdapter implements AdminCrudProxyPort {

    // Internal wrapper record matching the {timestamp, data} response envelope from the api.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SyncWrapper<T>(String timestamp, List<T> data) {}

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AdminCrudProxyAdapter(RestTemplate restTemplate,
                                  @Value("${app.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public void deleteConcert(String id) {
        String url = baseUrl + "/api/admin/concerts/" + id;
        try {
            restTemplate.delete(url);
        } catch (HttpClientErrorException ex) {
            throw toResponseStatusException(ex);
        }
    }

    @Override
    public SalaConciertoProxyDto createSala(Map<String, Object> body) {
        return postForDto("/api/admin/salas", body, SalaConciertoProxyDto.class);
    }

    @Override
    public ArtistProxyDto createArtist(Map<String, Object> body) {
        return postForDto("/api/admin/artists", body, ArtistProxyDto.class);
    }

    @Override
    public ConcertProxyDto createConcert(Map<String, Object> body) {
        return postForDto("/api/admin/concerts", body, ConcertProxyDto.class);
    }

    @Override
    public List<ConcertProxyDto> listConcerts() {
        return getList(
            "/api/v1/concerts?since=1970-01-01T00:00:00Z",
            new ParameterizedTypeReference<SyncWrapper<ConcertProxyDto>>() {}
        );
    }

    @Override
    public List<SalaConciertoProxyDto> listSalas() {
        return getList(
            "/api/v1/salas-concierto?since=1970-01-01T00:00:00Z",
            new ParameterizedTypeReference<SyncWrapper<SalaConciertoProxyDto>>() {}
        );
    }

    @Override
    public List<ArtistProxyDto> listArtists() {
        return getList(
            "/api/v1/artists?since=1970-01-01T00:00:00Z",
            new ParameterizedTypeReference<SyncWrapper<ArtistProxyDto>>() {}
        );
    }

    // --- Edit (PUT) ---

    @Override
    public SalaConciertoProxyDto updateSala(String id, Map<String, Object> body) {
        return exchangeForDto("/api/admin/salas/" + id, HttpMethod.PUT, body, SalaConciertoProxyDto.class);
    }

    @Override
    public ArtistProxyDto updateArtist(String id, Map<String, Object> body) {
        return exchangeForDto("/api/admin/artists/" + id, HttpMethod.PUT, body, ArtistProxyDto.class);
    }

    @Override
    public ConcertProxyDto updateConcert(String id, Map<String, Object> body) {
        return exchangeForDto("/api/admin/concerts/" + id, HttpMethod.PUT, body, ConcertProxyDto.class);
    }

    // --- Pre-fill (GET by id, including blocked/deleted) ---

    @Override
    public SalaConciertoProxyDto getSala(String id) {
        return getForDto("/api/admin/salas/" + id, SalaConciertoProxyDto.class);
    }

    @Override
    public ArtistProxyDto getArtist(String id) {
        return getForDto("/api/admin/artists/" + id, ArtistProxyDto.class);
    }

    @Override
    public ConcertProxyDto getConcert(String id) {
        return getForDto("/api/admin/concerts/" + id, ConcertProxyDto.class);
    }

    // --- Admin lists including blocked entities ---

    @Override
    public List<SalaConciertoProxyDto> listSalasIncludingBlocked() {
        return getRawList("/api/admin/salas", new ParameterizedTypeReference<List<SalaConciertoProxyDto>>() {});
    }

    @Override
    public List<ArtistProxyDto> listArtistsIncludingBlocked() {
        return getRawList("/api/admin/artists", new ParameterizedTypeReference<List<ArtistProxyDto>>() {});
    }

    @Override
    public List<ConcertProxyDto> listConcertsIncludingBlocked() {
        return getRawList("/api/admin/concerts", new ParameterizedTypeReference<List<ConcertProxyDto>>() {});
    }

    // --- helpers ---

    private <T> T postForDto(String path, Map<String, Object> body, Class<T> responseType) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<T> response = restTemplate.postForEntity(url, request, responseType);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw toResponseStatusException(ex);
        }
    }

    private <T> T exchangeForDto(String path, HttpMethod method, Map<String, Object> body, Class<T> responseType) {
        String url = baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<T> response = restTemplate.exchange(url, method, request, responseType);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw toResponseStatusException(ex);
        }
    }

    private <T> T getForDto(String path, Class<T> responseType) {
        String url = baseUrl + path;
        try {
            ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, null, responseType);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw toResponseStatusException(ex);
        }
    }

    private <T> List<T> getRawList(String path, ParameterizedTypeReference<List<T>> typeRef) {
        String url = baseUrl + path;
        try {
            ResponseEntity<List<T>> response = restTemplate.exchange(url, HttpMethod.GET, null, typeRef);
            List<T> result = response.getBody();
            return result != null ? result : List.of();
        } catch (HttpClientErrorException ex) {
            throw toResponseStatusException(ex);
        }
    }

    private <T> List<T> getList(String path, ParameterizedTypeReference<SyncWrapper<T>> typeRef) {
        String url = baseUrl + path;
        try {
            ResponseEntity<SyncWrapper<T>> response =
                restTemplate.exchange(url, HttpMethod.GET, null, typeRef);
            SyncWrapper<T> wrapper = response.getBody();
            return wrapper != null && wrapper.data() != null ? wrapper.data() : List.of();
        } catch (HttpClientErrorException ex) {
            throw toResponseStatusException(ex);
        }
    }

    private ResponseStatusException toResponseStatusException(HttpClientErrorException ex) {
        // Extract the {error} field from the api response body for a user-visible message.
        String body = ex.getResponseBodyAsString();
        String errorMessage = extractErrorField(body);
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return new ResponseStatusException(status, errorMessage);
    }

    /**
     * Extracts the "error" string value from a JSON body like {"error":"...", "status":400}.
     * Falls back to the raw body if parsing fails.
     */
    private String extractErrorField(String json) {
        if (json == null || json.isBlank()) return "API error";
        // Simple extraction — avoid pulling in ObjectMapper for a one-field parse.
        int idx = json.indexOf("\"error\"");
        if (idx < 0) return json;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return json;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return json;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return json;
        return json.substring(start + 1, end);
    }
}
