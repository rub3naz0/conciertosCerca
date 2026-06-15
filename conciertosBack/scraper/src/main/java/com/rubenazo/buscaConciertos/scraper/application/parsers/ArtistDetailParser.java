package com.rubenazo.buscaConciertos.scraper.application.parsers;

import com.rubenazo.buscaConciertos.scraper.domain.ArtistDetail;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ArtistDetailParser {

    private static final String BASE_URL = "https://conciertos.club";

    /**
     * Parses a concert detail page and returns an {@link ArtistDetail} containing
     * the description and the image URL extracted from {@code img[itemprop="image"]}.
     *
     * @param html          concert detail page HTML
     * @param discrepancies accumulator (reserved for future schema checks)
     * @return ArtistDetail with nullable description and imageUrl
     */
    public ArtistDetail parse(String html, List<Discrepancy> discrepancies) {
        Document doc = Jsoup.parse(html);

        // Image: img[itemprop="image"] src (prefix with base URL if relative)
        String imageUrl = null;
        Element imgEl = doc.selectFirst("img[itemprop=image]");
        if (imgEl != null) {
            String src = imgEl.attr("src");
            if (!src.isBlank()) {
                imageUrl = src.startsWith("http") ? src : BASE_URL + src;
            }
        }

        // Description: walk siblings after h3 inside div.texto
        String description = null;
        Element h3 = doc.selectFirst("div.texto h3");
        if (h3 != null) {
            List<String> parts = new ArrayList<>();
            Node current = h3.nextSibling();
            while (current != null) {
                if (current instanceof Comment comment) {
                    if (comment.getData().trim().contains("itemprop=\"description\"")) {
                        break;
                    }
                } else if (current instanceof Element el) {
                    String tag = el.tagName().toLowerCase();
                    // YouTube embeds (div.iframe_youtube) are intentionally skipped:
                    // the front renders the description as plain text and cannot play
                    // the video, so the raw embed URL would only be visible noise.
                    if ("p".equals(tag)) {
                        String text = el.text().strip();
                        if (!text.isBlank()) {
                            parts.add(text);
                        }
                    }
                }
                current = current.nextSibling();
            }
            if (!parts.isEmpty()) {
                description = String.join("\n\n", parts);
            }
        }

        return new ArtistDetail(description, imageUrl);
    }

    /**
     * Backwards-compatible wrapper — delegates to {@link #parse(String, List)}.
     *
     * @param html           concert detail page HTML
     * @param discrepancies  accumulator
     * @return Optional description string, empty if no relevant content is found
     */
    public Optional<String> parseDescription(String html, List<Discrepancy> discrepancies) {
        ArtistDetail detail = parse(html, discrepancies);
        return Optional.ofNullable(detail.description());
    }
}
