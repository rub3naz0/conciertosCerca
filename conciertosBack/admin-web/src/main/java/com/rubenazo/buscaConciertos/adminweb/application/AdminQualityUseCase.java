package com.rubenazo.buscaConciertos.adminweb.application;

import com.rubenazo.buscaConciertos.adminweb.application.ports.in.DataQualityReadPort;
import com.rubenazo.buscaConciertos.adminweb.application.ports.out.QualityFillPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Admin-web use case backing the manual-fill screen: lists the unresolved SEVERE data-quality issues
 * and submits a human-provided value for one.
 *
 * It is a thin orchestrator over two ports whose adapters cross the service boundary — the read side
 * ({@link DataQualityReadPort}) queries the shared SQLite directly, while the fill side
 * ({@link QualityFillPort}) proxies over HTTP to the public API. The admin web keeps no state of its
 * own.
 */
@Service
public class AdminQualityUseCase {

    private final DataQualityReadPort readPort;
    private final QualityFillPort fillPort;

    public AdminQualityUseCase(DataQualityReadPort readPort, QualityFillPort fillPort) {
        this.readPort = readPort;
        this.fillPort = fillPort;
    }

    public List<SevereIssue> listUnresolvedSevere() {
        return readPort.listUnresolvedSevere();
    }

    public void fill(Long id, String value) {
        fillPort.fill(id, value);
    }
}
