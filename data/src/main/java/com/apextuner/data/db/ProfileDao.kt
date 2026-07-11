package com.apextuner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.apextuner.data.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProfileEntity): Long

    @Update
    suspend fun update(entity: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM profiles WHERE isBuiltIn = 1")
    suspend fun deleteBuiltIns(): Int

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}
