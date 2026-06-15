package com.rubenazo.buscaConciertos.adapters.out.alcala;

import com.rubenazo.buscaConciertos.adapters.out.alcala.dto.AemBandDto;
import com.rubenazo.buscaConciertos.adapters.out.alcala.dto.AemEventDto;
import com.rubenazo.buscaConciertos.adapters.out.alcala.dto.AemPageDto;
import com.rubenazo.buscaConciertos.adapters.out.alcala.dto.AemVenueDto;
import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSnapshot;
import com.rubenazo.buscaConciertos.application.ports.out.AlcalaSourcePort;
import com.rubenazo.buscaConciertos.domain.Artist;
import com.rubenazo.buscaConciertos.domain.Concert;
import com.rubenazo.buscaConciertos.domain.SalaConcierto;
import com.rubenazo.buscaConciertos.scraper.application.SlugUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class AlcalaApiClientAdapter implements AlcalaSourcePort {

    private static final Logger log = LoggerFactory.getLogger(AlcalaApiClientAdapter.class);
    static final int MAX_PAGES = 100;

    private final RestClient restClient;

    AlcalaApiClientAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AlcalaSnapshot fetch(LocalDate from, LocalDate to) {
        // Keyed maps to deduplicate venues and artists harvested across all pages
        Map<String, SalaConcierto> venueMap = new LinkedHashMap<>();
        Map<String, Artist> artistMap = new LinkedHashMap<>();
        List<Concert> concerts = new ArrayList<>();

        String nextUrl = "/api/v1/upcoming_events/";
        int pageCount = 0;

        while (nextUrl != null && !nextUrl.isBlank() && pageCount < MAX_PAGES) {
            final String url = nextUrl;
            AemPageDto page = restClient.get()
                .uri(url)
                .retrieve()
                .body(AemPageDto.class);

            pageCount++;

            if (page == null) {
                break;
            }

            if (page.events() != null) {
                for (AemEventDto event : page.events()) {
                    processEvent(event, venueMap, artistMap, concerts);
                }
            }

            nextUrl = extractNextPath(page);
        }

        if (pageCount >= MAX_PAGES && nextUrl != null && !nextUrl.isBlank()) {
            log.warn("AlcalaApiClientAdapter reached MAX_PAGES={} cap, stopping pagination", MAX_PAGES);
        }

        enrichVenuesFromUpcomingVenuesEndpoint(venueMap);
        enrichArtistsFromBandsEndpoint(artistMap);

        return new AlcalaSnapshot(
            new ArrayList<>(venueMap.values()),
            new ArrayList<>(artistMap.values()),
            concerts
        );
    }

    private void processEvent(
        AemEventDto event,
        Map<String, SalaConcierto> venueMap,
        Map<String, Artist> artistMap,
        List<Concert> concerts
    ) {
        if (event == null) return;

        Instant now = Instant.now();

        // Harvest venue
        String venueKey = null;
        if (event.venues() != null) {
            venueKey = "aem-venue-" + event.venues().id();
            if (!venueMap.containsKey(venueKey)) {
                venueMap.put(venueKey, mapVenue(event.venues(), venueKey, now));
            }
        }

        // Harvest artists — D11 resolution order:
        // 1. bands[] non-empty → use band ids (aem-band-{integer})
        // 2. bands[] empty AND title non-blank → derive one artist from title (aem-artist-{slug})
        // 3. both empty/blank → artistIds stays empty
        List<String> artistIds = new ArrayList<>();
        if (event.bands() != null && !event.bands().isEmpty()) {
            // Path 1: bands present
            for (AemBandDto band : event.bands()) {
                String artistId = "aem-band-" + band.id();
                if (!artistMap.containsKey(artistId)) {
                    artistMap.put(artistId, mapArtist(band, artistId, now));
                }
                artistIds.add(artistId);
            }
        } else if (event.title() != null && !event.title().isBlank()) {
            // Path 2: no bands, but title is non-blank — derive one artist from event title
            String titleSlug = SlugUtils.slugify(event.title().trim());
            String artistId = "aem-artist-" + titleSlug;
            if (!artistMap.containsKey(artistId)) {
                artistMap.put(artistId, new Artist(artistId, event.title().trim(), null, null, null, null, null, now));
            }
            artistIds.add(artistId);
        }
        // Path 3: bands empty and title blank — artistIds stays empty (no-op)

        // Map concert
        String concertId = "aem-event-" + event.eventUid();
        String price = event.price() != null ? event.price() : event.pricePreorder();
        LocalDate date;
        if (event.day() == null) {
            date = null;
        } else {
            try {
                date = LocalDate.parse(event.day());
            } catch (DateTimeParseException e) {
                log.warn("Skipping event uid={} — malformed day '{}': {}", event.eventUid(), event.day(), e.getMessage());
                return;
            }
        }

        concerts.add(new Concert(
            concertId,
            venueKey,
            artistIds,
            date,
            event.time(),
            price,
            event.ticketLink(),
            now
        ));
    }

    private SalaConcierto mapVenue(AemVenueDto dto, String venueId, Instant now) {
        Double lat = parseDouble(dto.latitude());
        Double lng = parseDouble(dto.longitude());
        return new SalaConcierto(
            venueId,
            dto.name(),
            dto.address(),
            null,       // city — filled by use case via reverse geocoding (D9)
            null,       // province — filled by use case via reverse geocoding (D9)
            lat,
            lng,
            firstNonBlank(dto.image(), dto.profileImage()),
            dto.description(),
            null,       // sourceUrl
            now
        );
    }

    private Artist mapArtist(AemBandDto dto, String artistId, Instant now) {
        return new Artist(
            artistId,
            dto.name(),
            dto.genre(),
            firstNonBlank(dto.bandImage(), dto.profileImage()),
            dto.webpageLink(),
            null,
            dto.description(),
            now
        );
    }

    private void enrichVenuesFromUpcomingVenuesEndpoint(Map<String, SalaConcierto> venueMap) {
        if (venueMap.isEmpty()) return;
        AemVenueDto[] venues = restClient.get()
            .uri("/api/v1/upcoming_venues/")
            .retrieve()
            .body(AemVenueDto[].class);
        if (venues == null) return;
        Instant now = Instant.now();
        for (AemVenueDto venue : venues) {
            if (venue == null || venue.id() == null) continue;
            String venueId = "aem-venue-" + venue.id();
            if (venueMap.containsKey(venueId)) {
                venueMap.put(venueId, mapVenue(venue, venueId, now));
            }
        }
    }

    private void enrichArtistsFromBandsEndpoint(Map<String, Artist> artistMap) {
        if (artistMap.isEmpty()) return;
        AemBandDto[] bands = restClient.get()
            .uri("/api/v1/bands/")
            .retrieve()
            .body(AemBandDto[].class);
        if (bands == null) return;
        Instant now = Instant.now();
        for (AemBandDto band : bands) {
            if (band == null || band.id() == null) continue;
            // Keys from the /bands/ endpoint are always "aem-band-{integer}".
            // Title-derived artists use "aem-artist-{slug}" keys and will never
            // appear in artistMap under an "aem-band-*" key — the containsKey
            // guard below naturally skips them with no special casing needed.
            String artistId = "aem-band-" + band.id();
            if (artistMap.containsKey(artistId)) {
                artistMap.put(artistId, mapArtist(band, artistId, now));
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts the path+query from the absolute next URL returned by meta.next.
     * Returns null/empty when there is no next page.
     */
    private String extractNextPath(AemPageDto page) {
        if (page.meta() == null) return null;
        String next = page.meta().next();
        if (next == null || next.isBlank()) return null;

        // meta.next is an absolute URL like http://host/api/v1/upcoming_events/?page=2
        // Extract the path+query portion so RestClient can append it to its base URL
        try {
            java.net.URI uri = java.net.URI.create(next);
            String pathAndQuery = uri.getRawPath();
            if (uri.getRawQuery() != null) {
                pathAndQuery = pathAndQuery + "?" + uri.getRawQuery();
            }
            return pathAndQuery;
        } catch (IllegalArgumentException e) {
            log.warn("Could not parse meta.next URL '{}': {}", next, e.getMessage());
            return null;
        }
    }
}
