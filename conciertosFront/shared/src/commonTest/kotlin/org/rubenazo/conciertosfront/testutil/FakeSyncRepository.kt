package org.rubenazo.conciertosfront.testutil

import org.rubenazo.conciertosfront.core.domain.model.SyncResult
import org.rubenazo.conciertosfront.core.domain.repository.SyncRepository

class FakeSyncRepository : SyncRepository {
    var syncResult: SyncResult = SyncResult(0, 0, 0, 0, true, emptyList())
    var shouldThrow: Exception? = null
    var syncCalledCount = 0
    var localDataPresent = false

    override suspend fun sync(): SyncResult {
        syncCalledCount++
        shouldThrow?.let { throw it }
        return syncResult
    }

    override suspend fun hasLocalData(): Boolean = localDataPresent
}
