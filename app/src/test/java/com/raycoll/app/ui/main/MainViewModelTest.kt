package com.raycoll.app.ui.main

import androidx.lifecycle.viewModelScope
import com.raycoll.app.data.sync.MigrationOption
import com.raycoll.app.data.sync.SyncLifecycle
import com.raycoll.app.testing.PreferencesRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    @get:Rule val preferences = PreferencesRule()
    private val main = StandardTestDispatcher()
    private val sync = mock(SyncLifecycle::class.java)
    private lateinit var vm: MainViewModel

    @Before fun setup() {
        Dispatchers.setMain(main)
        `when`(sync.migrationRequired).thenReturn(MutableStateFlow(true))
        vm = MainViewModel(preferences.repository, sync)
    }

    @After fun close() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun `double click starts one migration and a failed migration can be retried`() = runTest(main) {
        `when`(sync.resolveMigration(MigrationOption.MIGRATE)).thenReturn(false)
        vm.resolveMigration(MigrationOption.MIGRATE)
        vm.resolveMigration(MigrationOption.FRESH)
        assertTrue(vm.migrating.value)
        runCurrent()
        assertFalse(vm.migrating.value)
        assertTrue(vm.migrationFailed.value)
        verify(sync).resolveMigration(MigrationOption.MIGRATE)
        verify(sync, never()).resolveMigration(MigrationOption.FRESH)
        `when`(sync.resolveMigration(MigrationOption.MIGRATE)).thenReturn(true)
        vm.resolveMigration(MigrationOption.MIGRATE)
        runCurrent()
        assertFalse(vm.migrationFailed.value)
    }
}
