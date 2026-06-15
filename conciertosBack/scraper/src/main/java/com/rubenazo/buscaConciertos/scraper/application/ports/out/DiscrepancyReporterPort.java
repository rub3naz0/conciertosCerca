package com.rubenazo.buscaConciertos.scraper.application.ports.out;

import com.rubenazo.buscaConciertos.scraper.domain.Discrepancy;

import java.util.List;

public interface DiscrepancyReporterPort {
    void writeReport(List<Discrepancy> discrepancies, RunMetadata meta);
}
