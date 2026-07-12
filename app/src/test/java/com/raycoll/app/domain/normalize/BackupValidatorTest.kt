package com.raycoll.app.domain.normalize

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port testów BackupValidator z "RayColl - Kopie" (spec = backupValidation.ts). */
class BackupValidatorTest {

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
}
