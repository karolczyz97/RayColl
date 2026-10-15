package com.raycoll.app.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.raycoll.app.data.local.prefs.UserPreferencesRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Oddzielny plik i zamknięty zakres na każdy test, bez produkcyjnego delegata Context.dataStore. */
class PreferencesRule : TestRule {
    private val folder = TemporaryFolder().apply { create() }
    private val job = SupervisorJob()
    val repository = UserPreferencesRepository(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(folder.root, "settings.preferences_pb") }
        )
    )

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                runBlocking { job.cancelAndJoin() }
                folder.delete()
            }
        }
    }
}
