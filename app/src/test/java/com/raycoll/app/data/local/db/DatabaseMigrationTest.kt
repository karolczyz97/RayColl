package com.raycoll.app.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {
    @Test
    fun `version one migrates without losing either namespace or tombstones`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-1-2.db"
        context.deleteDatabase(name)
        val schemaText = javaClass.getResourceAsStream("/com.raycoll.app.data.local.db.RayCollDatabase/1.json")!!
            .bufferedReader().use { it.readText() }
        val schema = JSONObject(schemaText).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            for (ns in listOf("", "uid")) {
                old.execSQL(
                    "INSERT INTO decks VALUES ('deck', ?, 'Saved deck', 'classic', 'all', 'sequential', '[]', '[]', 2, 42, 50, 30)",
                    arrayOf(ns)
                )
                old.execSQL(
                    "INSERT INTO cards VALUES ('card', ?, 'deck', '[\"front\",\"back\"]', 5.0, 3.0, 4, 2, 10, 20, 30, 40, 50)",
                    arrayOf(ns)
                )
                old.execSQL(
                    "INSERT INTO study_modes VALUES ('custom', ?, 'Saved mode', '[]', 0, NULL, 42, 50)",
                    arrayOf(ns)
                )
                old.execSQL("INSERT INTO activity_days VALUES ('2026-10-14', ?, 7)", arrayOf(ns))
            }
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, RayCollDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            for (ns in listOf("", "uid")) {
                val deck = migrated.deckDao().allDecks(ns).single()
                assertEquals("Saved deck", deck.name)
                assertEquals(50L, deck.deletedAt)
                assertEquals(30L, deck.archivedAt)
                val card = migrated.cardDao().allCards(ns).single()
                assertEquals(4, card.repetitions)
                assertEquals(50L, card.deletedAt)
                assertEquals("Saved mode", migrated.studyModeDao().allModes(ns).single().name)
                assertEquals(7, migrated.activityDao().allActivity(ns).single().count)
                assertNull(migrated.namespaceStateDao().get(ns))
                migrated.namespaceStateDao().upsert(NamespaceStateEntity(ns, 123, "reset"))
                assertEquals(123L, migrated.namespaceStateDao().get(ns)!!.replacementAt)
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
