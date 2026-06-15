package com.rubenazo.buscaConciertos.adminweb.application.ports.in;

import com.rubenazo.buscaConciertos.adminweb.application.SevereIssue;

import java.util.List;

public interface DataQualityReadPort {
    List<SevereIssue> listUnresolvedSevere();
}
