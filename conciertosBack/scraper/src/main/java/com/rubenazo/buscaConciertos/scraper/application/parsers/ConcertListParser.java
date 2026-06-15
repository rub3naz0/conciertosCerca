package com.rubenazo.buscaConciertos.scraper.application.parsers;

import com.rubenazo.buscaConciertos.scraper.application.SlugUtils;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedConcert;
import com.rubenazo.buscaConciertos.scraper.domain.Severity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConcertListParser {

    private static final Logger log = LoggerFactory.getLogger(ConcertListParser.class);

    // Date format after stripping day-of-week letter: d/M/yy
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d/M/yy");

    // Extracts numeric ID from /{prov}/conciertos/{ID}-{slug}/{timestamp}
    private static final Pattern CONCERT_ID_PATTERN = Pattern.compile("/conciertos/(\\d+)-");

    public List<ScrapedConcert> parse(String html, List<Discrepancy> discrepancies) {
        Document doc = Jsoup.parse(html);

        Elements container = doc.select("section.conciertos ul.list");
        if (container.isEmpty()) {
            discrepancies.add(new Discrepancy(
                DiscrepancyType.SCHEMA_CHANGE,
                Severity.WARNING,
                "concert-list",
                "search.php",
                "section.conciertos ul.list",
                "present",
                "missing",
                doc.outerHtml().substring(0, Math.min(300, doc.outerHtml().length())),
                Instant.now()
            ));
            return List.of();
        }

        Elements items = doc.select("section.conciertos ul.list > li");
        List<ScrapedConcert> concerts = new ArrayList<>();

        for (Element item : items) {
            // ID from a.nombre href
            Element nombreLink = item.selectFirst("a.nombre");
            if (nombreLink == null) continue;

            String href = nombreLink.attr("href");
            String id = extractConcertId(href);
            if (id == null) continue;

            // Artist name
            String artistName = nombreLink.text().strip();
            if (artistName.isBlank()) continue;

            // Date and time from div.time
            Element timeDiv = item.selectFirst("div.time");
            LocalDate date = null;
            String time = null;
            if (timeDiv != null) {
                String timeHtml = timeDiv.html();
                // Split on <br> — first part is date, second is time
                String[] timeParts = timeHtml.split("(?i)<br\\s*/?>\\s*");
                if (timeParts.length >= 1) {
                    String rawDate = Jsoup.parse(timeParts[0]).text().strip();
                    // Strip the leading day-of-week character (L, M, X, J, V, S, D)
                    if (rawDate.length() > 1) {
                        String dateStr = rawDate.substring(1);
                        try {
                            date = LocalDate.parse(dateStr, DATE_FORMAT);
                        } catch (Exception e) {
                            // skip malformed dates
                        }
                    }
                }
                if (timeParts.length >= 2) {
                    time = Jsoup.parse(timeParts[1]).text().strip();
                    if (time.isBlank()) time = null;
                }
            }

            // Genre from span.estilo — strip leading " / "
            String genre = null;
            Element estiloEl = item.selectFirst("span.estilo");
            if (estiloEl != null) {
                String estiloText = estiloEl.text().strip();
                if (estiloText.startsWith("/")) {
                    estiloText = estiloText.substring(1).strip();
                }
                if (!estiloText.isBlank()) genre = estiloText;
            }

            // Image URL: div.img img — prefer data-src
            String imageUrl = null;
            Element imgEl = item.selectFirst("div.img img");
            if (imgEl != null) {
                String dataSrc = imgEl.attr("data-src");
                if (!dataSrc.isBlank()) {
                    imageUrl = dataSrc;
                } else {
                    String src = imgEl.attr("src");
                    if (!src.isBlank() && !src.equals("/img/nofoto.jpg")) {
                        imageUrl = src;
                    }
                }
            }

            // Venue name and province from a.local
            String venueName = null;
            String venueProvince = null;
            Element localLink = item.selectFirst("a.local");
            if (localLink != null) {
                // Remove icon text — the text() method includes icon text, use own text nodes
                // The format is "Teatro Lope de Vega. Madrid" but may include icon text
                // Use the title attribute which has "Venue Name, City (Province)"
                String title = localLink.attr("title");
                if (!title.isBlank()) {
                    // title format: "Teatro Lope de Vega, Madrid (Madrid)"
                    int parenIdx = title.lastIndexOf('(');
                    if (parenIdx > 0) {
                        venueProvince = title.substring(parenIdx + 1, title.length() - 1).strip();
                    }
                    // venue name is before the first comma
                    int commaIdx = title.indexOf(',');
                    if (commaIdx > 0) {
                        venueName = title.substring(0, commaIdx).strip();
                    } else {
                        venueName = (parenIdx > 0 ? title.substring(0, parenIdx) : title).strip();
                    }
                } else {
                    // Fallback: use text content, strip the icon SVG text
                    String localText = localLink.text().strip();
                    if (!localText.isBlank()) venueName = localText;
                }
            }

            // Venue href from a.local
            String venueHref = null;
            if (localLink != null) {
                String vh = localLink.attr("href");
                if (!vh.isBlank()) venueHref = vh;
            }

            // Price from span.precio — keep as String
            String price = null;
            Element precioEl = item.selectFirst("span.precio");
            if (precioEl != null) {
                String precioText = precioEl.text().strip();
                if (!precioText.isBlank()) price = precioText;
            }

            // Source URL (relative)
            String sourceUrl = href.isBlank() ? null : href;

            // Skip items whose date could not be parsed — a null date would cause NPE downstream
            if (date == null) {
                log.warn("Skipping concert id={} — unparseable date in listing row", id);
                continue;
            }

            // Artist slug derived from name
            String artistSlug = SlugUtils.slugify(artistName);
            List<String> artistSlugs = artistSlug.isBlank() ? List.of() : List.of(artistSlug);

            concerts.add(new ScrapedConcert(
                id, null, artistSlugs, date, time, price, sourceUrl,
                venueName, venueProvince, artistName, genre, imageUrl, venueHref
            ));
        }

        return concerts;
    }

    private String extractConcertId(String href) {
        if (href == null || href.isBlank()) return null;
        Matcher m = CONCERT_ID_PATTERN.matcher(href);
        return m.find() ? m.group(1) : null;
    }
}
