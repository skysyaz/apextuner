package com.apextuner.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.apextuner.data.db.ApexDatabase
import com.apextuner.data.model.Profile
import com.apextuner.data.repository.ProfileRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ProfileRepositoryTest {

    private lateinit var db: ApexDatabase
    private lateinit var repo: ProfileRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ApexDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = ProfileRepository(db.profileDao())
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `seedBuiltIns inserts three presets`() = runBlocking {
        repo.seedBuiltIns()
        val profiles = collectFirst(repo.observeAll())
        assertThat(profiles).hasSize(3)
        assertThat(profiles.map { it.name })
            .containsExactly("Max Performance", "Balanced", "Power Save")
    }

    @Test
    fun `seedBuiltIns is idempotent`() = runBlocking {
        repo.seedBuiltIns()
        repo.seedBuiltIns()
        assertThat(collectFirst(repo.observeAll())).hasSize(3)
    }

    @Test
    fun `create and getById round-trip`() = runBlocking {
        repo.seedBuiltIns()
        val id = repo.create(Profile(id = 0, name = "Custom", description = "d"))
        val loaded = repo.getById(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("Custom")
    }

    @Test
    fun `update preserves createdAt and bumps updatedAt`() = runBlocking {
        repo.seedBuiltIns()
        val id = repo.create(Profile(id = 0, name = "Upd"))
        val original = repo.getById(id)!!
        Thread.sleep(5)
        repo.update(original.copy(description = "changed"))
        val updated = repo.getById(id)!!
        assertThat(updated.description).isEqualTo("changed")
        assertThat(updated.createdAt).isEqualTo(original.createdAt)
        assertThat(updated.updatedAt).isAtLeast(original.updatedAt)
    }

    @Test
    fun `duplicate copies payload with new name`() = runBlocking {
        repo.seedBuiltIns()
        val id = repo.create(Profile(id = 0, name = "Original"))
        val dupId = repo.duplicate(id, "Copy")
        val dup = repo.getById(dupId)!!
        assertThat(dup.name).isEqualTo("Copy")
        assertThat(dup.id).isNotEqualTo(id)
    }

    @Test
    fun `delete removes user profiles but not built-ins`() = runBlocking {
        repo.seedBuiltIns()
        val id = repo.create(Profile(id = 0, name = "Temp"))
        assertThat(repo.delete(id)).isTrue()
        assertThat(repo.getById(id)).isNull()
        // Built-in ids are -1/-2/-3 in the model but get auto-generated row ids
        // by Room — delete via the actual row id to test the isBuiltIn guard.
        val builtIn = collectFirst(repo.observeAll()).first()
        assertThat(repo.delete(builtIn.id)).isFalse()
    }

    @Test
    fun `export then import restores profiles`() = runBlocking {
        repo.seedBuiltIns()
        val original = collectFirst(repo.observeAll())
        val json = repo.export(original)
        // Decode directly to verify shape
        val decoded = repo.import(json)
        assertThat(decoded).hasSize(original.size)
        assertThat(decoded.map { it.name }).isEqualTo(original.map { it.name })
    }

    // Helper: take one emission from a Flow using runBlocking.
    private fun <T> collectFirst(flow: kotlinx.coroutines.flow.Flow<T>): T = runBlocking {
        var first: T? = null
        flow.collect {
            first = it
            throw kotlinx.coroutines.CancellationException("collected")
        }
        @Suppress("UNCHECKED_CAST")
        first as T
    }
}
