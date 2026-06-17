package com.rubenazo.buscaConciertos.adminweb.adapters.in;

import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ArtistProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.ConcertProxyDto;
import com.rubenazo.buscaConciertos.adminweb.adapters.out.dto.SalaConciertoProxyDto;
import com.rubenazo.buscaConciertos.adminweb.application.ports.out.AdminCrudProxyPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Browser-facing admin CRUD endpoints for manual concert/sala/artist management.
 * All routes are protected by the existing HTTP-Basic SecurityConfig.
 * POST success → 303 redirect to /admin/concerts.
 * POST failure → 200 with {error} body (operator sees the message in the UI).
 */
@RestController
public class AdminCrudWebApi {

    private final AdminCrudProxyPort proxyPort;

    public AdminCrudWebApi(AdminCrudProxyPort proxyPort) {
        this.proxyPort = proxyPort;
    }

    // --- Concert list ---

    @GetMapping("/admin/concerts")
    public ResponseEntity<List<ConcertProxyDto>> listConcerts() {
        List<ConcertProxyDto> concerts = proxyPort.listConcerts();
        return ResponseEntity.ok(concerts);
    }

    // --- Sala and artist lists for dropdown population ---

    @GetMapping("/admin/salas-list")
    public ResponseEntity<List<SalaConciertoProxyDto>> listSalas() {
        return ResponseEntity.ok(proxyPort.listSalas());
    }

    @GetMapping("/admin/artists-list")
    public ResponseEntity<List<ArtistProxyDto>> listArtists() {
        return ResponseEntity.ok(proxyPort.listArtists());
    }

    // --- Concert delete ---

    @PostMapping("/admin/concerts/{id}/delete")
    public ResponseEntity<Void> deleteConcert(@PathVariable("id") String id) {
        proxyPort.deleteConcert(id);
        return ResponseEntity.status(303)
            .location(URI.create("/admin/concerts"))
            .build();
    }

    // --- Sala create ---

    @PostMapping("/admin/salas")
    public ResponseEntity<Object> createSala(
        @RequestParam("name") String name,
        @RequestParam(value = "address", required = false) String address,
        @RequestParam(value = "city", required = false) String city,
        @RequestParam(value = "province", required = false) String province,
        @RequestParam(value = "lat", required = false) String lat,
        @RequestParam(value = "lng", required = false) String lng,
        @RequestParam(value = "imageUrl", required = false) String imageUrl,
        @RequestParam(value = "description", required = false) String description
    ) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            if (address != null) body.put("address", address);
            if (city != null) body.put("city", city);
            if (province != null) body.put("province", province);
            if (lat != null && !lat.isBlank()) body.put("lat", Double.parseDouble(lat));
            if (lng != null && !lng.isBlank()) body.put("lng", Double.parseDouble(lng));
            if (imageUrl != null) body.put("image_url", imageUrl);
            if (description != null) body.put("description", description);

