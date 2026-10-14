package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.StudyConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class BackupValidationException(message: String) : Exception(message)

// Walidacja surowej struktury kopii zapasowej, zanim cokolwiek zostanie zapisane:
// błędny plik ma zostać odrzucony w całości, a nie wczytany częściowo.

fun validateBackupJson(jsonText: String) {
    val element = try {
        Json.parseToJsonElement(jsonText)
    } catch (e: Exception) {
        throw BackupValidationException("Backup data is not a valid JSON object.")
    }
    validateBackupData(jsonElementToPlain(element))
}

/** JSON → zwykłe struktury Kotlina (Map/List/String/Long/Double/Boolean/null). */
internal fun jsonElementToPlain(element: JsonElement): Any? = when (element) {
    is JsonNull -> null

    is JsonPrimitive -> when {
        element.isString -> element.content
        element.content == "true" -> true
        element.content == "false" -> false
        else -> element.content.toLongOrNull() ?: element.content.toDoubleOrNull() ?: element.content
    }

    is JsonArray -> element.map(::jsonElementToPlain)

    is JsonObject -> element.mapValues { jsonElementToPlain(it.value) }
}

private fun stepMessage(modeId: String, index: Int, problem: String) =
    "Step ${index + 1} in study mode $modeId $problem."

private fun assertNumber(value: Any?, message: String) {
    if (value !is Number || !value.toDouble().isFinite()) throw BackupValidationException(message)
}

private fun assertNonNegativeNumber(value: Any?, message: String) {
    assertNumber(value, message)
    if ((value as Number).toDouble() < 0) throw BackupValidationException(message)
}

private fun assertBoolean(value: Any?, message: String) {
    if (value !is Boolean) throw BackupValidationException(message)
}

private fun assertDocumentId(value: Any?) {
    if (value !is String || value.isEmpty() || '/' in value || value == "." || value == "..") {
        throw BackupValidationException("Invalid document ID: $value.")
    }
}

private fun assertUniqueIds(items: List<*>, kind: String) {
    val ids = items.map { (it as Map<*, *>)["id"] }
    if (ids.distinct().size != ids.size) throw BackupValidationException("Duplicate $kind IDs.")
}

private fun assertOptionalPage(value: Any?, message: String) {
    if (value != null) assertNonNegativeNumber(value, message)
}

private fun assertCompoundPause(value: Any?, modeId: String, index: Int) {
    if (value == null) return
    if (value !is Map<*, *>) throw BackupValidationException(stepMessage(modeId, index, "has invalid compound pause"))
    val kind = value["kind"]
    when (kind) {
        "fixed" -> assertNonNegativeNumber(value["ms"], stepMessage(modeId, index, "has invalid compound pause"))

        "dynamic" -> {
            assertNonNegativeNumber(value["page"], stepMessage(modeId, index, "has invalid compound pause"))
            assertNonNegativeNumber(value["multiplier"], stepMessage(modeId, index, "has invalid compound pause"))
        }

        else -> throw BackupValidationException(stepMessage(modeId, index, "has invalid compound pause"))
    }
}

private fun assertCompoundBranch(value: Any?, modeId: String, index: Int) {
    if (value !is Map<*, *>) throw BackupValidationException(stepMessage(modeId, index, "has invalid compound branch"))
    assertBoolean(value["feedback"], stepMessage(modeId, index, "has invalid compound branch"))
    assertOptionalPage(value["speakPage"], stepMessage(modeId, index, "has invalid compound branch"))
    assertCompoundPause(value["pause"], modeId, index)
    assertBoolean(value["revealAll"], stepMessage(modeId, index, "has invalid compound branch"))
    assertBoolean(value["markFailed"], stepMessage(modeId, index, "has invalid compound branch"))
    val rate = value["rate"]
    if (rate != "fromAnswer" && rate != "fixed" && rate != "none") {
        throw BackupValidationException(stepMessage(modeId, index, "has invalid compound branch"))
    }
    assertNonNegativeNumber(value["fixedRating"], stepMessage(modeId, index, "has invalid compound branch"))
    assertBoolean(value["advance"], stepMessage(modeId, index, "has invalid compound branch"))
}

