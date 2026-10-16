package com.raycoll.app.ui.modedetail

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.RevealStyle
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.steps.ModeTemplate
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.PreferencesRule
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ModeDetailViewModelTest {
    @get:org.junit.Rule val testPreferences = PreferencesRule()

    @Test
    fun `insert below follows the selected key after moving and undo retains the inserted key`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        val original = editing(vm).steps
        val selected = original[1]
        vm.moveStep(1, 0)
        val inserted = ShowPageStep(pageIndex = 4)
        vm.addStep(inserted, afterKey = selected.key)
        val after = editing(vm).steps
        assertEquals(selected.key, after[0].key)
        assertEquals(inserted, after[1].step)
        assertEquals(original.map { it.key }.toSet(), after.filterIndexed { i, _ -> i != 1 }.map { it.key }.toSet())
        vm.removeStep(1)
        vm.undoRemove()
        assertEquals(after, editing(vm).steps)
    }

    @Test
    fun `double save creates one new mode`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        vm.setName("One mode")
        vm.save()
        vm.save()
        advanceUntilIdle()
        assertEquals(1, repos.snapshot().studyModes.size)
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repos: TestRepos

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repos = VmTestHarness.create(ApplicationProvider.getApplicationContext(), FakeClock())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun vm(modeId: String? = null) =
        ModeDetailViewModel(modeId, repos.studyModeRepository, testPreferences.repository)

    @Test
    fun `choosing a template creates steps with stable unique keys`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)

        val state = editing(vm)
        assertTrue(state.steps.isNotEmpty())
        assertEquals(ModeTemplate.CLASSIC.steps, state.steps.map { it.step })

        val ids = state.steps.map { it.key }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `updateStep modyfikuje krok zachowujac stabilne id`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)

        val originalItem = editing(vm).steps[0]
        val originalId = originalItem.key
        val newStep = ShowPageStep(pageIndex = 5)

        vm.updateStep(0, newStep)

        val updatedItem = editing(vm).steps[0]
        assertEquals(originalId, updatedItem.key)
        assertEquals(newStep, updatedItem.step)
        assertEquals(newStep, editing(vm).steps[0].step)
    }

    @Test
    fun `moveStep zamienia kolejnosc zachowujac stabilne id kazdego elementu`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)

        val item0 = editing(vm).steps[0]
        val item1 = editing(vm).steps[1]

        vm.moveStep(0, 1)

        val newStepItems = editing(vm).steps
        assertEquals(item1.key, newStepItems[0].key)
        assertEquals(item0.key, newStepItems[1].key)
    }

    @Test
    fun `adding and removing steps updates the editable list`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.BLANK)
        val initialCount = editing(vm).steps.size

        val extraStep = ShowPageStep(pageIndex = 0)
        vm.addStep(extraStep)

        assertEquals(initialCount + 1, editing(vm).steps.size)
        assertEquals(extraStep, editing(vm).steps.last().step)

        vm.removeStep(editing(vm).steps.lastIndex)
        assertEquals(initialCount, editing(vm).steps.size)
    }

    @Test
    fun `new mode starts with saving disabled until name and steps are valid`() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(ModeDetailUiState.ChoosingTemplate, vm.uiState.value)
        vm.chooseTemplate(ModeTemplate.BLANK)
        val state = vm.uiState.value as ModeDetailUiState.Editing
        assertTrue(state.isNew)
        assertFalse(state.canSave)
        assertTrue(state.issues.any { it.blocking })
    }

    @Test
    fun `operacje z indeksem poza zakresem sa bezpiecznymi no-op`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        val initialSize = editing(vm).steps.size

        // move poza zakres
        vm.moveStep(0, -1)
        assertEquals(initialSize, editing(vm).steps.size)
        vm.moveStep(initialSize - 1, +1)
        assertEquals(initialSize, editing(vm).steps.size)

        // remove poza zakres
        vm.removeStep(-1)
        vm.removeStep(initialSize + 5)
        assertEquals(initialSize, editing(vm).steps.size)

        // update poza zakres
        vm.updateStep(-1, ShowPageStep(pageIndex = 0))
        vm.updateStep(initialSize + 5, ShowPageStep(pageIndex = 0))
        assertEquals(initialSize, editing(vm).steps.size)
    }

    @Test
    fun `invalid new mode cannot save but valid mode persists and emits Closed`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        vm.save()
        advanceUntilIdle()
        assertTrue(repos.snapshot().studyModes.isEmpty())
        vm.setName("My mode")
        vm.save()
        advanceUntilIdle()
        assertEquals(ModeDetailEvent.Closed, vm.events.first())
        val saved = repos.snapshot().studyModes.single()
        assertEquals("My mode", saved.name)
        assertEquals(ModeTemplate.CLASSIC.steps, saved.steps)
        assertFalse(saved.isBuiltIn)
    }

    @Test
    fun `existing mode loads from route and retains its identity when edited`() = runTest(dispatcher) {
        val id = repos.studyModeRepository.addMode("Original", ModeTemplate.CLASSIC.steps)
        val vm = vm(id)
        advanceUntilIdle()
        assertFalse(editing(vm).isNew)
        vm.setName("Renamed")
        vm.save()
        advanceUntilIdle()
        assertEquals(ModeDetailEvent.Closed, vm.events.first())
        assertEquals(id, repos.snapshot().studyModes.single().id)
        assertEquals("Renamed", repos.studyModeRepository.getMode(id)!!.name)
    }

    @Test
    fun `reveal style of a flip step is saved and reopened, also through cloud and backup formats`() =
        runTest(dispatcher) {
            val id = repos.studyModeRepository.addMode("Flip", ModeTemplate.CLASSIC.steps)
            val vm = vm(id)
            advanceUntilIdle()
            val steps = editing(vm).steps
            val flipIndex = steps.indexOfFirst { (it.step as? CompoundStep)?.params is CompoundParams.FlipReveal }
            val flip = steps[flipIndex].step as CompoundStep
            vm.updateStep(flipIndex, flip.copy(params = CompoundParams.FlipReveal(RevealStyle.NEXT)))
            vm.save()
            advanceUntilIdle()

            val saved = repos.studyModeRepository.getMode(id)!!
            val reopened = (vm(id).also { advanceUntilIdle() }.uiState.value as ModeDetailUiState.Editing)
                .steps[flipIndex].step
            assertEquals(CompoundParams.FlipReveal(RevealStyle.NEXT), (reopened as CompoundStep).params)
            // synchronizacja (Firestore) i kopia zapasowa niosą wybrany wariant
            val cloud = FirestoreSchema.modeFromMap(id, FirestoreSchema.modeToMap(saved))
            assertEquals(saved.steps, cloud.steps)
            val backupSteps = repos.snapshot().studyModes.single { it.id == id }.steps
            assertEquals(CompoundParams.FlipReveal(RevealStyle.NEXT), (backupSteps[flipIndex] as CompoundStep).params)
        }

    @Test
    fun `reset built-in restores its original name and steps`() = runTest(dispatcher) {
        repos.seed("en")
        val original = repos.studyModeRepository.getMode("classic")!!
        repos.studyModeRepository.updateMode(original.copy(name = "Edited", steps = listOf(ShowPageStep())))
        val vm = vm("classic")
        advanceUntilIdle()
        vm.resetToBuiltIn()
        advanceUntilIdle()
        assertEquals(ModeDetailEvent.Closed, vm.events.first())
        val reset = repos.studyModeRepository.getMode("classic")!!
        assertEquals(original.name, reset.name)
        assertEquals(original.steps, reset.steps)
    }

    private fun editing(vm: ModeDetailViewModel) = vm.uiState.value as ModeDetailUiState.Editing

    @Test
    fun `dirty tracks changes against the state the editor was opened with`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        assertFalse(editing(vm).dirty)

        vm.setName("Mine")
        assertTrue(editing(vm).dirty)
        vm.setName("")
        assertFalse(editing(vm).dirty)

        vm.removeStep(0)
        assertTrue(editing(vm).dirty)
        vm.undoRemove()
        assertFalse(editing(vm).dirty)

        vm.moveStep(0, 1)
        assertTrue(editing(vm).dirty)
        vm.moveStep(1, 0)
        assertFalse(editing(vm).dirty)
    }

    @Test
    fun `an existing mode is clean until edited`() = runTest(dispatcher) {
        repos.seed("en")
        val vm = vm("classic")
        advanceUntilIdle()
        assertFalse(editing(vm).dirty)
        vm.setName("Classic edited")
        assertTrue(editing(vm).dirty)
    }

    @Test
    fun `undo puts a removed step back at its place with the same key`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        val before = editing(vm).steps
        vm.removeStep(1)
        assertEquals(before.size - 1, editing(vm).steps.size)

        vm.undoRemove()
        assertEquals(before, editing(vm).steps)

        // drugie cofnięcie nic nie robi
        vm.undoRemove()
        assertEquals(before, editing(vm).steps)
    }

    @Test
    fun `undo restores only the last removed step`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        val before = editing(vm).steps
        vm.removeStep(0)
        vm.removeStep(0)
        vm.undoRemove()
        assertEquals(listOf(before[1]) + before.drop(2), editing(vm).steps)
    }
}
