package org.rubenazo.conciertosfront.testutil

import org.rubenazo.conciertosfront.core.data.local.dao.SyncMetaDao
import org.rubenazo.conciertosfront.core.data.local.entity.SyncMetaEntity

class FakeSyncMetaDao : SyncMetaDao {
    val store = mutableMapOf<String, SyncMetaEntity>()

    override suspend fun getByKey(key: String): SyncMetaEntity? = store[key]

    override suspend fun upsert(meta: SyncMetaEntity) {
        store[meta.key] = meta
    }
}
