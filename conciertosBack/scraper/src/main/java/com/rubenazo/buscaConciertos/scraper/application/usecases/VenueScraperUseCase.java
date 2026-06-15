package com.rubenazo.buscaConciertos.scraper.application.usecases;

import com.rubenazo.buscaConciertos.scraper.application.parsers.VenueDetailParser;
import com.rubenazo.buscaConciertos.scraper.application.parsers.VenueListParser;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue;
import com.rubenazo.buscaConciertos.scraper.domain.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes venue (sala) details from the primary HTML source. Two entry points: {@link #scrape} walks
 * the venue listings for given provinces, while {@link #scrapeByUrls} fetches specific venue pages —
 * the path the sync uses to resolve only the venues referenced by newly found concerts.
 *
 * Detail pages are parsed by {@link VenueDetailParser} (address, coordinates, etc.); fetch failures
 * become {@link Discrepancy} entries so one unreachable venue doesn't fail the batch.
 */
@Service
public class VenueScraperUseCase {

    private static final Logger log = LoggerFactory.getLogger(VenueScraperUseCase.class);

    private final HtmlFetchPort htmlFetchPort;
    private final VenueListParser venueListParser;
    private final VenueDetailParser venueDetailParser;
    private final String baseUrl;

    public VenueScraperUseCase(HtmlFetchPort htmlFetchPort,
                                VenueListParser venueListParser,
                                VenueDetailParser venueDetailParser,
                                @Value("${scraper.base-url}") String baseUrl) {
        this.htmlFetchPort = htmlFetchPort;
        this.venueListParser = venueListParser;
        this.venueDetailParser = venueDetailParser;
        this.baseUrl = baseUrl;
    }

    public List<ScrapedVenue> scrape(List<String> provinces, List<Discrepancy> discrepancies) {
        List<ScrapedVenue> venues = new ArrayList<>();

        for (String province : provinces) {
            String listUrl = baseUrl + "/" + province + "/locales";
            String listHtml;
            try {
                listHtml = htmlFetchPort.fetch(listUrl);
            } catch (HtmlFetchException e) {
                discrepancies.add(new Discrepancy(
                    DiscrepancyType.FETCH_ERROR, Severity.ERROR,
                    "venue-list", province, "url",
                    "2xx", String.valueOf(e.getStatusCode()),
                    listUrl, Instant.now()
                ));
                continue;
            }

            List<String> venueUrls = venueListParser.parse(listHtml, province, discrepancies);

            for (String relativeUrl : venueUrls) {
                String detailUrl = baseUrl + relativeUrl;
                String slug = relativeUrl.substring(relativeUrl.lastIndexOf('/') + 1);

                String detailHtml;
                try {
                    detailHtml = htmlFetchPort.fetch(detailUrl);
                } catch (HtmlFetchException e) {
                    discrepancies.add(new Discrepancy(
                        DiscrepancyType.FETCH_ERROR, Severity.ERROR,
                        "venue", province + "-" + slug, "url",
                        "2xx", String.valueOf(e.getStatusCode()),
                        detailUrl, Instant.now()
                    ));
                    continue;
                }

                venueDetailParser.parse(detailHtml, province, slug, detailUrl, discrepancies)
                    .ifPresent(venues::add);
            }
        }

        return venues;
    }

    public List<ScrapedVenue> scrapeByUrls(List<String> venueUrls, List<Discrepancy> discrepancies) {
        List<ScrapedVenue> venues = new ArrayList<>();

        for (String url : venueUrls) {
            boolean absolute = url.startsWith("http://") || url.startsWith("https://");
            String detailUrl = absolute ? url : baseUrl + url;

            String pathPart = absolute ? url.replaceFirst("https?://[^/]+", "") : url;
            String[] parts = pathPart.split("/");
            if (parts.length < 4) continue;
            String province = parts[1];
            String slug = parts[3];

            log.info("Fetching venue detail: {}", detailUrl);

            String detailHtml;
            try {
                detailHtml = htmlFetchPort.fetch(detailUrl);
            } catch (HtmlFetchException e) {
                discrepancies.add(new Discrepancy(
                    DiscrepancyType.FETCH_ERROR, Severity.ERROR,
                    "venue", province + "-" + slug, "url",
                    "2xx", String.valueOf(e.getStatusCode()),
                    detailUrl, Instant.now()
                ));
                continue;
            }

            venueDetailParser.parse(detailHtml, province, slug, detailUrl, discrepancies)
                .ifPresent(venues::add);
        }

        return venues;
    }
}
