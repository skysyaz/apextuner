package com.apextuner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.apextuner.data.db.entity.GameEntity
import com.apextuner.data.db.entity.ProfileEntity
import com.apextuner.data.db.entity.TunerLogEntity

@TypeConverters
class ApexTypeConverters {
    @TypeConverter
    fun booleanToInt(value: Boolean): Int = if (value) 1 else 0

    @TypeConverter
    fun intToBoolean(value: Int): Boolean = value != 0
}

@Database(
    entities = [ProfileEntity::class, GameEntity::class, TunerLogEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(ApexTypeConverters::class)
abstract class ApexDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun gameDao(): GameDao
    abstract fun logDao(): LogDao

    companion object {
        const val DB_NAME = "apextuner.db"
    }
}
