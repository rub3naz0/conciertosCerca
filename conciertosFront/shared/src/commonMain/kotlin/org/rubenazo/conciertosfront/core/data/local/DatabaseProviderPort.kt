package org.rubenazo.conciertosfront.core.data.local

import kotlinx.coroutines.flow.SharedFlow
import org.rubenazo.conciertosfront.core.data.local.dao.ArtistDao
import org.rubenazo.conciertosfront.core.data.local.dao.ConcertDao
import org.rubenazo.conciertosfront.core.data.local.dao.SalaConciertoDao
import org.rubenazo.conciertosfront.core.data.local.dao.SyncMetaDao

interface DatabaseProviderPort {
    val resetSignal: SharedFlow<Unit>
    fun salaConciertoDao(): SalaConciertoDao
    fun artistDao(): ArtistDao
    fun concertDao(): ConcertDao
    fun syncMetaDao(): SyncMetaDao
    suspend fun reset()
    suspend fun <R> withTransaction(block: suspend () -> R): R
}
