package com.rubenazo.buscaConciertos.scraper.application.usecases;

import com.rubenazo.buscaConciertos.scraper.application.parsers.ConcertListParser;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedConcert;
import com.rubenazo.buscaConciertos.scraper.domain.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes concert listings from the configured primary HTML source over a date window.
 *
 * The window is fetched in monthly chunks ({@code scraper.chunk-months}) to keep each request small:
 * for each chunk it builds the search URL, fetches HTML via {@link HtmlFetchPort} (Jsoup) and parses
 * it with {@link ConcertListParser}. Fetch failures are recorded as {@link Discrepancy} entries
 * rather than aborting the run, so a single bad chunk doesn't lose the rest.
 */
@Service
public class ConcertScraperUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConcertScraperUseCase.class);
    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HtmlFetchPort htmlFetchPort;
    private final ConcertListParser concertListParser;
    private final String baseUrl;
    private final int chunkMonths;

    public ConcertScraperUseCase(HtmlFetchPort htmlFetchPort,
                                  ConcertListParser concertListParser,
                                  @Value("${scraper.base-url}") String baseUrl,
                                  @Value("${scraper.chunk-months:1}") int chunkMonths) {
        this.htmlFetchPort = htmlFetchPort;
        this.concertListParser = concertListParser;
        this.baseUrl = baseUrl;
        this.chunkMonths = chunkMonths;
    }

    public List<ScrapedConcert> scrape(LocalDate start, LocalDate end, List<Discrepancy> discrepancies) {
        List<ScrapedConcert> concerts = new ArrayList<>();

        LocalDate chunkStart = start;
        while (chunkStart.isBefore(end)) {
            LocalDate chunkEnd = chunkStart.plusMonths(chunkMonths);
            if (chunkEnd.isAfter(end)) {
                chunkEnd = end;
            }

            String url = buildSearchUrl(chunkStart, chunkEnd);
            log.info("Fetching concert chunk: {} → {}", chunkStart, chunkEnd);
            try {
                String html = htmlFetchPort.fetch(url);
                List<ScrapedConcert> chunk = concertListParser.parse(html, discrepancies);
                log.info("  Parsed {} concerts for chunk {} → {}", chunk.size(), chunkStart, chunkEnd);
                concerts.addAll(chunk);
            } catch (HtmlFetchException e) {
                discrepancies.add(new Discrepancy(
                    DiscrepancyType.FETCH_ERROR, Severity.ERROR,
                    "concert-list", chunkStart + "_" + chunkEnd, "url",
                    "2xx", String.valueOf(e.getStatusCode()),
                    url, Instant.now()
                ));
            }

            chunkStart = chunkEnd;
        }

        return concerts;
    }

    private String buildSearchUrl(LocalDate start, LocalDate end) {
        String fecha1 = URLEncoder.encode(start.format(URL_DATE_FORMAT), StandardCharsets.UTF_8);
        String fecha2 = URLEncoder.encode(end.format(URL_DATE_FORMAT), StandardCharsets.UTF_8);
        return baseUrl + "/search.php?fecha1=" + fecha1 + "&fecha2=" + fecha2;
    }
}