            SalaConciertoProxyDto result = proxyPort.createSala(body);
            return ResponseEntity.status(303)
                .location(URI.create("/admin/concerts?created=sala&id=" + result.id()))
                .build();
        } catch (ResponseStatusException ex) {
            return ResponseEntity.ok(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
        }
    }

    // --- Artist create ---

    @PostMapping("/admin/artists")
    public ResponseEntity<Object> createArtist(
        @RequestParam("name") String name,
        @RequestParam(value = "genre", required = false) String genre,
        @RequestParam(value = "imageUrl", required = false) String imageUrl,
        @RequestParam(value = "website", required = false) String website,
        @RequestParam(value = "description", required = false) String description
    ) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            if (genre != null) body.put("genre", genre);
            if (imageUrl != null) body.put("image_url", imageUrl);
            if (website != null) body.put("website", website);
            if (description != null) body.put("description", description);

            ArtistProxyDto result = proxyPort.createArtist(body);
            return ResponseEntity.status(303)
                .location(URI.create("/admin/concerts?created=artist&id=" + result.id()))
                .build();
        } catch (ResponseStatusException ex) {
            return ResponseEntity.ok(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
        }
    }

    // --- Concert create ---

    @PostMapping("/admin/concerts")
    public ResponseEntity<Object> createConcert(
        @RequestParam("salaConciertoId") String salaConciertoId,
        @RequestParam("artistIds") List<String> artistIds,
        @RequestParam("date") String date,
        @RequestParam(value = "time", required = false) String time,
        @RequestParam(value = "price", required = false) String price
    ) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("salaConciertoId", salaConciertoId);
            body.put("artistIds", artistIds);
            body.put("date", date);
            if (time != null) body.put("time", time);
            if (price != null) body.put("price", price);

            ConcertProxyDto result = proxyPort.createConcert(body);
            return ResponseEntity.status(303)
                .location(URI.create("/admin/concerts?created=concert&id=" + result.id()))
                .build();
        } catch (ResponseStatusException ex) {
            return ResponseEntity.ok(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
        }
    }

    // --- Pre-fill GET routes (edit forms) ---

    @GetMapping("/admin/salas/{id}")
    public ResponseEntity<SalaConciertoProxyDto> getSala(@PathVariable("id") String id) {
        return ResponseEntity.ok(proxyPort.getSala(id));
    }

    @GetMapping("/admin/artists/{id}")
    public ResponseEntity<ArtistProxyDto> getArtist(@PathVariable("id") String id) {
        return ResponseEntity.ok(proxyPort.getArtist(id));
    }

    @GetMapping("/admin/concerts/{id}")
    public ResponseEntity<ConcertProxyDto> getConcert(@PathVariable("id") String id) {
        return ResponseEntity.ok(proxyPort.getConcert(id));
    }

    // --- Including-blocked list GET routes (edit tabs) ---

    @GetMapping("/admin/salas-all")
    public ResponseEntity<List<SalaConciertoProxyDto>> listSalasIncludingBlocked() {
        return ResponseEntity.ok(proxyPort.listSalasIncludingBlocked());
    }

    @GetMapping("/admin/artists-all")
    public ResponseEntity<List<ArtistProxyDto>> listArtistsIncludingBlocked() {
        return ResponseEntity.ok(proxyPort.listArtistsIncludingBlocked());
    }

    @GetMapping("/admin/concerts-all")
    public ResponseEntity<List<ConcertProxyDto>> listConcertsIncludingBlocked() {
        return ResponseEntity.ok(proxyPort.listConcertsIncludingBlocked());
    }

    // --- Sala edit (PUT) ---

    @PutMapping("/admin/salas/{id}")
    public ResponseEntity<Object> updateSala(
        @PathVariable("id") String id,
        @RequestParam("name") String name,
        @RequestParam(value = "address", required = false) String address,
        @RequestParam(value = "city", required = false) String city,
        @RequestParam(value = "province", required = false) String province,
        @RequestParam(value = "lat", required = false) String lat,
        @RequestParam(value = "lng", required = false) String lng,
        @RequestParam(value = "imageUrl", required = false) String imageUrl,
        @RequestParam(value = "description", required = false) String description
    ) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            if (address != null) body.put("address", address);
            if (city != null) body.put("city", city);
            if (province != null) body.put("province", province);
            if (lat != null && !lat.isBlank()) body.put("lat", Double.parseDouble(lat));
            if (lng != null && !lng.isBlank()) body.put("lng", Double.parseDouble(lng));
            if (imageUrl != null) body.put("image_url", imageUrl);
            if (description != null) body.put("description", description);

            SalaConciertoProxyDto result = proxyPort.updateSala(id, body);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.ok(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
        }
    }

    // --- Artist edit (PUT) ---

    @PutMapping("/admin/artists/{id}")
    public ResponseEntity<Object> updateArtist(
        @PathVariable("id") String id,
        @RequestParam("name") String name,
        @RequestParam(value = "genre", required = false) String genre,
        @RequestParam(value = "imageUrl", required = false) String imageUrl,
        @RequestParam(value = "description", required = false) String description
    ) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("name", name);
            if (genre != null) body.put("genre", genre);
            if (imageUrl != null) body.put("image_url", imageUrl);
            if (description != null) body.put("description", description);

            ArtistProxyDto result = proxyPort.updateArtist(id, body);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.ok(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
        }
    }

    // --- Concert edit (PUT — date/time/price only) ---

    @PutMapping("/admin/concerts/{id}")
    public ResponseEntity<Object> updateConcert(
        @PathVariable("id") String id,
        @RequestParam("date") String date,
        @RequestParam(value = "time", required = false) String time,
        @RequestParam(value = "price", required = false) String price
    ) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("date", date);
            if (time != null) body.put("time", time);
            if (price != null) body.put("price", price);

            ConcertProxyDto result = proxyPort.updateConcert(id, body);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException ex) {
            return ResponseEntity.ok(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
        }
    }
}