private fun assertCompoundParams(params: Any?, modeId: String, index: Int) {
    if (params !is Map<*, *>) throw BackupValidationException(stepMessage(modeId, index, "has invalid compound params"))
    val kind = params["kind"]
    when (kind) {
        "present_front" -> {
            assertNonNegativeNumber(params["page"], stepMessage(modeId, index, "has invalid pageIndex"))
            assertBoolean(params["speak"], stepMessage(modeId, index, "has invalid compound params"))
        }

        "flip_reveal" -> {
            val rs = params["revealStyle"]
            if (rs != "all" &&
                rs != "next"
            ) {
                throw BackupValidationException(stepMessage(modeId, index, "has invalid compound params"))
            }
        }

        "show_all_grade", "fail_next" -> {}

        "speak_pause_next" -> {
            assertNonNegativeNumber(params["page"], stepMessage(modeId, index, "has invalid pageIndex"))
            assertNonNegativeNumber(params["nextPage"], stepMessage(modeId, index, "has invalid nextPageIndex"))
            assertNonNegativeNumber(params["multiplier"], stepMessage(modeId, index, "has invalid pauseMultiplier"))
        }

        "auto_flip" -> {
            assertNonNegativeNumber(params["questionPage"], stepMessage(modeId, index, "has invalid pageIndex"))
            assertNonNegativeNumber(params["answerPage"], stepMessage(modeId, index, "has invalid pageIndex"))
            assertNonNegativeNumber(params["multiplier"], stepMessage(modeId, index, "has invalid pauseMultiplier"))
            assertBoolean(params["speakQuestion"], stepMessage(modeId, index, "has invalid compound params"))
            assertBoolean(params["speakAnswer"], stepMessage(modeId, index, "has invalid compound params"))
        }

        "listen_grade" -> {
            assertNonNegativeNumber(params["answerPage"], stepMessage(modeId, index, "has invalid pageIndex"))
            assertNonNegativeNumber(params["threshold"], stepMessage(modeId, index, "has invalid successThreshold"))
            if ((params["threshold"] as Number).toInt() > StudyConstants.MAX_THRESHOLD_PERCENT) {
                throw BackupValidationException(stepMessage(modeId, index, "has invalid successThreshold"))
            }
            assertCompoundBranch(params["onCorrect"], modeId, index)
            assertCompoundBranch(params["onWrong"], modeId, index)
            assertBoolean(params["manualFallback"], stepMessage(modeId, index, "has invalid compound params"))
        }

        "grade_after_listen" -> {
            assertCompoundBranch(params["onCorrect"], modeId, index)
            assertCompoundBranch(params["onWrong"], modeId, index)
            assertBoolean(params["manualFallback"], stepMessage(modeId, index, "has invalid compound params"))
        }

        "auto_pass_next" -> assertNonNegativeNumber(params["rating"], stepMessage(modeId, index, "has invalid rating"))

        else -> throw BackupValidationException(stepMessage(modeId, index, "has invalid compound params"))
    }
}

fun assertStudyModeStep(step: Any?, modeId: String, index: Int) {
    if (step !is Map<*, *>) throw BackupValidationException(stepMessage(modeId, index, "must be a valid object"))
    when (step["type"]) {
        "show_page" -> assertNonNegativeNumber(step["pageIndex"], stepMessage(modeId, index, "has invalid pageIndex"))

        "speak_page" -> assertNonNegativeNumber(step["pageIndex"], stepMessage(modeId, index, "has invalid pageIndex"))

        "dynamic_pause" -> {
            assertNonNegativeNumber(step["nextPageIndex"], stepMessage(modeId, index, "has invalid nextPageIndex"))
            if (step["pauseMultiplier"] !=
                null
            ) {
                assertNonNegativeNumber(
                    step["pauseMultiplier"],
                    stepMessage(modeId, index, "has invalid pauseMultiplier")
                )
            }
        }

        "wait" -> assertNonNegativeNumber(step["ms"], stepMessage(modeId, index, "has invalid ms"))

        "listen_and_check" -> {
            assertNonNegativeNumber(step["pageIndex"], stepMessage(modeId, index, "has invalid pageIndex"))
            assertNonNegativeNumber(
                step["successThreshold"],
                stepMessage(modeId, index, "has invalid successThreshold")
            )
            if ((step["successThreshold"] as Number).toInt() > StudyConstants.MAX_THRESHOLD_PERCENT) {
                throw BackupValidationException(stepMessage(modeId, index, "has invalid successThreshold"))
            }
        }

        "auto_rate_fixed" -> assertNonNegativeNumber(step["rating"], stepMessage(modeId, index, "has invalid rating"))

        "compound" -> {
            if (step["version"] !=
                null
            ) {
                assertNumber(step["version"], stepMessage(modeId, index, "has invalid compound version"))
            }
            assertCompoundParams(step["params"], modeId, index)
        }

        "show_all_pages", "wait_for_tap_to_reveal_next", "wait_for_tap_to_reveal",
        "show_ratings", "feedback_success", "feedback_error",
        "auto_rate_from_answer", "mark_failed", "next_card", "wait_for_tap", "speak_all_pages" -> {}

        else -> throw BackupValidationException(stepMessage(modeId, index, "has an unsupported type"))
    }
}

