package com.apextuner.data.repository

import com.apextuner.data.db.GameDao
import com.apextuner.data.db.entity.GameEntity
import com.apextuner.data.model.Game
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val dao: GameDao
) {
    fun observeAll(): Flow<List<Game>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun getByPackage(pkg: String): Game? = dao.getByPackage(pkg)?.toDomain()

    suspend fun getEnabledGames(): List<Game> = dao.getEnabledGames().map { it.toDomain() }

    suspend fun upsert(game: Game) = dao.upsert(game.toEntity())

    suspend fun insertAll(games: List<Game>) = dao.insertAll(games.map { it.toEntity() })

    suspend fun delete(pkg: String) = dao.deleteByPackage(pkg)

    suspend fun markLaunched(pkg: String, ts: Long = System.currentTimeMillis()) =
        dao.markLaunched(pkg, ts)

    suspend fun setProfile(pkg: String, profileId: Long) = dao.setProfile(pkg, profileId)

    suspend fun setEnabled(pkg: String, enabled: Boolean) = dao.setEnabled(pkg, enabled)

    private fun GameEntity.toDomain() = Game(
        packageName = packageName, label = label, profileId = profileId,
        enabled = enabled, installedAt = installedAt, lastLaunchedAt = lastLaunchedAt
    )

    private fun Game.toEntity() = GameEntity(
        packageName = packageName, label = label, profileId = profileId,
        enabled = enabled, installedAt = installedAt, lastLaunchedAt = lastLaunchedAt
    )
}
