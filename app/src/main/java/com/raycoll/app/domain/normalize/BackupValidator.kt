package com.raycoll.app.domain.normalize

import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.ModeStep
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.model.CompoundStep
import com.raycoll.app.domain.model.CompoundParams
import com.raycoll.app.domain.model.CompoundBranch
import com.raycoll.app.domain.model.CompoundPause
import com.raycoll.app.domain.model.StoreData

class BackupValidationException(message: String) : Exception(message)

/**
 * Walidacja surowego tekstu backupu: parse JSON → czyste struktury (Map/List/String/Number/Boolean)
 * → validateBackupData. Błąd parsowania = błąd walidacji (import odrzucony, stan nietknięty).
 */
fun validateBackupJson(jsonText: String) {
    val element = try {
        kotlinx.serialization.json.Json.parseToJsonElement(jsonText)
    } catch (e: Exception) {
        throw BackupValidationException("Backup data is not a valid JSON object.")
    }
    validateBackupData(jsonElementToPlain(element))
}

internal fun jsonElementToPlain(element: kotlinx.serialization.json.JsonElement): Any? = when (element) {
    is kotlinx.serialization.json.JsonNull -> null
    is kotlinx.serialization.json.JsonPrimitive -> when {
        element.isString -> element.content
        element.content == "true" -> true
        element.content == "false" -> false
        element.content.toLongOrNull() != null -> element.content.toLong()
        element.content.toDoubleOrNull() != null -> element.content.toDouble()
        else -> element.content
    }
    is kotlinx.serialization.json.JsonArray -> element.map { jsonElementToPlain(it) }
    is kotlinx.serialization.json.JsonObject -> element.mapValues { jsonElementToPlain(it.value) }
}

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

private fun assertOptionalPage(value: Any?, message: String) {
    if (value != null) assertNonNegativeNumber(value, message)
}

private fun assertCompoundPause(value: Any?, modeId: String, index: Int) {
    if (value == null) return
    if (value !is Map<*, *>) throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid compound pause.")
    when (value["kind"]) {
        "fixed" -> assertNonNegativeNumber(value["ms"], "Step ${index + 1} in study mode $modeId has invalid compound pause.")
        "dynamic" -> {
            assertNonNegativeNumber(value["page"], "Step ${index + 1} in study mode $modeId has invalid compound pause.")
            assertNonNegativeNumber(value["multiplier"], "Step ${index + 1} in study mode $modeId has invalid compound pause.")
        }
        else -> throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid compound pause.")
    }
}

private fun assertCompoundBranch(value: Any?, modeId: String, index: Int) {
    if (value !is Map<*, *>) throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid compound branch.")
    assertBoolean(value["feedback"], "Step ${index + 1} in study mode $modeId has invalid compound branch.")
    assertOptionalPage(value["speakPage"], "Step ${index + 1} in study mode $modeId has invalid compound branch.")
    assertCompoundPause(value["pause"], modeId, index)
    assertBoolean(value["revealAll"], "Step ${index + 1} in study mode $modeId has invalid compound branch.")
    assertBoolean(value["markFailed"], "Step ${index + 1} in study mode $modeId has invalid compound branch.")
    val rate = value["rate"]
    if (rate != "fromAnswer" && rate != "fixed" && rate != "none")
        throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid compound branch.")
    assertNonNegativeNumber(value["fixedRating"], "Step ${index + 1} in study mode $modeId has invalid compound branch.")
    assertBoolean(value["advance"], "Step ${index + 1} in study mode $modeId has invalid compound branch.")
}

