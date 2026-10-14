package com.raycoll.app.ui.modedetail

import androidx.test.core.app.ApplicationProvider
import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.RevealStyle
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.steps.ModeTemplate
import com.raycoll.app.testing.FakeClock
import com.raycoll.app.testing.TestRepos
import com.raycoll.app.testing.VmTestHarness
import com.raycoll.app.testing.routeArgs
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

    @Test
    fun `double save creates one new mode`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        vm.setName("One mode")
        vm.save()
        vm.save()
        advanceUntilIdle()
        assertEquals(1, repos.localStore.snapshot().studyModes.size)
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
        repos.db.close()
    }

    private fun vm(modeId: String? = null) =
        ModeDetailViewModel(routeArgs("modeId" to modeId), repos.studyModeRepository)

    @Test
    fun `choosing a template creates steps with stable unique keys`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)

        val state = (vm.uiState.value as ModeDetailUiState.Editing)
        assertTrue(state.steps.isNotEmpty())
        assertEquals(ModeTemplate.CLASSIC.steps, state.steps.map { it.step })

        val ids = state.steps.map { it.key }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `updateStep modyfikuje krok zachowujac stabilne id`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)

        val originalItem = (vm.uiState.value as ModeDetailUiState.Editing).steps[0]
        val originalId = originalItem.key
        val newStep = ShowPageStep(pageIndex = 5)

        vm.updateStep(0, newStep)

        val updatedItem = (vm.uiState.value as ModeDetailUiState.Editing).steps[0]
        assertEquals(originalId, updatedItem.key)
        assertEquals(newStep, updatedItem.step)
        assertEquals(newStep, (vm.uiState.value as ModeDetailUiState.Editing).steps[0].step)
    }

    @Test
    fun `moveStep zamienia kolejnosc zachowujac stabilne id kazdego elementu`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)

        val item0 = (vm.uiState.value as ModeDetailUiState.Editing).steps[0]
        val item1 = (vm.uiState.value as ModeDetailUiState.Editing).steps[1]

        vm.moveStep(0, 1)

        val newStepItems = (vm.uiState.value as ModeDetailUiState.Editing).steps
        assertEquals(item1.key, newStepItems[0].key)
        assertEquals(item0.key, newStepItems[1].key)
    }

    @Test
    fun `adding and removing steps updates the editable list`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.BLANK)
        val initialCount = (vm.uiState.value as ModeDetailUiState.Editing).steps.size

        val extraStep = ShowPageStep(pageIndex = 0)
        vm.addStep(extraStep)

        assertEquals(initialCount + 1, (vm.uiState.value as ModeDetailUiState.Editing).steps.size)
        assertEquals(extraStep, (vm.uiState.value as ModeDetailUiState.Editing).steps.last().step)

        vm.removeStep((vm.uiState.value as ModeDetailUiState.Editing).steps.lastIndex)
        assertEquals(initialCount, (vm.uiState.value as ModeDetailUiState.Editing).steps.size)
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
        val initialSize = (vm.uiState.value as ModeDetailUiState.Editing).steps.size

        // move poza zakres
        vm.moveStep(0, -1)
        assertEquals(initialSize, (vm.uiState.value as ModeDetailUiState.Editing).steps.size)
        vm.moveStep(initialSize - 1, +1)
        assertEquals(initialSize, (vm.uiState.value as ModeDetailUiState.Editing).steps.size)

        // remove poza zakres
        vm.removeStep(-1)
        vm.removeStep(initialSize + 5)
        assertEquals(initialSize, (vm.uiState.value as ModeDetailUiState.Editing).steps.size)

        // update poza zakres
        vm.updateStep(-1, ShowPageStep(pageIndex = 0))
        vm.updateStep(initialSize + 5, ShowPageStep(pageIndex = 0))
        assertEquals(initialSize, (vm.uiState.value as ModeDetailUiState.Editing).steps.size)
    }

    @Test
    fun `invalid new mode cannot save but valid mode persists and emits Closed`() = runTest(dispatcher) {
        val vm = vm()
        vm.chooseTemplate(ModeTemplate.CLASSIC)
        vm.save()
        advanceUntilIdle()
        assertTrue(repos.localStore.snapshot().studyModes.isEmpty())
        vm.setName("My mode")
        vm.save()
        advanceUntilIdle()
        assertEquals(ModeDetailEvent.Closed, vm.events.first())
        val saved = repos.localStore.snapshot().studyModes.single()
        assertEquals("My mode", saved.name)
        assertEquals(ModeTemplate.CLASSIC.steps, saved.steps)
        assertFalse(saved.isBuiltIn)
    }

    @Test
    fun `existing mode loads from route and retains its identity when edited`() = runTest(dispatcher) {
        val id = repos.studyModeRepository.addMode("Original", ModeTemplate.CLASSIC.steps)
        val vm = vm(id)
        advanceUntilIdle()
        assertFalse((vm.uiState.value as ModeDetailUiState.Editing).isNew)
        vm.setName("Renamed")
        vm.save()
        advanceUntilIdle()
        assertEquals(ModeDetailEvent.Closed, vm.events.first())
        assertEquals(id, repos.localStore.snapshot().studyModes.single().id)
        assertEquals("Renamed", repos.studyModeRepository.getMode(id)!!.name)
    }

    @Test
    fun `reveal style of a flip step is saved and reopened, also through cloud and backup formats`() =
        runTest(dispatcher) {
            val id = repos.studyModeRepository.addMode("Flip", ModeTemplate.CLASSIC.steps)
            val vm = vm(id)
            advanceUntilIdle()
            val steps = (vm.uiState.value as ModeDetailUiState.Editing).steps
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
            val backupSteps = repos.localStore.snapshot().studyModes.single { it.id == id }.steps
            assertEquals(CompoundParams.FlipReveal(RevealStyle.NEXT), (backupSteps[flipIndex] as CompoundStep).params)
        }

    @Test
    fun `reset built-in restores its original name and steps`() = runTest(dispatcher) {
        repos.localStore.seedIfEmpty("en")
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
}
