package com.rubenazo.buscaConciertos.adminweb.application;

import com.rubenazo.buscaConciertos.adminweb.application.ports.in.DataQualityReadPort;
import com.rubenazo.buscaConciertos.adminweb.application.ports.out.QualityFillPort;
import org.springframework.stereotype.Service;

import java.util.List;

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
