package com.rubenazo.buscaConciertos.adapters.out.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import com.rubenazo.buscaConciertos.domain.EntityEnrichmentRequest;
import com.rubenazo.buscaConciertos.domain.EnrichedEntityResult;
import com.rubenazo.buscaConciertos.domain.TavilyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAiEnrichmentAdapterTest {

    @Mock
    private OpenAIClient client;
    @Mock
    private ChatService chatService;
    @Mock
    private ChatCompletionService completionService;

    private OpenAiEnrichmentAdapter adapter;

    @BeforeEach
    void setUp() {
        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        adapter = new OpenAiEnrichmentAdapter(client, "gpt-4o-mini");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private ChatCompletion completionWithContent(String content) {
        ChatCompletionMessage message = ChatCompletionMessage.builder()
            .content(content)
            .refusal((String) null)
            .build();
        CompletionUsage usage = CompletionUsage.builder()
            .completionTokens(50L)
            .promptTokens(200L)
            .totalTokens(250L)
            .build();
        ChatCompletion.Choice choice = ChatCompletion.Choice.builder()
            .finishReason(ChatCompletion.Choice.FinishReason.STOP)
            .index(0L)
            .logprobs((ChatCompletion.Choice.Logprobs) null)
            .message(message)
            .build();
        return ChatCompletion.builder()
            .id("chatcmpl-test")
            .choices(List.of(choice))
            .created(1700000000L)
            .model("gpt-4o-mini")
            .usage(usage)
            .build();
    }

    private EntityEnrichmentRequest salaRequest() {
        return new EntityEnrichmentRequest(
            "sala", "sala-1", "Sala Apolo", "Barcelona", "Cataluña",
            Map.of("name", "Sala Apolo", "city", "Barcelona"),
            List.of("address", "description"),
            Map.of(),
            Map.of(
                "address", List.of(new TavilyResult("Sala Apolo dirección", "https://example.com/apolo", "Carrer Nou de la Rambla, 113", 0.9)),
                "description", List.of(new TavilyResult("Acerca de Sala Apolo", "https://example.com/desc", "Historic music venue", 0.85))
            )
        );
    }

    // ── tests ─────────────────────────────────────────────────────────────

    @Test
    void buildsSchemaFromMissingFields() {
        String responseJson = """
            {
              "address": {"value": "Carrer Nou de la Rambla, 113", "sourceUrl": "https://example.com/apolo", "confidence": 0.95},
              "description": {"value": "Historic music venue", "sourceUrl": "https://example.com/desc", "confidence": 0.88}
            }
            """;
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completionWithContent(responseJson));

        ArgumentCaptor<ChatCompletionCreateParams> captor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        adapter.enrich(salaRequest());

        verify(completionService).create(captor.capture());
        ChatCompletionCreateParams params = captor.getValue();
        // responseFormat toString should contain field names from missingFields
        String responseFormatStr = params.responseFormat().map(Object::toString).orElse("");
        assertThat(responseFormatStr).contains("address");
        assertThat(responseFormatStr).contains("description");
    }

    @Test
    void injectsEntityJsonAndEvidence() {
        String responseJson = """
            {
              "address": {"value": "Carrer Nou de la Rambla, 113", "sourceUrl": "https://example.com/apolo", "confidence": 0.95},
              "description": {"value": null, "sourceUrl": null, "confidence": 0.0}
            }
            """;
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completionWithContent(responseJson));

        ArgumentCaptor<ChatCompletionCreateParams> captor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        adapter.enrich(salaRequest());

        verify(completionService).create(captor.capture());
        ChatCompletionCreateParams params = captor.getValue();
        // Messages (as string representation) should contain entity name
        String messagesStr = params.messages().toString();
        assertThat(messagesStr).containsIgnoringCase("Sala Apolo");
    }

    @Test
    void parsesStructuredResponse_returnsEnrichedFieldValues() {
        String responseJson = """
            {
              "address": {"value": "Carrer Nou de la Rambla, 113", "sourceUrl": "https://example.com/apolo", "confidence": 0.95},
              "description": {"value": "Historic music venue in Barcelona", "sourceUrl": "https://example.com/desc", "confidence": 0.88}
            }
            """;
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completionWithContent(responseJson));

        EnrichedEntityResult result = adapter.enrich(salaRequest());

        assertThat(result.fields()).containsKey("address");
        assertThat(result.fields()).containsKey("description");
        assertThat(result.fields().get("address").value()).isEqualTo("Carrer Nou de la Rambla, 113");
        assertThat(result.fields().get("address").sourceUrl()).isEqualTo("https://example.com/apolo");
        assertThat(result.fields().get("address").confidence()).isEqualTo(0.95);
        assertThat(result.fields().get("description").value()).isEqualTo("Historic music venue in Barcelona");
        assertThat(result.fields().get("description").confidence()).isEqualTo(0.88);
    }

    @Test
    void nullValueField_treatedAsMissing() {
        String responseJson = """
            {
              "address": {"value": null, "sourceUrl": null, "confidence": 0.0},
              "description": {"value": "Historic music venue", "sourceUrl": "https://example.com/desc", "confidence": 0.85}
            }
            """;
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completionWithContent(responseJson));

        EnrichedEntityResult result = adapter.enrich(salaRequest());

        // null value is preserved in the result (stays-missing semantics handled by use case)
        assertThat(result.fields().get("address").value()).isNull();
        assertThat(result.fields().get("description").value()).isEqualTo("Historic music venue");
    }

    @Test
    void systemPrompt_includesMusicDomainAndConfidenceRubric() {
        String responseJson = """
            {
              "address": {"value": "Carrer Nou de la Rambla, 113", "sourceUrl": "https://example.com/apolo", "confidence": 0.95},
              "description": {"value": "Historic music venue", "sourceUrl": "https://example.com/desc", "confidence": 0.88}
            }
            """;
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completionWithContent(responseJson));

        ArgumentCaptor<ChatCompletionCreateParams> captor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        adapter.enrich(salaRequest());

        verify(completionService).create(captor.capture());
        String messagesStr = captor.getValue().messages().toString();

        // Music domain is made explicit so the model disambiguates artists/venues toward live music
        assertThat(messagesStr).containsIgnoringCase("música");

        // Confidence is anchored to a calibration scale because its value crosses auto-apply thresholds
        assertThat(messagesStr).containsIgnoringCase("Calibración de confianza");
        assertThat(messagesStr).containsIgnoringCase("fuente oficial");
        assertThat(messagesStr).contains("0.9");
        assertThat(messagesStr).contains("0.7");
    }

    @Test
    void systemPrompt_includesGenreControlledVocabulary() {
        String responseJson = """
            {
              "address": {"value": "Carrer Nou de la Rambla, 113", "sourceUrl": "https://example.com/apolo", "confidence": 0.95},
              "description": {"value": "Historic music venue", "sourceUrl": "https://example.com/desc", "confidence": 0.88}
            }
            """;
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completionWithContent(responseJson));

        ArgumentCaptor<ChatCompletionCreateParams> captor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        adapter.enrich(salaRequest());

        verify(completionService).create(captor.capture());
        String messagesStr = captor.getValue().messages().toString();

        // The genre value must map to the source taxonomy (front builds filter chips by splitting on "," and "/").
        // Passing the canonical compound labels prevents orphan chips like "Indie rock".
        assertThat(messagesStr).containsIgnoringCase("vocabulario de género");
        assertThat(messagesStr).contains("Pop-rock/Indie");
        assertThat(messagesStr).contains("Flamenco/Copla");
        // Anti-invention guard
        assertThat(messagesStr).containsIgnoringCase("NUNCA inventes");
    }

    @Test
    void malformedResponse_returnsEmpty() {
        when(completionService.create(any(ChatCompletionCreateParams.class)))
            .thenReturn(completionWithContent("this is not json {{{"));

        EnrichedEntityResult result = adapter.enrich(salaRequest());

        assertThat(result).isEqualTo(EnrichedEntityResult.empty());
        assertThat(result.fields()).isEmpty();
    }

    @Test
    void clientThrows_returnsEmpty() {
        when(completionService.create(any(ChatCompletionCreateParams.class)))
            .thenThrow(new RuntimeException("Network error"));

        EnrichedEntityResult result = adapter.enrich(salaRequest());

        assertThat(result).isEqualTo(EnrichedEntityResult.empty());
        assertThat(result.fields()).isEmpty();
    }

    @Test
    void confidenceOutOfRange_isClampedToZeroOne() {
        String responseJson = """
            {
              "address": {"value": "Carrer Nou de la Rambla, 113", "sourceUrl": "https://example.com/apolo", "confidence": 1.5},
              "description": {"value": "Historic music venue", "sourceUrl": "https://example.com/desc", "confidence": -0.3}
            }
            """;
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completionWithContent(responseJson));

        EnrichedEntityResult result = adapter.enrich(salaRequest());

        assertThat(result.fields().get("address").confidence()).isEqualTo(1.0);
        assertThat(result.fields().get("description").confidence()).isEqualTo(0.0);
    }
}
