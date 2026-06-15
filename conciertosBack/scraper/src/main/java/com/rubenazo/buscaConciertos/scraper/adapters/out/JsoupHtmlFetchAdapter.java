package com.rubenazo.buscaConciertos.scraper.adapters.out;

import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchException;
import com.rubenazo.buscaConciertos.scraper.application.ports.out.HtmlFetchPort;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JsoupHtmlFetchAdapter implements HtmlFetchPort {

    private static final Logger log = LoggerFactory.getLogger(JsoupHtmlFetchAdapter.class);

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MS = 30_000;

    private final long rateLimitMs;
    private final int maxRetries;
    private volatile long lastRequestTime = 0;

    public JsoupHtmlFetchAdapter(
            @Value("${scraper.rate-limit-ms:500}") long rateLimitMs,
            @Value("${scraper.max-retries:3}") int maxRetries) {
        this.rateLimitMs = rateLimitMs;
        this.maxRetries = maxRetries;
    }

    @Override
    public String fetch(String url) throws HtmlFetchException {
        enforceRateLimit();

        int attempt = 0;
        while (attempt <= maxRetries) {
            try {
                Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(0)
                    .ignoreHttpErrors(true)
                    .execute();

                int status = response.statusCode();

                if (status == 503 && attempt < maxRetries) {
                    attempt++;
                    log.warn("503 on {} — retry {}/{}", url, attempt, maxRetries);
                    continue;
                }

                if (status < 200 || status >= 300) {
                    throw new HtmlFetchException(url, status, "HTTP " + status + " for " + url);
                }

                return response.body();

            } catch (HtmlFetchException e) {
                throw e;
            } catch (Exception e) {
                throw new HtmlFetchException(url, e);
            }
        }

        throw new HtmlFetchException(url, 503, "Max retries (" + maxRetries + ") exhausted for " + url);
    }

    private synchronized void enforceRateLimit() {
        if (rateLimitMs <= 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < rateLimitMs) {
            try {
                Thread.sleep(rateLimitMs - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTime = System.currentTimeMillis();
    }
}
