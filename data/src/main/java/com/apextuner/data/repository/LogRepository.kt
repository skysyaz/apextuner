package com.apextuner.data.repository

import com.apextuner.data.db.LogDao
import com.apextuner.data.db.entity.TunerLogEntity
import com.apextuner.data.model.TunerLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val dao: LogDao
) {
    fun observeRecent(limit: Int = 500): Flow<List<TunerLog>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    suspend fun log(
        level: TunerLog.Level,
        category: TunerLog.Category,
        message: String,
        detail: String? = null
    ) {
        dao.insert(
            TunerLogEntity(
                id = 0L,
                timestamp = System.currentTimeMillis(),
                level = level.name,
                category = category.name,
                message = message,
                detail = detail
            )
        )
    }

    suspend fun trimOlderThan(cutoff: Long) = dao.trimOlderThan(cutoff)

    suspend fun clear() = dao.clear()

    private fun TunerLogEntity.toDomain() = TunerLog(
        id = id, timestamp = timestamp,
        level = runCatching { TunerLog.Level.valueOf(level) }.getOrDefault(TunerLog.Level.INFO),
        category = runCatching { TunerLog.Category.valueOf(category) }.getOrDefault(TunerLog.Category.SYSTEM),
        message = message, detail = detail
    )
}
