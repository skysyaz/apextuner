package com.apextuner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apextuner.data.db.entity.TunerLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Query("SELECT * FROM tuner_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<TunerLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TunerLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TunerLogEntity>)

    @Query("DELETE FROM tuner_logs WHERE timestamp < :cutoff")
    suspend fun trimOlderThan(cutoff: Long): Int

    @Query("DELETE FROM tuner_logs")
    suspend fun clear(): Int

    @Query("SELECT COUNT(*) FROM tuner_logs")
    suspend fun count(): Int
}