private fun assertCompoundParams(params: Any?, modeId: String, index: Int) {
    if (params !is Map<*, *>) throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid compound params.")
    val kind = params["kind"]
    when (kind) {
        "present_front" -> {
            assertNonNegativeNumber(params["page"], "Step ${index + 1} in study mode $modeId has invalid pageIndex.")
            assertBoolean(params["speak"], "Step ${index + 1} in study mode $modeId has invalid compound params.")
        }
        "flip_reveal" -> {
            val rs = params["revealStyle"]
            if (rs != "all" && rs != "next") throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid compound params.")
        }
        "show_all_grade", "fail_next" -> {}
        "speak_pause_next" -> {
            assertNonNegativeNumber(params["page"], "Step ${index + 1} in study mode $modeId has invalid pageIndex.")
            assertNonNegativeNumber(params["nextPage"], "Step ${index + 1} in study mode $modeId has invalid nextPageIndex.")
            assertNonNegativeNumber(params["multiplier"], "Step ${index + 1} in study mode $modeId has invalid pauseMultiplier.")
        }
        "auto_flip" -> {
            assertNonNegativeNumber(params["questionPage"], "Step ${index + 1} in study mode $modeId has invalid pageIndex.")
            assertNonNegativeNumber(params["answerPage"], "Step ${index + 1} in study mode $modeId has invalid pageIndex.")
            assertNonNegativeNumber(params["multiplier"], "Step ${index + 1} in study mode $modeId has invalid pauseMultiplier.")
            assertBoolean(params["speakQuestion"], "Step ${index + 1} in study mode $modeId has invalid compound params.")
            assertBoolean(params["speakAnswer"], "Step ${index + 1} in study mode $modeId has invalid compound params.")
        }
        "listen_grade" -> {
            assertNonNegativeNumber(params["answerPage"], "Step ${index + 1} in study mode $modeId has invalid pageIndex.")
            assertNonNegativeNumber(params["threshold"], "Step ${index + 1} in study mode $modeId has invalid successThreshold.")
            if ((params["threshold"] as Number).toInt() > 100)
                throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid successThreshold.")
            assertCompoundBranch(params["onCorrect"], modeId, index)
            assertCompoundBranch(params["onWrong"], modeId, index)
            assertBoolean(params["manualFallback"], "Step ${index + 1} in study mode $modeId has invalid compound params.")
        }
        "grade_after_listen" -> {
            assertCompoundBranch(params["onCorrect"], modeId, index)
            assertCompoundBranch(params["onWrong"], modeId, index)
            assertBoolean(params["manualFallback"], "Step ${index + 1} in study mode $modeId has invalid compound params.")
        }
        "auto_pass_next" -> assertNonNegativeNumber(params["rating"], "Step ${index + 1} in study mode $modeId has invalid rating.")
        else -> throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid compound params.")
    }
}

fun assertStudyModeStep(step: Any?, modeId: String, index: Int) {
    if (step !is Map<*, *>) throw BackupValidationException("Step ${index + 1} in study mode $modeId must be a valid object.")
    when (step["type"]) {
        "show_page" -> assertNonNegativeNumber(step["pageIndex"], "Step ${index + 1} in study mode $modeId has invalid pageIndex.")
        "speak_page" -> assertNonNegativeNumber(step["pageIndex"], "Step ${index + 1} in study mode $modeId has invalid pageIndex.")
        "dynamic_pause" -> {
            assertNonNegativeNumber(step["nextPageIndex"], "Step ${index + 1} in study mode $modeId has invalid nextPageIndex.")
            if (step["pauseMultiplier"] != null) assertNonNegativeNumber(step["pauseMultiplier"], "Step ${index + 1} in study mode $modeId has invalid pauseMultiplier.")
        }
        "wait" -> assertNonNegativeNumber(step["ms"], "Step ${index + 1} in study mode $modeId has invalid ms.")
        "listen_and_check" -> {
            assertNonNegativeNumber(step["pageIndex"], "Step ${index + 1} in study mode $modeId has invalid pageIndex.")
            assertNonNegativeNumber(step["successThreshold"], "Step ${index + 1} in study mode $modeId has invalid successThreshold.")
            if ((step["successThreshold"] as Number).toInt() > 100) throw BackupValidationException("Step ${index + 1} in study mode $modeId has invalid successThreshold.")
        }
        "auto_rate_fixed" -> assertNonNegativeNumber(step["rating"], "Step ${index + 1} in study mode $modeId has invalid rating.")
        "compound" -> {
            if (step["version"] != null) assertNumber(step["version"], "Step ${index + 1} in study mode $modeId has invalid compound version.")
            assertCompoundParams(step["params"], modeId, index)
        }
        "show_all_pages", "wait_for_tap_to_reveal_next", "wait_for_tap_to_reveal",
        "show_ratings", "feedback_success", "feedback_error",
        "auto_rate_from_answer", "mark_failed", "next_card" -> {}
        else -> throw BackupValidationException("Step ${index + 1} in study mode $modeId has an unsupported type.")
    }
}

