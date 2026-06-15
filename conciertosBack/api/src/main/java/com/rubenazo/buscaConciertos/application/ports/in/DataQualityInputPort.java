package com.rubenazo.buscaConciertos.application.ports.in;

import com.rubenazo.buscaConciertos.domain.DataQuality;

import java.util.List;

public interface DataQualityInputPort {
    List<DataQuality> listIssues(String status);
    List<DataQuality> listIssues(String status, Double minScore);
    void approve(Long id);
    void reject(Long id);
    int approveAll(double minScore, String severityFilter);
    void fill(Long id, String value);
}
