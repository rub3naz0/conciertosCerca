package com.rubenazo.buscaConciertos.scraper.adapters.out;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.RunMetadata;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import com.rubenazo.buscaConciertos.scraper.domain.DiscrepancyType;
import com.rubenazo.buscaConciertos.scraper.domain.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiscrepancyJsonReporterAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writeReport_writesFileWithTimestampInName(@TempDir Path tempDir) throws Exception {
        DiscrepancyJsonReporterAdapter adapter = new DiscrepancyJsonReporterAdapter(
            objectMapper, tempDir.toString()
        );
        RunMetadata meta = new RunMetadata("initial",
            Instant.parse("2026-05-27T10:00:00Z"),
            Instant.parse("2026-05-27T10:05:00Z"),
            10
        );

        adapter.writeReport(List.of(), meta);

        String[] files = tempDir.toFile().list((dir, name) -> name.startsWith("discrepancy-") && name.endsWith(".json"));
        assertThat(files).hasSize(1);
    }

    @Test
    void writeReport_emptyItemsArray_writesValidJson(@TempDir Path tempDir) throws Exception {
        DiscrepancyJsonReporterAdapter adapter = new DiscrepancyJsonReporterAdapter(
            objectMapper, tempDir.toString()
        );
        RunMetadata meta = new RunMetadata("initial",
            Instant.parse("2026-05-27T10:00:00Z"),
            Instant.parse("2026-05-27T10:05:00Z"),
            0
        );

        adapter.writeReport(List.of(), meta);

        Path file = tempDir.toFile().listFiles()[0].toPath();
        JsonNode root = objectMapper.readTree(file.toFile());
        assertThat(root.has("runMode")).isTrue();
        assertThat(root.has("startedAt")).isTrue();
        assertThat(root.has("completedAt")).isTrue();
        assertThat(root.has("totalDiscrepancies")).isTrue();
        assertThat(root.has("items")).isTrue();
        assertThat(root.get("items").isArray()).isTrue();
        assertThat(root.get("items")).isEmpty();
        assertThat(root.get("totalDiscrepancies").asInt()).isZero();
    }

    @Test
    void writeReport_discrepanciesSerializedWithAllFields(@TempDir Path tempDir) throws Exception {
        DiscrepancyJsonReporterAdapter adapter = new DiscrepancyJsonReporterAdapter(
            objectMapper, tempDir.toString()
        );
        Discrepancy d = new Discrepancy(
            DiscrepancyType.PARSE_ERROR, Severity.ERROR,
            "venue", "bcn-sala", "name", "expected", "actual", "<html/>",
            Instant.parse("2026-05-27T10:00:00Z")
        );
        RunMetadata meta = new RunMetadata("initial",
            Instant.parse("2026-05-27T10:00:00Z"),
            Instant.parse("2026-05-27T10:05:00Z"),
            1
        );

        adapter.writeReport(List.of(d), meta);

        Path file = tempDir.toFile().listFiles()[0].toPath();
        JsonNode root = objectMapper.readTree(file.toFile());
        assertThat(root.get("totalDiscrepancies").asInt()).isEqualTo(1);
        JsonNode item = root.get("items").get(0);
        assertThat(item.get("type").asText()).isEqualTo("PARSE_ERROR");
        assertThat(item.get("severity").asText()).isEqualTo("ERROR");
        assertThat(item.get("entityType").asText()).isEqualTo("venue");
        assertThat(item.get("rawSnippet").asText()).isEqualTo("<html/>");
    }
}
