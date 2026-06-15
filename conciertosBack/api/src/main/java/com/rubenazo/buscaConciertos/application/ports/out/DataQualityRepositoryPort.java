package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.DataQuality;

import java.util.List;
import java.util.Optional;

public interface DataQualityRepositoryPort {
    List<DataQuality> findByStatus(String status);
    List<DataQuality> findByEntityTypeAndStatus(String entityType, String status);
    Optional<DataQuality> findById(Long id);
    List<DataQuality> findByStatusAndScore(String status, double minScore, String severityFilter);
    List<String> findEntityIdsBySourceAndField(String entityType, String source, List<String> fields);
}
