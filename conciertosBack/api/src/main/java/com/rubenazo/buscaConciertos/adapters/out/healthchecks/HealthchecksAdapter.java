package com.rubenazo.buscaConciertos.adapters.out.healthchecks;

import com.rubenazo.buscaConciertos.application.ports.out.SyncHeartbeatPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Sends heartbeat pings to a healthchecks.io check (or any compatible
 * dead-man's-switch endpoint) so a missed scheduled sync triggers an alert.
 *
 * <p>Ping semantics (healthchecks.io convention):
 * <ul>
 *   <li>{@code GET/POST <url>/start} — run started</li>
 *   <li>{@code GET/POST <url>} — run succeeded</li>
 *   <li>{@code GET/POST <url>/fail} — run failed</li>
 * </ul>
 *
 * <p>Any transport error is caught and logged as a warning — monitoring must
 * never affect the sync it is observing.
 */
class HealthchecksAdapter implements SyncHeartbeatPort {

    private static final Logger log = LoggerFactory.getLogger(HealthchecksAdapter.class);

    private final RestClient restClient;

    HealthchecksAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void pingStart() {
        ping("/start", null);
    }

    @Override
    public void pingSuccess(String summary) {
        ping("", summary);
    }

    @Override
    public void pingFail(String summary) {
        ping("/fail", summary);
    }

    private void ping(String path, String body) {
        try {
            restClient.post()
                .uri(path)
                .body(body == null ? "" : body)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("healthchecks.io ping to '{}' failed — sync is unaffected: {}", path, e.getMessage());
        }
    }
}
