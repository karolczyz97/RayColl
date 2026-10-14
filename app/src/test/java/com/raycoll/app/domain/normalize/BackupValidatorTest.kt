package com.raycoll.app.domain.normalize

import com.raycoll.app.data.remote.firestore.FirestoreSchema
import com.raycoll.app.domain.model.AutoRateFixedStep
import com.raycoll.app.domain.model.AutoRateFromAnswerStep
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.DynamicPauseStep
import com.raycoll.app.domain.model.FeedbackErrorStep
import com.raycoll.app.domain.model.FeedbackSuccessStep
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.ListenAndCheckStep
import com.raycoll.app.domain.model.MarkFailedStep
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.NextCardStep
import com.raycoll.app.domain.model.ShowAllPagesStep
import com.raycoll.app.domain.model.ShowPageStep
import com.raycoll.app.domain.model.ShowRatingsStep
import com.raycoll.app.domain.model.SpeakAllPagesStep
import com.raycoll.app.domain.model.SpeakPageStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.WaitForTapStep
import com.raycoll.app.domain.model.WaitForTapToRevealNextStep
import com.raycoll.app.domain.model.WaitForTapToRevealStep
import com.raycoll.app.domain.model.WaitStep
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Walidacja kopii zapasowych przed zastąpieniem danych użytkownika. */
class BackupValidatorTest {

    @Test
    fun `parameterless compound steps read legacy dummy fields in backups and cloud`() {
        val json = Json { ignoreUnknownKeys = true }
        for ((kind, params) in listOf(
            "show_all_grade" to CompoundParams.ShowAllGrade,
            "fail_next" to CompoundParams.FailNext
        )) {
            val legacy = """{"type":"compound","params":{"kind":"$kind","dummy":0}}"""
            val step = json.decodeFromString(ModeStep.serializer(), legacy) as CompoundStep
            assertSame(params, step.params)
            val encoded = json.encodeToString(ModeStep.serializer(), step)
            assertFalse(encoded.contains("dummy"))
            assertEquals(step, json.decodeFromString(ModeStep.serializer(), encoded))

            val mode = StudyMode(id = "legacy", name = "Legacy", steps = listOf(step))
            val cloud = FirestoreSchema.modeToMap(mode) + (
                "steps" to listOf(
                    mapOf("type" to "compound", "condition" to null, "params" to mapOf("kind" to kind, "dummy" to 0))
                )
                )
            validateBackupData(backup(modes = listOf(cloud)))
            assertEquals(mode, FirestoreSchema.modeFromMap(mode.id, cloud))
        }
    }

    private fun group(id: String = "deck", cardId: String = "card") = mapOf(
        "id" to id,
        "name" to "Deck",
        "cards" to
            listOf(FirestoreSchema.cardToMap(Flashcard(id = cardId, pages = listOf("q", "a"), srsState = SrsState()))),
        "pageNames" to listOf("A", "B"),
        "pageLanguages" to listOf("en-US", "pl-PL"),
        "activeModeId" to "classic"
    )

    private fun backup(
        groups: List<Any> = listOf(group()),
        modes: List<Any> = emptyList(),
        days: Map<String, Int> = emptyMap()
    ) = mapOf("groups" to groups, "studyModes" to modes, "activityHeatmap" to days)

    @Test
    fun `invalid document IDs are rejected at every backup boundary`() {
        for (id in listOf("", "a/b", ".", "..")) {
            val mode = FirestoreSchema.modeToMap(StudyMode(id = id, name = "Mode"))
            for (data in listOf(
                backup(groups = listOf(group(id))),
                backup(groups = listOf(group(cardId = id))),
                backup(modes = listOf(mode)),
                backup(days = mapOf(id to 1))
            )) {
                assertThrows(BackupValidationException::class.java) { validateBackupData(data) }
            }
        }
    }

