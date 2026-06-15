package org.rubenazo.conciertosfront.core.data.local

import kotlinx.coroutines.flow.SharedFlow
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistDao
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertDao
import org.rubenazo.conciertosfront.core.data.local.dao.SalaConciertoDao
import org.rubenazo.conciertosfront.core.data.local.dao.SyncMetaDao

/**
 * Port over the local Room database, kept platform-agnostic in commonMain.
 *
 * Exposes the DAOs plus two cross-cutting operations: [withTransaction] for atomic multi-table
 * writes, and [reset] to drop and recreate a corrupted database. After a [reset], [resetSignal]
 * notifies observers (e.g. ViewModels) so they re-subscribe to the new database instance.
 */
interface DatabaseProviderPort {
    val resetSignal: SharedFlow<Unit>
    fun salaConciertoDao(): SalaConciertoDao
    fun artistDao(): ArtistDao
    fun concertDao(): ConcertDao
    fun syncMetaDao(): SyncMetaDao
    suspend fun reset()
    suspend fun <R> withTransaction(block: suspend () -> R): R
}
