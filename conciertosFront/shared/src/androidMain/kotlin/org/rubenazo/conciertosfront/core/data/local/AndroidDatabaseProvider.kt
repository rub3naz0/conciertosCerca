package org.rubenazo.conciertosfront.core.data.local

import android.content.Context
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistDao
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertDao
import org.rubenazo.conciertosfront.core.data.local.dao.SalaConciertoDao
import org.rubenazo.conciertosfront.core.data.local.dao.SyncMetaDao

class AndroidDatabaseProvider(
    private val context: Context,
    private val builderFactory: () -> AppDatabase,
) : DatabaseProviderPort {

    private val mutex = Mutex()
    private var db: AppDatabase = builderFactory()

    private val _resetSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val resetSignal: SharedFlow<Unit> = _resetSignal.asSharedFlow()

    override fun salaConciertoDao(): SalaConciertoDao = db.salaConciertoDao()
    override fun artistDao(): ArtistDao = db.artistDao()
    override fun concertDao(): ConcertDao = db.concertDao()
    override fun syncMetaDao(): SyncMetaDao = db.syncMetaDao()

    override suspend fun reset() {
        mutex.withLock {
            db.close()
            context.deleteDatabase(DB_NAME)
            db = builderFactory()
            _resetSignal.tryEmit(Unit)
        }
    }

    override suspend fun <R> withTransaction(block: suspend () -> R): R =
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }
}
