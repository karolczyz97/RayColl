package com.raycoll.app.domain.imports

import java.text.Normalizer

private val LANG_TOKENS = mapOf(
    "en-US" to (listOf("word", "phrase", "english", "translation", "example", "front", "back", "meaning", "definition", "en") to listOf("angiel")),
    "pl-PL" to (listOf("słowo", "polish", "pl", "tłumaczenie", "przykład") to listOf("polsk", "fraz")),
    "es-ES" to (listOf("palabra", "es", "spanish", "espanol", "traduccion", "ejemplo") to listOf("hiszpan")),
    "de-DE" to (listOf("wort", "deutsch", "de", "ubersetzung", "beispiel") to listOf("niemiec")),
    "fr-FR" to (listOf("french", "francais", "fr") to listOf("franc")),
    "it-IT" to (listOf("italiano", "italian", "it") to listOf("włos")),
)

private fun normalizeToken(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .trim()

fun detectLangFromHeader(header: String): String {
    val h = normalizeToken(header)
    val words = h.split(Regex("\\s+"))
    for ((lang, pair) in LANG_TOKENS) {
        val (exact, prefixes) = pair
        if (words.any { it in exact || prefixes.any { p -> it.startsWith(p) } }) return lang
    }
    return "en-US"
}
