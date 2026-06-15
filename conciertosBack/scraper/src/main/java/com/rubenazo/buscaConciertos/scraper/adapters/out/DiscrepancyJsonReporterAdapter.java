package com.rubenazo.buscaConciertos.scraper.adapters.out;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.DiscrepancyReporterPort;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.RunMetadata;
import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DiscrepancyJsonReporterAdapter implements DiscrepancyReporterPort {

    private static final Logger log = LoggerFactory.getLogger(DiscrepancyJsonReporterAdapter.class);
    private static final DateTimeFormatter FILENAME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final String reportsDir;

    public DiscrepancyJsonReporterAdapter(ObjectMapper objectMapper,
                                           @Value("${scraper.reports-dir}") String reportsDir) {
        this.objectMapper = objectMapper;
        this.reportsDir = reportsDir;
    }

    @Override
    public void writeReport(List<Discrepancy> discrepancies, RunMetadata meta) {
        String timestamp = FILENAME_FORMAT.format(Instant.now());
        String filename = "discrepancy-" + timestamp + ".json";

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runMode", meta.runMode());
        report.put("startedAt", meta.startedAt().toString());
        report.put("completedAt", meta.completedAt().toString());
        report.put("totalProcessed", meta.totalProcessed());
        report.put("totalDiscrepancies", discrepancies.size());
        report.put("items", discrepancies.stream().map(this::discrepancyToMap).toList());

        try {
            Path dir = Path.of(reportsDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), report);
            log.info("Written discrepancy report: {}", target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write discrepancy report: " + filename, e);
        }
    }

    private Map<String, Object> discrepancyToMap(Discrepancy d) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", d.type().name());
        map.put("severity", d.severity().name());
        map.put("entityType", d.entityType());
        map.put("entityId", d.entityId());
        map.put("field", d.field());
        map.put("expected", d.expected());
        map.put("actual", d.actual());
        map.put("rawSnippet", d.rawSnippet());
        map.put("timestamp", d.timestamp().toString());
        return map;
    }
}
