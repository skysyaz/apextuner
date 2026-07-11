package com.apextuner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.apextuner.data.db.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY label COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE packageName = :pkg LIMIT 1")
    suspend fun getByPackage(pkg: String): GameEntity?

    @Query("SELECT * FROM games WHERE enabled = 1")
    suspend fun getEnabledGames(): List<GameEntity>

    @Upsert
    suspend fun upsert(entity: GameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<GameEntity>)

    @Query("DELETE FROM games WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("UPDATE games SET lastLaunchedAt = :ts WHERE packageName = :pkg")
    suspend fun markLaunched(pkg: String, ts: Long)

    @Query("UPDATE games SET profileId = :profileId WHERE packageName = :pkg")
    suspend fun setProfile(pkg: String, profileId: Long)

    @Query("UPDATE games SET enabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)
}
