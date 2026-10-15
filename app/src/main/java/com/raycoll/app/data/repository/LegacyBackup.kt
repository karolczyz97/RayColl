package com.raycoll.app.data.repository

import com.raycoll.app.domain.seed.seedModeFor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Starszy edytor pozwalał zapisać pustą nazwę znanego trybu wbudowanego. */
internal fun repairLegacyBuiltInNames(source: JsonElement): JsonElement {
    val root = source as? JsonObject ?: return source
    val modes = root["studyModes"] as? JsonArray ?: return source
    val repaired = modes.map { entry ->
        val mode = entry as? JsonObject ?: return@map entry
        val name = mode["name"] as? JsonPrimitive ?: return@map entry
        val builtIn = (mode["isBuiltIn"] as? JsonPrimitive)?.booleanOrNull == true
        if (!builtIn || !name.isString || name.content.isNotBlank()) return@map entry
        val sourceId = (mode["builtInSourceId"] as? JsonPrimitive)?.contentOrNull
            ?: (mode["id"] as? JsonPrimitive)?.contentOrNull
        val seed = seedModeFor(sourceId) ?: return@map entry
        JsonObject(
            mode + mapOf(
                "name" to JsonPrimitive(seed.name),
                "builtInSourceId" to JsonPrimitive(seed.builtInSourceId)
            )
        )
    }
    return JsonObject(root + ("studyModes" to JsonArray(repaired)))
}
