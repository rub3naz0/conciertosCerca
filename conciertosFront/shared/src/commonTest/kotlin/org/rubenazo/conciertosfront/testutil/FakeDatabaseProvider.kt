package org.rubenazo.conciertosfront.testutil

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistDao
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertDao
import org.rubenazo.conciertosfront.core.data.local.dao.SalaConciertoDao
import org.rubenazo.conciertosfront.core.data.local.dao.SyncMetaDao

class FakeDatabaseProvider(
    private val concertDao: ConcertDao = FakeConcertDao(),
    private val artistDao: ArtistDao = FakeArtistDao(),
    private val salaConciertoDao: SalaConciertoDao = FakeSalaConciertoDao(),
    private val syncMetaDao: SyncMetaDao = FakeSyncMetaDao(),
) : DatabaseProviderPort {

    private val _resetSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val resetSignal: SharedFlow<Unit> = _resetSignal.asSharedFlow()

    var resetCallCount = 0

    override fun salaConciertoDao(): SalaConciertoDao = salaConciertoDao
    override fun artistDao(): ArtistDao = artistDao
    override fun concertDao(): ConcertDao = concertDao
    override fun syncMetaDao(): SyncMetaDao = syncMetaDao

    override suspend fun reset() {
        resetCallCount++
        _resetSignal.tryEmit(Unit)
    }

    override suspend fun <R> withTransaction(block: suspend () -> R): R = block()

    fun emitReset() {
        _resetSignal.tryEmit(Unit)
    }
}
