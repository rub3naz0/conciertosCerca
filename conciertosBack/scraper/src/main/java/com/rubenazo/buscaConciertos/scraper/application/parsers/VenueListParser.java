package com.rubenazo.buscaConciertos.scraper.application.parsers;

import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.Severity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class VenueListParser {

    public List<String> parse(String html, String province, List<Discrepancy> discrepancies) {
        Document doc = Jsoup.parse(html);

        Elements container = doc.select("ul.list.list-block");
        if (container.isEmpty()) {
            discrepancies.add(new Discrepancy(
                DiscrepancyType.SCHEMA_CHANGE,
                Severity.WARNING,
                "venue-list",
                province,
                "ul.list.list-block",
                "present",
                "missing",
                doc.outerHtml().substring(0, Math.min(300, doc.outerHtml().length())),
                Instant.now()
            ));
            return List.of();
        }

        Elements links = doc.select("ul.list.list-block li a");
        return links.stream()
            .map(el -> el.attr("href"))
            .filter(href -> !href.isBlank() && href.contains("/locales/"))
            .toList();
    }
}