fun validateSrsState(cardId: String, groupName: String, srsState: Any?) {
    if (srsState !is Map<*, *>) throw BackupValidationException("Card $cardId in group $groupName is missing a valid \"srsState\" object.")
    assertNumber(srsState["difficulty"], "Card $cardId in group $groupName has an invalid difficulty.")
    assertNumber(srsState["stability"], "Card $cardId in group $groupName has an invalid stability.")
    assertNumber(srsState["repetitions"], "Card $cardId in group $groupName has invalid repetitions.")
    assertNumber(srsState["state"], "Card $cardId in group $groupName has invalid state.")
    assertNumber(srsState["lastReviewTimestamp"], "Card $cardId in group $groupName has an invalid last review timestamp.")
    assertNumber(srsState["nextReviewTimestamp"], "Card $cardId in group $groupName has an invalid next review timestamp.")
}

fun validateCard(card: Any?, groupName: String) {
    if (card !is Map<*, *>) throw BackupValidationException("Card in group $groupName is not a valid object.")
    if (card["id"] !is String) throw BackupValidationException("Card in group $groupName is missing a string ID.")
    val cardId = card["id"] as String
    val pages = card["pages"]
    if (pages !is List<*>) throw BackupValidationException("Card $cardId in group $groupName must have a \"pages\" array.")
    if (pages.any { it !is String }) throw BackupValidationException("Card $cardId in group $groupName must have string pages.")
    validateSrsState(cardId, groupName, card["srsState"])
}

fun validateGroup(group: Any?) {
    if (group !is Map<*, *>) throw BackupValidationException("Each group must be a valid object.")
    if (group["id"] !is String) throw BackupValidationException("Each group must have a string id.")
    if (group["name"] !is String) throw BackupValidationException("Group with ID ${group["id"]} must have a name.")
    if (group["cards"] !is List<*>) throw BackupValidationException("Group with ID ${group["id"]} must contain a \"cards\" array.")
    if (group["pageLanguages"] !is List<*>) throw BackupValidationException("Group with ID ${group["id"]} must contain a \"pageLanguages\" array.")
    if (group["pageNames"] !is List<*>) throw BackupValidationException("Group with ID ${group["id"]} must contain a \"pageNames\" array.")
    if (group["activeModeId"] !is String) throw BackupValidationException("Group with ID ${group["id"]} must have an active study mode.")
    val groupName = group["name"] as String
    (group["cards"] as List<*>).forEach { validateCard(it, groupName) }
}

fun validateStudyMode(mode: Any?) {
    if (mode !is Map<*, *>) throw BackupValidationException("Each study mode must be a valid object.")
    if (mode["id"] !is String) throw BackupValidationException("Each study mode must have a string id.")
    val modeId = mode["id"] as String
    if (mode["name"] !is String) throw BackupValidationException("Study mode with ID $modeId must have a name.")
    if (mode["steps"] !is List<*>) throw BackupValidationException("Study mode with ID $modeId must have a \"steps\" array.")
    if (mode["isBuiltIn"] != null && mode["isBuiltIn"] !is Boolean) throw BackupValidationException("Study mode with ID $modeId has an invalid isBuiltIn flag.")
    if (mode["builtInSourceId"] != null && mode["builtInSourceId"] !is String) throw BackupValidationException("Study mode with ID $modeId has an invalid builtInSourceId.")
    (mode["steps"] as List<*>).forEachIndexed { index, step -> assertStudyModeStep(step, modeId, index) }
}

fun validateBackupData(data: Any?) {
    if (data !is Map<*, *>) throw BackupValidationException("Backup data is not a valid JSON object.")
    if (data["groups"] !is List<*>) throw BackupValidationException("Backup data must contain a \"groups\" array.")
    if (data["studyModes"] !is List<*>) throw BackupValidationException("Backup data must contain a \"studyModes\" array.")
    if (data["activityHeatmap"] !is Map<*, *>) throw BackupValidationException("Backup data must contain an \"activityHeatmap\" object.")
    (data["activityHeatmap"] as Map<*, *>).forEach { (date, count) ->
        assertNonNegativeNumber(count, "Activity heatmap entry $date must be a non-negative number.")
    }
    (data["groups"] as List<*>).forEach { validateGroup(it) }
    (data["studyModes"] as List<*>).forEach { validateStudyMode(it) }
}