    @Test
    fun `duplicate deck mode and globally duplicate card IDs reject the whole backup`() {
        val mode = FirestoreSchema.modeToMap(StudyMode(id = "mode", name = "Mode"))
        for (data in listOf(
            backup(groups = listOf(group("deck", "a"), group("deck", "b"))),
            backup(groups = listOf(group("a", "same-card"), group("b", "same-card"))),
            backup(modes = listOf(mode, mode))
        )) {
            assertThrows(BackupValidationException::class.java) { validateBackupData(data) }
        }
    }

    @Test
    fun `all editable steps survive backup validation and cloud round trip`() {
        val steps = listOf(
            ShowPageStep(), ShowAllPagesStep(), WaitForTapToRevealNextStep(), WaitForTapToRevealStep(),
            ShowRatingsStep(), SpeakPageStep(), SpeakAllPagesStep(), DynamicPauseStep(), WaitStep(),
            ListenAndCheckStep(), FeedbackSuccessStep(), FeedbackErrorStep(), AutoRateFromAnswerStep(),
            AutoRateFixedStep(), MarkFailedStep(), WaitForTapStep(), NextCardStep(),
            CompoundStep(params = CompoundParams.PresentFront()),
            CompoundStep(params = CompoundParams.FlipReveal()),
            CompoundStep(params = CompoundParams.ShowAllGrade),
            CompoundStep(params = CompoundParams.SpeakPauseNext()),
            CompoundStep(params = CompoundParams.AutoFlip()),
            CompoundStep(params = CompoundParams.ListenGrade()),
            CompoundStep(params = CompoundParams.GradeAfterListen()),
            CompoundStep(params = CompoundParams.AutoPassNext()),
            CompoundStep(params = CompoundParams.FailNext)
        )
        val mode = StudyMode(id = "all-steps", name = "All steps", steps = steps, updatedAt = 1L)
        val plain = FirestoreSchema.modeToMap(mode)
        val plainSteps = plain["steps"] as List<*>
        val stepTypes = ModeStep.serializer().descriptor.getElementDescriptor(1)
        assertEquals(
            (0 until stepTypes.elementsCount).map { stepTypes.getElementName(it) }.toSet(),
            plainSteps.map { (it as Map<*, *>)["type"] }.toSet()
        )
        val compoundTypes = CompoundParams.serializer().descriptor.getElementDescriptor(1)
        assertEquals(
            (0 until compoundTypes.elementsCount).map { compoundTypes.getElementName(it) }.toSet(),
            plainSteps.mapNotNull { ((it as Map<*, *>)["params"] as? Map<*, *>)?.get("kind") }.toSet()
        )
        validateBackupData(
            mapOf(
                "groups" to emptyList<Any>(),
                "studyModes" to listOf(plain),
                "activityHeatmap" to emptyMap<String, Int>()
            )
        )
        assertEquals(mode, FirestoreSchema.modeFromMap(mode.id, plain))
    }

    @Test
    fun `accepts valid backup`() {
        val validBackup = """
            {
                "groups": [
                    {
                        "id": "g_backup",
                        "name": "Backup Group",
                        "cards": [
                            {
                                "id": "c_backup",
                                "pages": ["A", "B"],
                                "srsState": {
                                    "difficulty": 5.0,
                                    "stability": 0.0,
                                    "repetitions": 0,
                                    "state": 0,
                                    "lastReviewTimestamp": 0,
                                    "nextReviewTimestamp": 0
                                },
                                "contentUpdatedAt": 0,
                                "srsUpdatedAt": 0
                            }
                        ],
                        "pageLanguages": ["en-US", "pl-PL"],
                        "pageNames": ["Front", "Back"],
                        "activeModeId": "classic",
                        "activePageCount": 2,
                        "updatedAt": 0
                    }
                ],
                "studyModes": [
                    {
                        "id": "classic",
                        "name": "Classic",
                        "steps": [
                            { "type": "show_page", "pageIndex": 0 }
                        ],
                        "isBuiltIn": true,
                        "builtInSourceId": "classic",
                        "updatedAt": 0
                    }
                ],
                "activityHeatmap": {
                    "2026-05-26": 1
                }
            }
        """.trimIndent()

        // nie może rzucić
        validateBackupJson(validBackup)
    }

