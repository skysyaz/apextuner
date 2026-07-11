package com.apextuner.data.repository

import com.apextuner.data.db.ProfileDao
import com.apextuner.data.db.entity.ProfileEntity
import com.apextuner.data.model.Profile
import com.apextuner.data.model.ProfileSerializer
import com.apextuner.data.model.ProfileJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for [Profile] objects. Hides the JSON-in-Room storage
 * shape from the rest of the app and seeds the three built-in presets
 * (Max Performance / Balanced / Power Save) on first run.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao
) {
    fun observeAll(): Flow<List<Profile>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun getById(id: Long): Profile? = dao.getById(id)?.toDomain()

    suspend fun getByName(name: String): Profile? = dao.getByName(name)?.toDomain()

    suspend fun count(): Int = dao.count()

    /**
     * Inserts a new profile. Returns the assigned row id. Throws if the name
     * already exists (Room unique-index violation) — callers should catch and
     * surface a user-facing message.
     */
    suspend fun create(profile: Profile): Long {
        val now = System.currentTimeMillis()
        val entity = profile.copy(createdAt = now, updatedAt = now).toEntity(autoId = true)
        return dao.insert(entity)
    }

    suspend fun update(profile: Profile) {
        require(profile.id > 0) { "Cannot update profile without id: $profile" }
        val existing = dao.getById(profile.id)
            ?: error("Profile ${profile.id} not found")
        val updated = profile.copy(
            createdAt = existing.createdAt,
            updatedAt = System.currentTimeMillis()
        ).toEntity(autoId = false, existingId = existing.id, isBuiltIn = existing.isBuiltIn)
        dao.update(updated)
    }

    suspend fun duplicate(id: Long, newName: String): Long {
        val src = dao.getById(id)?.toDomain()
            ?: error("Source profile $id not found")
        val copy = src.copy(
            id = 0L,
            name = newName,
            description = "Copy of ${src.name}",
            createdAt = 0L,
            updatedAt = 0L
        )
        return create(copy)
    }

    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    /** Ensures the three built-in presets exist. Safe to call on every startup. */
    suspend fun seedBuiltIns() {
        if (dao.count() > 0) return
        builtIns().forEach { profile ->
            val now = System.currentTimeMillis()
            dao.insert(
                ProfileEntity(
                    id = 0L,
                    name = profile.name,
                    description = profile.description,
                    iconKey = profile.iconKey,
                    thermalPolicy = profile.thermalPolicy.name,
                    isBuiltIn = true,
                    createdAt = now,
                    updatedAt = now,
                    payload = ProfileSerializer.encode(profile)
                )
            )
        }
    }

    fun export(profiles: List<Profile>): String = ProfileJson.encodeList(profiles)

    fun import(payload: String): List<Profile> = ProfileJson.decodeList(payload)

    // ---- mappers ----

    private fun ProfileEntity.toDomain(): Profile =
        ProfileSerializer.decode(payload).copy(id = id)

    private fun Profile.toEntity(autoId: Boolean, existingId: Long = 0L, isBuiltIn: Boolean = false): ProfileEntity =
        ProfileEntity(
            id = if (autoId) 0L else existingId,
            name = name,
            description = description,
            iconKey = iconKey,
            thermalPolicy = thermalPolicy.name,
            isBuiltIn = isBuiltIn,
            createdAt = createdAt,
            updatedAt = updatedAt,
            payload = ProfileSerializer.encode(this)
        )

    private fun builtIns(): List<Profile> = listOf(
        Profile(
            id = Profile.DEFAULT_ID_MAX_PERFORMANCE,
            name = "Max Performance",
            description = "All cores online, performance governor, peak GPU clock, highest refresh rate.",
            iconKey = "rocket",
            thermalPolicy = Profile.ThermalPolicy.MAX_PERFORMANCE,
            display = com.apextuner.data.model.DisplayConfig(
                modeId = -1, refreshRateHz = 120f, forcePeakHz = true, adaptive = false, batterySaverHz = false
            )
        ),
        Profile(
            id = Profile.DEFAULT_ID_BALANCED,
            name = "Balanced",
            description = "schedutil governor, sane frequency limits, adaptive refresh rate.",
            iconKey = "scale",
            thermalPolicy = Profile.ThermalPolicy.BALANCED
        ),
        Profile(
            id = Profile.DEFAULT_ID_POWER_SAVE,
            name = "Power Save",
            description = "conservative governor, little-core-only option, low max frequency.",
            iconKey = "battery",
            thermalPolicy = Profile.ThermalPolicy.POWER_SAVE
        )
    )
}
