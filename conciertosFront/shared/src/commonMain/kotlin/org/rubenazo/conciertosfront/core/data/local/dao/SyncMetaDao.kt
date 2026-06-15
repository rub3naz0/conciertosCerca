package org.rubenazo.conciertosfront.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import org.rubenazo.conciertosfront.core.data.local.entity.SyncMetaEntity

@Dao
interface SyncMetaDao {
    @Query("SELECT * FROM sync_meta WHERE key = :key")
    suspend fun getByKey(key: String): SyncMetaEntity?

    @Upsert
    suspend fun upsert(meta: SyncMetaEntity)
}
