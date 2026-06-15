package org.rubenazo.conciertosfront.core.domain.repository

import org.rubenazo.conciertosfront.core.domain.model.SyncResult

/**
 * Domain port for synchronizing remote data into the local cache. The implementation lives in the
 * data layer ([org.rubenazo.conciertosfront.core.data.repository.SyncRepositoryImpl]); callers
 * depend only on this contract.
 */
interface SyncRepository {
    suspend fun sync(): SyncResult

    /**
     * True when the local database already holds previously synced data, so the app can be
     * shown immediately (cache-first) while a refresh runs in the background. False on a fresh
     * install, where the sync must block until the first data arrives.
     */
    suspend fun hasLocalData(): Boolean
}
