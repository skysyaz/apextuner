package com.apextuner.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tuner_logs",
    indices = [Index(value = ["timestamp"]), Index(value = ["category"])]
)
data class TunerLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val timestamp: Long,
    val level: String,
    val category: String,
    val message: String,
    val detail: String?
)
