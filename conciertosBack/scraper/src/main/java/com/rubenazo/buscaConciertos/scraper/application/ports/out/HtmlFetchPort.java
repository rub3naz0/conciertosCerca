package com.rubenazo.buscaConciertos.scraper.application.ports.out;

public interface HtmlFetchPort {
    /**
     * Fetches raw HTML from the given URL.
     *
     * @throws HtmlFetchException on non-2xx response or network failure
     */
    String fetch(String url) throws HtmlFetchException;
}