fun validateSrsState(cardId: String, groupName: String, srsState: Any?) {
    if (srsState !is Map<*, *>) {
        throw BackupValidationException(
            "Card $cardId in group $groupName is missing a valid \"srsState\" object."
        )
    }
    assertNumber(srsState["difficulty"], "Card $cardId in group $groupName has an invalid difficulty.")
    assertNumber(srsState["stability"], "Card $cardId in group $groupName has an invalid stability.")
    assertNumber(srsState["repetitions"], "Card $cardId in group $groupName has invalid repetitions.")
    assertNumber(srsState["state"], "Card $cardId in group $groupName has invalid state.")
    assertNumber(
        srsState["lastReviewTimestamp"],
        "Card $cardId in group $groupName has an invalid last review timestamp."
    )
    assertNumber(
        srsState["nextReviewTimestamp"],
        "Card $cardId in group $groupName has an invalid next review timestamp."
    )
}

fun validateCard(card: Any?, groupName: String) {
    if (card !is Map<*, *>) throw BackupValidationException("Card in group $groupName is not a valid object.")
    if (card["id"] !is String) throw BackupValidationException("Card in group $groupName is missing a string ID.")
    assertDocumentId(card["id"])
    val cardId = card["id"] as String
    val pages = card["pages"]
    if (pages !is List<*>) {
        throw BackupValidationException(
            "Card $cardId in group $groupName must have a \"pages\" array."
        )
    }
    if (pages.any {
            it !is String
        }
    ) {
        throw BackupValidationException("Card $cardId in group $groupName must have string pages.")
    }
    validateSrsState(cardId, groupName, card["srsState"])
}

fun validateGroup(group: Any?) {
    if (group !is Map<*, *>) throw BackupValidationException("Each group must be a valid object.")
    if (group["id"] !is String) throw BackupValidationException("Each group must have a string id.")
    assertDocumentId(group["id"])
    if (group["name"] !is String) throw BackupValidationException("Group with ID ${group["id"]} must have a name.")
    if (group["cards"] !is List<*>) {
        throw BackupValidationException(
            "Group with ID ${group["id"]} must contain a \"cards\" array."
        )
    }
    if (group["pageLanguages"] !is List<*>) {
        throw BackupValidationException(
            "Group with ID ${group["id"]} must contain a \"pageLanguages\" array."
        )
    }
    if (group["pageNames"] !is List<*>) {
        throw BackupValidationException(
            "Group with ID ${group["id"]} must contain a \"pageNames\" array."
        )
    }
    if (group["activeModeId"] !is String) {
        throw BackupValidationException(
            "Group with ID ${group["id"]} must have an active study mode."
        )
    }
    val groupName = group["name"] as String
    (group["cards"] as List<*>).forEach { validateCard(it, groupName) }
}

fun validateStudyMode(mode: Any?) {
    if (mode !is Map<*, *>) throw BackupValidationException("Each study mode must be a valid object.")
    if (mode["id"] !is String) throw BackupValidationException("Each study mode must have a string id.")
    assertDocumentId(mode["id"])
    val modeId = mode["id"] as String
    if (mode["name"] !is String) throw BackupValidationException("Study mode with ID $modeId must have a name.")
    if (mode["steps"] !is List<*>) {
        throw BackupValidationException(
            "Study mode with ID $modeId must have a \"steps\" array."
        )
    }
    if (mode["isBuiltIn"] != null &&
        mode["isBuiltIn"] !is Boolean
    ) {
        throw BackupValidationException("Study mode with ID $modeId has an invalid isBuiltIn flag.")
    }
    if (mode["builtInSourceId"] != null &&
        mode["builtInSourceId"] !is String
    ) {
        throw BackupValidationException("Study mode with ID $modeId has an invalid builtInSourceId.")
    }
    (mode["steps"] as List<*>).forEachIndexed { index, step -> assertStudyModeStep(step, modeId, index) }
}

fun validateBackupData(data: Any?) {
    if (data !is Map<*, *>) throw BackupValidationException("Backup data is not a valid JSON object.")
    if (data["groups"] !is List<*>) throw BackupValidationException("Backup data must contain a \"groups\" array.")
    if (data["studyModes"] !is List<*>) {
        throw BackupValidationException(
            "Backup data must contain a \"studyModes\" array."
        )
    }
    if (data["activityHeatmap"] !is Map<*, *>) {
        throw BackupValidationException(
            "Backup data must contain an \"activityHeatmap\" object."
        )
    }
    (data["activityHeatmap"] as Map<*, *>).forEach { (date, count) ->
        assertDocumentId(date)
        assertNonNegativeNumber(count, "Activity heatmap entry $date must be a non-negative number.")
    }
    (data["groups"] as List<*>).forEach { validateGroup(it) }
    (data["studyModes"] as List<*>).forEach { validateStudyMode(it) }
    val groups = data["groups"] as List<*>
    assertUniqueIds(groups, "deck")
    assertUniqueIds(groups.flatMap { (it as Map<*, *>)["cards"] as List<*> }, "card")
    assertUniqueIds(data["studyModes"] as List<*>, "study mode")
}
