package com.rubenazo.buscaConciertos.scraper.application.parsers;

import com.rubenazo.buscaConciertos.domain.ScrapedCoordinateValidator;
import com.rubenazo.buscaConciertos.scraper.application.SlugUtils;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.ScrapedVenue;
import com.rubenazo.buscaConciertos.scraper.domain.Severity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VenueDetailParser {

    private static final String BASE_URL = "https://conciertos.club";

    // Matches q=lat,lng (with optional whitespace after the comma) inside a Google Maps
    // embed iframe src, e.g. "https://maps.google.com/maps?q=41.3735, 2.1700&t=&z=17...".
    private static final Pattern IFRAME_COORDS_PATTERN =
        Pattern.compile("q=(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)");

    public Optional<ScrapedVenue> parse(String html, String provinceSlug, String venueSlug,
                                        String sourceUrl, List<Discrepancy> discrepancies) {
        Document doc = Jsoup.parse(html);

        // Name: article h2
        Element nameEl = doc.selectFirst("article h2");
        if (nameEl == null || nameEl.text().isBlank()) {
            discrepancies.add(new Discrepancy(
                DiscrepancyType.PARSE_ERROR,
                Severity.ERROR,
                "venue",
                provinceSlug + "-" + venueSlug,
                "name",
                "non-empty",
                "missing",
                doc.outerHtml().substring(0, Math.min(500, doc.outerHtml().length())),
                Instant.now()
            ));
            return Optional.empty();
        }
        String name = nameEl.text().strip();

        // Image: div.fotog_movil img src (prefix with base URL if relative)
        String imageUrl = null;
        Element imgEl = doc.selectFirst("div.fotog_movil img");
        if (imgEl != null) {
            String src = imgEl.attr("src");
            if (!src.isBlank()) {
                imageUrl = src.startsWith("http") ? src : BASE_URL + src;
            }
        }

        // City and province: <p> containing span.icon_place
        // The text after the SVG span contains: City<br/>Province<br/>
        String city = null;
        String province = null;
        Element placeParagraph = findParagraphWithClass(doc, "icon_place");
        if (placeParagraph != null) {
            String[] lines = splitByBr(placeParagraph);
            if (lines.length >= 1) city = lines[0].strip();
            if (lines.length >= 2) province = lines[1].strip();
        }

        // Address: <p> containing strong with "Dirección:"
        String address = null;
        Element addressParagraph = findParagraphWithDireccion(doc);
        if (addressParagraph != null) {
            address = extractAddressText(addressParagraph);
        }

        // Description: div.texto text content
        String description = null;
        Element textoEl = doc.selectFirst("div.texto");
        if (textoEl != null) {
            String text = textoEl.text().strip();
            if (!text.isBlank()) {
                description = text;
            }
        }

        // Coordinates: extracted from the venue page's Google Maps iframe q=lat,lng,
        // validated against ScrapedCoordinateValidator (finite + global range + Spain
        // bounding box). If the iframe is absent, unmatched, or the pair fails
        // validation, lat/lng stay null (no regression for iframe-less pages).
        Double[] coords = extractIframeCoordinates(doc);
        Double lat = coords[0];
        Double lng = coords[1];

        String id = SlugUtils.venueId(
            province != null ? province : provinceSlug,
            name
        );

        return Optional.of(new ScrapedVenue(id, name, address, city, province,
            lat, lng, imageUrl, description, sourceUrl));
    }

    /**
     * Extracts and validates {@code lat}/{@code lng} from the venue page's Google Maps
     * iframe {@code src} attribute (pattern {@code q=lat,lng}).
     *
     * <p>Selector: {@code div.map iframe} first; falls back to
     * {@code iframe[src*=maps.google]} if the page markup nests the iframe outside the
     * expected map container. The Stay22 widget {@code <script>} block is never
     * considered — it is not an {@code <iframe>}.
     *
     * @return a 2-element array {@code [lat, lng]}, both {@code null} if no iframe is
     *         found, the {@code src} doesn't match the pattern, or the matched pair
     *         fails {@link ScrapedCoordinateValidator#isValid(double, double)}.
     */
    private Double[] extractIframeCoordinates(Document doc) {
        Element iframe = doc.selectFirst("div.map iframe");
        if (iframe == null) {
            iframe = doc.selectFirst("iframe[src*=maps.google]");
        }
        if (iframe == null) {
            return new Double[]{null, null};
        }

        // Jsoup returns the decoded attribute value (plain '&', not '&amp;').
        String src = iframe.attr("src");
        Matcher matcher = IFRAME_COORDS_PATTERN.matcher(src);
        if (!matcher.find()) {
            return new Double[]{null, null};
        }

        try {
            double lat = Double.parseDouble(matcher.group(1));
            double lng = Double.parseDouble(matcher.group(2));
            if (ScrapedCoordinateValidator.isValid(lat, lng)) {
                return new Double[]{lat, lng};
            }
        } catch (NumberFormatException ignored) {
            // fall through to null
        }
        return new Double[]{null, null};
    }

    /**
     * Finds a &lt;p&gt; element that contains a span with the given icon class.
     */
    private Element findParagraphWithClass(Document doc, String iconClass) {
        Elements paragraphs = doc.select("p");
        for (Element p : paragraphs) {
            if (p.selectFirst("span." + iconClass) != null) {
                return p;
            }
        }
        return null;
    }

    /**
     * Finds a &lt;p&gt; element whose strong child text contains "Dirección:".
     */
    private Element findParagraphWithDireccion(Document doc) {
        Elements paragraphs = doc.select("p");
        for (Element p : paragraphs) {
            Element strong = p.selectFirst("strong");
            if (strong != null && strong.text().contains("Dirección")) {
                return p;
            }
        }
        return null;
    }

    /**
     * Splits paragraph inner HTML by &lt;br&gt; and returns non-blank, non-tag text lines.
     * Strips any child elements (e.g. SVG spans) so we get only text content.
     */
    private String[] splitByBr(Element p) {
        String inner = p.html();
        // Split on <br> variants
        String[] rawParts = inner.split("(?i)<br\\s*/?>\\s*");
        return java.util.Arrays.stream(rawParts)
            .map(part -> Jsoup.parse(part).text().strip())
            .filter(s -> !s.isBlank())
            .toArray(String[]::new);
    }

    /**
     * Extracts address text from the Dirección paragraph.
     * Skips the strong element and any icon spans; joins remaining text lines.
     */
    private String extractAddressText(Element p) {
        String inner = p.html();
        // Remove the strong element (Dirección: label)
        String withoutStrong = inner.replaceAll("(?s)<strong>[^<]*</strong>", "");
        // Split by <br>
        String[] parts = withoutStrong.split("(?i)<br\\s*/?>\\s*");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String text = Jsoup.parse(part).text().strip();
            if (!text.isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(text);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

}
