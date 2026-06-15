package com.rubenazo.buscaConciertos.scraper.application.ports.out;

public class HtmlFetchException extends Exception {

    private final int statusCode;
    private final String url;

    public HtmlFetchException(String url, int statusCode, String message) {
        super(message);
        this.url = url;
        this.statusCode = statusCode;
    }

    public HtmlFetchException(String url, Throwable cause) {
        super("Failed to fetch: " + url, cause);
        this.url = url;
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getUrl() {
        return url;
    }
}
