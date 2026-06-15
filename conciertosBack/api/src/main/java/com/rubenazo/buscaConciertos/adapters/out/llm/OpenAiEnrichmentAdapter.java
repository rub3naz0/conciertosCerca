package com.rubenazo.buscaConciertos.adapters.out.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.rubenazo.buscaConciertos.application.ports.out.EntityEnrichmentPort;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;
import com.rubenazo.buscaConciertos.domain.EnrichedFieldValue;
import com.rubenazo.buscaConciertos.domain.EntityEnrichmentRequest;
import com.rubenazo.buscaConciertos.domain.TavilyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Out-adapter for the LLM enrichment step: takes an {@link EntityEnrichmentRequest} plus the Tavily
 * snippets and asks OpenAI to extract structured field values, returned as an
 * {@link EnrichedEntityResult} with a per-field confidence the use case thresholds against.
 *
 * Uses a JSON-schema response format so the model output is parseable, not free text. Package-private
 * and paired with {@code LlmNoOpAdapter}; {@code LlmConfig} picks the active bean by API-key presence.
 */
class OpenAiEnrichmentAdapter implements EntityEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEnrichmentAdapter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Source (conciertos.club) genre taxonomy. The mobile client builds its genre filter chips by
    // splitting each artist genre on "," and "/", so any free-text variant the LLM invents (e.g.
    // "Indie rock") becomes an orphan chip that matches nothing. The model must map to one of these
    // canonical compound labels — never coin a new one. Keep this list in sync with the source taxonomy.
    private static final List<String> GENRE_VOCABULARY = List.of(
        "Pop-rock/Indie", "Rock/Rock Alternativo", "Pop", "Pop Latino", "Música Latina",
        "Urbana/Reggaeton/Trap", "Hip-Hop", "Soul/Funk", "Blues/RNB", "Jazz/Swing",
        "Flamenco/Copla", "Cantautores", "Mestizaje/Fusión", "Rock and roll/Garage",
        "Metal/Rock duro", "Punk/Hardcore", "Post-Punk", "Reggae/Ska", "Electrónica/Dance",
        "Clásica", "Música Coral", "Danza/Ballet", "Americana/Folk Rock/Country", "Folk",
        "World Music/Música Étnica", "Versiones/Tributos", "Musicales/Teatro musical",
        "Música ligera", "Varios"
    );

    private final OpenAIClient client;
    private final String model;

    // Package-visible constructor for tests (inject mock client)
    OpenAiEnrichmentAdapter(OpenAIClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public EnrichedEntityResult enrich(EntityEnrichmentRequest request) {
        try {
            ResponseFormatJsonSchema responseFormat = buildResponseFormat(request.missingFields());
            String userMessage = buildUserMessage(request);
            String systemPrompt = buildSystemPrompt();

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(model)
                .addSystemMessage(systemPrompt)
                .addUserMessage(userMessage)
                .responseFormat(responseFormat)
                .build();

            ChatCompletion completion = client.chat().completions().create(params);

            Optional<String> usage = completion.usage().map(u ->
                "promptTokens=" + u.promptTokens() + " completionTokens=" + u.completionTokens());
            log.info("LLM enrichment: entity={}/{} model={} {}",
                request.entityType(), request.entityId(), model, usage.orElse("no-usage"));

            String content = completion.choices().stream()
                .findFirst()
                .flatMap(c -> c.message().content())
                .orElse(null);

            if (content == null || content.isBlank()) {
                log.warn("LLM enrichment: empty response for entity={}/{}", request.entityType(), request.entityId());
                return EnrichedEntityResult.empty();
            }

            return parseResponse(content, request);

        } catch (Exception e) {
            log.warn("LLM enrichment failed for entity={}/{}: {}", request.entityType(), request.entityId(), e.getMessage());
            return EnrichedEntityResult.empty();
        }
    }

    private ResponseFormatJsonSchema buildResponseFormat(List<String> missingFields) {
        // Build per-field schema: each field → {value: string|null, sourceUrl: string|null, confidence: number}
        Map<String, Object> fieldSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "value", Map.of("type", List.of("string", "null")),
                "sourceUrl", Map.of("type", List.of("string", "null")),
                "confidence", Map.of("type", "number")
            ),
            "required", List.of("value", "sourceUrl", "confidence"),
            "additionalProperties", false
        );

        Map<String, Object> properties = new LinkedHashMap<>();
        for (String field : missingFields) {
            properties.put(field, fieldSchema);
        }

        Map<String, Object> schemaMap = new LinkedHashMap<>();
        schemaMap.put("type", "object");
        schemaMap.put("properties", properties);
        schemaMap.put("required", missingFields);
        schemaMap.put("additionalProperties", false);

        ResponseFormatJsonSchema.JsonSchema jsonSchema = ResponseFormatJsonSchema.JsonSchema.builder()
            .name("entity_enrichment")
            .strict(true)
            .schema(ResponseFormatJsonSchema.JsonSchema.Schema.builder()
                .putAllAdditionalProperties(toJsonValueMap(schemaMap))
                .build())
            .build();

        return ResponseFormatJsonSchema.builder()
            .jsonSchema(jsonSchema)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, JsonValue> toJsonValueMap(Map<String, Object> map) {
        Map<String, JsonValue> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), JsonValue.from(entry.getValue()));
        }
        return result;
    }

    private String buildSystemPrompt() {
        return """
            Eres un asistente especializado en completar información faltante de entidades del ámbito \
            de la MÚSICA EN VIVO: salas de conciertos y artistas musicales (bandas, cantantes, DJs, \
            solistas). Todo el dominio gira en torno a conciertos, salas y artistas de música; ante un \
            nombre ambiguo, asume SIEMPRE el sentido musical (p. ej. un artista es un músico o banda, \
            no un pintor ni un actor; una sala es un recinto de conciertos). Completa la información \
            usando únicamente la evidencia proporcionada y los datos que ya conocemos de la entidad.

            DATOS CONOCIDOS:
            - El mensaje incluye "Campos conocidos": son DATOS FIRMES y verificados de la entidad \
            (nombre, ciudad, provincia, etc.). Trátalos como verdad de base.
            - SOLO debes completar los campos listados en "Campos a completar". \
            NO toques ni reescribas los campos conocidos.

            REGLAS ESTRICTAS:
            1. SOLO completa un campo cuando la evidencia lo respalde DIRECTAMENTE.
            2. Todo valor que completes DEBE ser coherente con los campos conocidos. En particular, \
            una dirección o ubicación tiene que pertenecer a la ciudad y provincia conocidas; si la \
            evidencia apunta a otra localidad, devuelve "value": null y "confidence": 0.0.
            3. Si la evidencia no respalda un valor, o contradice los datos conocidos, \
            devuelve "value": null y "confidence": 0.0.
            4. NO inventes información ni hagas suposiciones sin evidencia.
            5. Devuelve valores completos y limpios, NUNCA fragmentos, etiquetas ni encabezados sueltos \
            (ej: NO devuelvas "Código Postal" ni "Dirección:" como valor de un campo).
            6. Una "description" es PROSA que describe a la entidad (quién es el artista o qué es la sala). \
            Si la evidencia para "description" es un listado de fechas de gira, precios, "tickets a la venta", \
            nombres de salas/ciudades o un titular promocional, NO es una descripción: devuelve "value": null \
            y "confidence": 0.0. Ante la duda de si es prosa descriptiva o un volcado promocional, devuelve null.
            7. El campo "confidence" (0.0 a 1.0) refleja cuán directamente la evidencia respalda el \
            valor y cuán coherente es con los datos conocidos. Asigna su valor SIEMPRE según la \
            siguiente escala de calibración, no por intuición.
            8. Usa el "url" de la evidencia más relevante como "sourceUrl".
            9. Responde SOLO con el JSON estructurado solicitado, sin texto adicional.

            Calibración de confianza (anclá cada valor a estos tramos):
            - 0.9 a 1.0: fuente oficial o Wikipedia, con coincidencia EXACTA de nombre y, si aplica, \
            de ciudad/provincia conocidas. Evidencia inequívoca.
            - 0.7 a 0.8: fuente secundaria fiable, coherente con los datos conocidos, sin contradicciones.
            - 0.4 a 0.6: evidencia parcial, indirecta o que requiere inferencia para encajar.
            - 0.0 a 0.3: sin respaldo directo, fuente dudosa o posible confusión de entidad. \
            En este tramo devuelve "value": null.

            VOCABULARIO DE GÉNERO (aplica SOLO al campo "genre" de artistas):
            El valor de "genre" DEBE ser EXACTAMENTE una de las categorías de la siguiente lista \
            (es la taxonomía oficial de la fuente). Elegí la MÁS específica que encaje con la evidencia. \
            Si ninguna encaja con confianza, devuelve "value": null. NUNCA inventes una variante nueva, \
            ni la traduzcas, ni devuelvas un fragmento (p. ej. NO devuelvas "Indie rock": usa "Pop-rock/Indie").
            Categorías permitidas: %s\
            """.formatted(String.join(", ", GENRE_VOCABULARY));
    }

    private String buildUserMessage(EntityEnrichmentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Entidad: ").append(request.entityType())
          .append(" | Nombre: ").append(request.name());
        if (request.city() != null) {
            sb.append(" | Ciudad: ").append(request.city());
        }
        if (request.province() != null) {
            sb.append(" | Provincia: ").append(request.province());
        }
        sb.append("\n\n");

        // Known fields as JSON
        try {
            sb.append("Campos conocidos:\n");
            sb.append(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(request.knownFields()));
            sb.append("\n\n");
        } catch (Exception e) {
            sb.append("Campos conocidos: ").append(request.knownFields()).append("\n\n");
        }

        // Context hints (e.g. cities where artist plays)
        if (!request.contextHints().isEmpty()) {
            sb.append("Contexto adicional:\n");
            request.contextHints().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
            sb.append("\n");
        }

        // Missing fields
        sb.append("Campos a completar: ").append(request.missingFields()).append("\n\n");

        // Per-field evidence
        sb.append("Evidencia por campo:\n");
        for (String field : request.missingFields()) {
            List<TavilyResult> hits = request.evidence().getOrDefault(field, List.of());
            sb.append("--- ").append(field).append(" ---\n");
            if (hits.isEmpty()) {
                sb.append("  (sin evidencia disponible)\n");
            } else {
                for (TavilyResult hit : hits) {
                    sb.append("  Título: ").append(hit.title()).append("\n");
                    sb.append("  URL: ").append(hit.url()).append("\n");
                    sb.append("  Contenido: ").append(hit.content()).append("\n");
                    sb.append("  Relevancia: ").append(hit.score()).append("\n\n");
                }
            }
        }

        return sb.toString();
    }

    private EnrichedEntityResult parseResponse(String content, EntityEnrichmentRequest request) {
        try {
            Map<String, Map<String, Object>> parsed = MAPPER.readValue(content,
                new TypeReference<Map<String, Map<String, Object>>>() {});

            Map<String, EnrichedFieldValue> fields = new HashMap<>();
            for (String field : request.missingFields()) {
                Map<String, Object> fieldData = parsed.get(field);
                if (fieldData == null) {
                    continue;
                }
                String value = fieldData.get("value") != null ? String.valueOf(fieldData.get("value")) : null;
                String sourceUrl = fieldData.get("sourceUrl") != null ? String.valueOf(fieldData.get("sourceUrl")) : null;
                double confidence = fieldData.get("confidence") != null
                    ? ((Number) fieldData.get("confidence")).doubleValue()
                    : 0.0;
                // Clamp into [0.0, 1.0] — OpenAI strict mode can't enforce min/max in the schema
                confidence = Math.max(0.0, Math.min(1.0, confidence));

                // Blank string → treat as null
                if (value != null && value.isBlank()) {
                    value = null;
                }

                fields.put(field, new EnrichedFieldValue(value, sourceUrl, confidence));
            }

            return new EnrichedEntityResult(fields);

        } catch (Exception e) {
            log.warn("LLM enrichment: failed to parse response for entity={}/{}: {}",
                request.entityType(), request.entityId(), e.getMessage());
            return EnrichedEntityResult.empty();
        }
    }
}
