package com.rubenazo.buscaConciertos.application.ports.out;

/**
 * Output port for an external dead-man's-switch monitor (e.g. healthchecks.io).
 *
 * <p>The scheduled weekly sync is the only caller: it reports that the schedule
 * fired, and later whether both sync sources actually refreshed the data. Manual
 * syncs triggered via {@code /api/admin/sync*} do not report through this port.
 *
 * <p>Implementations must never let monitoring failures affect the sync itself —
 * any transport error talking to the monitor must be caught and logged, not
 * propagated.
 */
public interface SyncHeartbeatPort {

    /**
     * Signals that the scheduled sync run has started.
     */
    void pingStart();

    /**
     * Signals that the scheduled sync run finished successfully.
     *
     * @param summary short, human-readable summary included in the ping body
     */
    void pingSuccess(String summary);

    /**
     * Signals that the scheduled sync run did not fully succeed.
     *
     * @param summary short, human-readable reason included in the ping body
     */
    void pingFail(String summary);
}
