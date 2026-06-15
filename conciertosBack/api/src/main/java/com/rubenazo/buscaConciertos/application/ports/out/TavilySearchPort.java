package com.rubenazo.buscaConciertos.application.ports.out;

import com.rubenazo.buscaConciertos.domain.SearchOptions;
import com.rubenazo.buscaConciertos.domain.TavilyResult;

import java.util.List;

public interface TavilySearchPort {

    List<TavilyResult> search(String query, SearchOptions options);

    default List<TavilyResult> search(String query) {
        return search(query, SearchOptions.defaults());
    }
}
