package com.rubenazo.buscaConciertos.scraper.application.usecases;

import com.rubenazo.buscaConciertos.scraper.application.SlugUtils;
import com.rubenazo.buscaConciertos.scraper.application.parsers.ArtistDetailParser;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.domain.ArtistDetail;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedArtist;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedConcert;
import com.rubenazo.buscaConciertos.scraper.domain.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArtistEnrichmentUseCase {

    private final HtmlFetchPort htmlFetchPort;
    private final ArtistDetailParser artistDetailParser;
    private final String baseUrl;

    public ArtistEnrichmentUseCase(HtmlFetchPort htmlFetchPort,
                                    ArtistDetailParser artistDetailParser,
                                    @Value("${scraper.base-url}") String baseUrl) {
        this.htmlFetchPort = htmlFetchPort;
        this.artistDetailParser = artistDetailParser;
        this.baseUrl = baseUrl;
    }

    /**
     * Enriches artists by fetching concert detail pages for each unique artist name.
     * The description is extracted from the concert detail page.
     *
     * @param concerts       all scraped concerts (artist name, imageUrl, genre, sourceUrl come from here)
     * @param existingIds    artist slugs already known — will not be re-fetched
     * @param discrepancies  accumulator for any anomalies found
     * @return list of newly fetched ScrapedArtist records
     */
    public List<ScrapedArtist> enrich(List<ScrapedConcert> concerts, Set<String> existingIds,
                                      List<Discrepancy> discrepancies) {
        // Deduplicate by artist name — keep first occurrence per name
        Map<String, ScrapedConcert> byArtistName = new LinkedHashMap<>();
        for (ScrapedConcert concert : concerts) {
            if (concert.artistName() != null && !concert.artistName().isBlank()) {
                byArtistName.putIfAbsent(concert.artistName(), concert);
            }
        }

        List<ScrapedArtist> artists = new ArrayList<>();

        for (Map.Entry<String, ScrapedConcert> entry : byArtistName.entrySet()) {
            String artistName = entry.getKey();
            ScrapedConcert concert = entry.getValue();
            String artistSlug = SlugUtils.slugify(artistName);

            if (existingIds.contains(artistSlug)) {
                continue;
            }

            String sourceUrl = concert.sourceUrl();
            if (sourceUrl == null || sourceUrl.isBlank()) {
                continue;
            }

            String url = sourceUrl.startsWith("http") ? sourceUrl : baseUrl + sourceUrl;
            String description = null;
            String imageUrl = concert.imageUrl();
            try {
                String html = htmlFetchPort.fetch(url);
                ArtistDetail detail = artistDetailParser.parse(html, discrepancies);
                description = detail.description();
                if (detail.imageUrl() != null) {
                    imageUrl = detail.imageUrl();
                }
            } catch (HtmlFetchException e) {
                discrepancies.add(new Discrepancy(
                    DiscrepancyType.ARTIST_NOT_FOUND, Severity.WARNING,
                    "artist", artistSlug, "url",
                    "2xx", String.valueOf(e.getStatusCode()),
                    url, Instant.now()
                ));
            }

            artists.add(new ScrapedArtist(
                artistSlug, artistName, concert.genre(), imageUrl,
                null, url, description
            ));
        }

        return artists;
    }
}
