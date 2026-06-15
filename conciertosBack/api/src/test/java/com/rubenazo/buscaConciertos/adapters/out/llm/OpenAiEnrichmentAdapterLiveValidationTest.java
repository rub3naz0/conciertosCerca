package com.rubenazo.buscaConciertos.adapters.out.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;
import com.rubenazo.buscaConciertos.domain.EnrichedFieldValue;
import com.rubenazo.buscaConciertos.domain.EntityEnrichmentRequest;
import com.rubenazo.buscaConciertos.domain.TavilyResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live validation harness for the enrichment system prompt. Hits the REAL OpenAI API
 * with hand-crafted evidence so prompt rules (locality coherence, anti-promo-blob) can
 * be exercised deterministically — independent of Tavily and impact ordering.
 *
 * Does NOT run in normal builds: gated on -Dllm.live=true AND a resolvable api-key.
 * Run with:
 *   ./gradlew :api:test --tests "*LiveValidationTest" -Dllm.live=true
 */
class OpenAiEnrichmentAdapterLiveValidationTest {

    private static OpenAiEnrichmentAdapter adapter;

    @BeforeAll
    static void setUp() {
        assumeTrue("true".equals(System.getProperty("llm.live")),
            "live LLM validation disabled (pass -Dllm.live=true to run)");
        String apiKey = resolveApiKey();
        assumeTrue(apiKey != null && !apiKey.isBlank(),
            "no app.llm.api-key resolvable from config/application.properties");

        OpenAIClient client = OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .timeout(Duration.ofMillis(30000))
            .maxRetries(2)
            .build();
        adapter = new OpenAiEnrichmentAdapter(client, "gpt-4o-mini");
    }

    // ── A: sala address — decoy from a DIFFERENT locality must be rejected ──────
    @Test
    void salaAddress_decoyFromWrongLocality_isRejected() {
        EntityEnrichmentRequest req = new EntityEnrichmentRequest(
            "sala", "sala-decoy", "Palacio de los Córdova", "Granada", "Granada",
            Map.of("name", "Palacio de los Córdova", "city", "Granada", "province", "Granada"),
            List.of("address"),
            Map.of(),
            Map.of("address", List.of(
                new TavilyResult(
                    "Palacio de los Córdova - eventos",
                    "https://example.com/madrid",
                    "El Palacio de los Córdova acoge un evento. Dirección: Calle de Alcalá 50, 28014 Madrid, Comunidad de Madrid.",
                    0.88)
            ))
        );

        EnrichedFieldValue address = adapter.enrich(req).fields().get("address");
        print("A decoy-wrong-locality", "address", address);

        // Coherence rule: a Madrid address cannot belong to a Granada venue → null.
        assertThat(address.value()).isNull();
    }

    // ── B: sala address — correct locality must be filled ───────────────────────
    @Test
    void salaAddress_correctLocality_isFilled() {
        EntityEnrichmentRequest req = new EntityEnrichmentRequest(
            "sala", "sala-ok", "Palacio de los Córdova", "Granada", "Granada",
            Map.of("name", "Palacio de los Córdova", "city", "Granada", "province", "Granada"),
            List.of("address"),
            Map.of(),
            Map.of("address", List.of(
                new TavilyResult(
                    "Palacio de los Córdova - cómo llegar",
                    "https://example.com/granada",
                    "El Palacio de los Córdova está en la Cuesta del Chapiz, 4, 18010 Granada.",
                    0.92)
            ))
        );

        EnrichedFieldValue address = adapter.enrich(req).fields().get("address");
        print("B correct-locality", "address", address);

        assertThat(address.value()).isNotNull();
        assertThat(address.value().toLowerCase()).contains("granada");
        assertThat(address.confidence()).isGreaterThanOrEqualTo(0.5);
    }

    // ── C: artist genre — clean genre, not a fragment ───────────────────────────
    @Test
    void artistGenre_returnsCleanGenre() {
        EntityEnrichmentRequest req = new EntityEnrichmentRequest(
            "artist", "elise-frank", "Elise Frank", null, null,
            Map.of("name", "Elise Frank"),
            List.of("genre"),
            Map.of(),
            Map.of("genre", List.of(
                new TavilyResult(
                    "Elise Frank - biografía",
                    "https://example.com/elise",
                    "Elise Frank es una guitarrista y cantante que se ha hecho un nombre en la escena del blues y el blues rock.",
                    0.9)
            ))
        );

        EnrichedFieldValue genre = adapter.enrich(req).fields().get("genre");
        print("C artist-genre", "genre", genre);

        assertThat(genre.value()).isNotNull();
        assertThat(genre.value().length()).isLessThan(40); // a genre, not a paragraph
        assertThat(genre.value().toLowerCase()).contains("blues");
    }

    // ── D: regression — promo/listing blob must NOT pass as a description ────────
    @Test
    void artistDescription_promoBlob_isRejected() {
        EntityEnrichmentRequest req = new EntityEnrichmentRequest(
            "artist", "emanero", "EMANERO", null, null,
            Map.of("name", "EMANERO"),
            List.of("description"),
            Map.of(),
            Map.of("description", List.of(
                new TavilyResult(
                    "EMANERO gira",
                    "https://www.instagram.com/reel/DQZwM99Difc",
                    "EMANERO TOUR ESPAÑA 2026 TICKETS YA A LA VENTA BARCELONA 04 DE SEPTIEMBRE "
                        + "Razzmatazz MADRID 05 DE SEPTIEMBRE La Riviera VALENCIA 06 DE SEPTIEMBRE",
                    0.9)
            ))
        );

        EnrichedFieldValue description = adapter.enrich(req).fields().get("description");
        print("D promo-blob-regression", "description", description);

        // A tour-dates promo header is not prose → must be null.
        assertThat(description.value()).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private static void print(String scenario, String field, EnrichedFieldValue v) {
        System.out.printf("[LIVE %s] %s -> value=%s | confidence=%s | sourceUrl=%s%n",
            scenario, field,
            v == null ? "<absent>" : v.value(),
            v == null ? "-" : v.confidence(),
            v == null ? "-" : v.sourceUrl());
    }

    private static String resolveApiKey() {
        String fromProp = System.getProperty("llm.apiKey");
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        // Search upward from the working dir for config/application.properties.
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("config").resolve("application.properties");
            if (Files.isRegularFile(candidate)) {
                Properties p = new Properties();
                try (var in = Files.newInputStream(candidate)) {
                    p.load(in);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                String key = p.getProperty("app.llm.api-key");
                if (key != null && !key.isBlank()) {
                    return key;
                }
            }
        }
        return null;
    }
}
