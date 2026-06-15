package com.rubenazo.buscaConciertos.adapters.out.healthchecks;

import com.rubenazo.buscaConciertos.application.ports.out.SyncHeartbeatPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NoOpSyncHeartbeatAdapter implements SyncHeartbeatPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpSyncHeartbeatAdapter.class);

    @Override
    public void pingStart() {
        log.debug("healthchecks.io heartbeat is disabled: app.healthchecks.ping-url is not configured");
    }

    @Override
    public void pingSuccess(String summary) {
        // no-op
    }

    @Override
    public void pingFail(String summary) {
        // no-op
    }
}