    @Test
    fun `throws on malformed json`() {
        assertThrows(BackupValidationException::class.java) { validateBackupJson("null") }
        assertThrows(BackupValidationException::class.java) { validateBackupJson("") }
        assertThrows(BackupValidationException::class.java) { validateBackupJson("{not json") }
    }

    @Test
    fun `throws on missing groups`() {
        val json = """{ "studyModes": [], "activityHeatmap": {} }"""
        assertThrows(BackupValidationException::class.java) { validateBackupJson(json) }
    }

    @Test
    fun `throws on missing studyModes`() {
        val json = """{ "groups": [], "activityHeatmap": {} }"""
        assertThrows(BackupValidationException::class.java) { validateBackupJson(json) }
    }

    @Test
    fun `throws on missing activityHeatmap`() {
        val json = """{ "groups": [], "studyModes": [] }"""
        assertThrows(BackupValidationException::class.java) { validateBackupJson(json) }
    }

    @Test
    fun `throws on null groups entry`() {
        val json = """{ "groups": [null], "studyModes": [], "activityHeatmap": {} }"""
        assertThrows(BackupValidationException::class.java) { validateBackupJson(json) }
    }

    @Test
    fun `throws on non-string group id`() {
        val json = """
            {
                "groups": [
                    {
                        "id": 123,
                        "name": "Test",
                        "cards": [],
                        "pageLanguages": [],
                        "pageNames": [],
                        "activeModeId": "classic",
                        "updatedAt": 0
                    }
                ],
                "studyModes": [],
                "activityHeatmap": {}
            }
        """.trimIndent()
        assertThrows(BackupValidationException::class.java) { validateBackupJson(json) }
    }

    @Test
    fun `throws on invalid study mode step`() {
        val json = """
            {
                "groups": [],
                "studyModes": [
                    {
                        "id": "broken",
                        "name": "Broken",
                        "steps": [
                            { "type": "show_page" }
                        ],
                        "updatedAt": 0
                    }
                ],
                "activityHeatmap": {}
            }
        """.trimIndent()
        val exception = assertThrows(BackupValidationException::class.java) { validateBackupJson(json) }
        assertTrue(exception.message!!.contains("invalid pageIndex"))
    }

    @Test
    fun `throws on negative activity heatmap entry`() {
        val json = """{ "groups": [], "studyModes": [], "activityHeatmap": { "2026-05-26": -2 } }"""
        assertThrows(BackupValidationException::class.java) { validateBackupJson(json) }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `compound pause with type instead of kind is rejected`() {
        val mode = StudyMode(
            id = "m",
            name = "M",
            steps = listOf(CompoundStep(params = CompoundParams.ListenGrade())),
            updatedAt = 1L
        )
        val plain = FirestoreSchema.modeToMap(mode).toMutableMap()
        val step = ((plain["steps"] as List<*>)[0] as Map<String, Any?>).toMutableMap()
        val params = (step["params"] as Map<String, Any?>).toMutableMap()
        val onCorrect = (params["onCorrect"] as Map<String, Any?>).toMutableMap()
        val pause = (onCorrect["pause"] as Map<String, Any?>).toMutableMap()
        pause["type"] = pause.remove("kind")
        onCorrect["pause"] = pause
        params["onCorrect"] = onCorrect
        step["params"] = params
        plain["steps"] = listOf(step)
        assertThrows(BackupValidationException::class.java) {
            validateBackupData(
                mapOf(
                    "groups" to emptyList<Any>(),
                    "studyModes" to listOf(plain),
                    "activityHeatmap" to emptyMap<String, Int>()
                )
            )
        }
    }
}
