package com.apextuner.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val profileId: Long,
    val enabled: Boolean,
    val installedAt: Long,
    val lastLaunchedAt: Long
)
