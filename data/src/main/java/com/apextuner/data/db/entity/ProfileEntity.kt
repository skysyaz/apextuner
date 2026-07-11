package com.apextuner.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stored form of [com.apextuner.data.model.Profile]. The full profile graph is
 * serialized to JSON (see [com.apextuner.data.model.ProfileSerializer]) and stored in
 * [payload] — this keeps schema migrations trivial and import/export lossless.
 */
@Entity(
    tableName = "profiles",
    indices = [Index(value = ["name"], unique = true)]
)
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val iconKey: String,
    val thermalPolicy: String,
    val isBuiltIn: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val payload: String                  // JSON of [Profile]
)
