package org.rubenazo.conciertosfront.testutil

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.rubenazo.conciertosfront.core.data.local.dao.SalaConciertoDao
import org.rubenazo.conciertosfront.core.data.local.dao.SalaWithConcertRow
import org.rubenazo.conciertosfront.core.data.local.entity.SalaConciertoEntity

class FakeSalaConciertoDao : SalaConciertoDao {
    private val _entities = MutableStateFlow<List<SalaConciertoEntity>>(emptyList())
    private val _withConcerts = MutableStateFlow<List<SalaWithConcertRow>>(emptyList())

    fun setEntities(entities: List<SalaConciertoEntity>) { _entities.value = entities }
    fun setWithConcerts(rows: List<SalaWithConcertRow>) { _withConcerts.value = rows }

    var upsertedSalas = mutableListOf<SalaConciertoEntity>()

    override suspend fun upsert(salas: List<SalaConciertoEntity>) { upsertedSalas.addAll(salas) }
    override fun getAll(): Flow<List<SalaConciertoEntity>> = _entities
    override suspend fun getById(id: String): SalaConciertoEntity? = _entities.value.find { it.id == id }
    override suspend fun getCount(): Int = _entities.value.size
    override fun getByDateRangeWithConcerts(startDate: String, endDate: String): Flow<List<SalaWithConcertRow>> = _withConcerts
}
