package com.raycoll.app.domain.imports

import com.raycoll.app.domain.model.DEFAULT_PAGE_LANGUAGE

/** Nazwa języka wygrywa z ogólnym podpisem kolumny, np. Polish translation. */
object LanguageDetector {
    private class LanguageTokens(
        val tag: String,
        val names: Set<String>,
        val prefixes: Set<String>,
        val labels: Set<String> = emptySet(),
        val labelPrefixes: Set<String> = emptySet()
    )

    private val LANGUAGES = listOf(
        LanguageTokens(
            "en-US",
            setOf("english", "en", "en-us", "en-gb"),
            setOf("angiel"),
            setOf("word", "phrase", "translation", "example", "front", "back", "meaning", "definition")
        ),
        LanguageTokens(
            "pl-PL",
            setOf("polish", "polski", "pl", "pl-pl"),
            setOf("polsk"),
            setOf("slowo", "tlumaczenie", "przyklad"),
            setOf("fraz")
        ),
        LanguageTokens(
            "es-ES",
            setOf("es", "es-es", "spanish", "espanol"),
            setOf("hiszpan"),
            setOf("palabra", "traduccion", "ejemplo")
        ),
        LanguageTokens(
            "de-DE",
            setOf("german", "deutsch", "de", "de-de"),
            setOf("niemiec"),
            setOf("wort", "ubersetzung", "beispiel")
        ),
        LanguageTokens("fr-FR", setOf("french", "francais", "fr", "fr-fr"), setOf("franc")),
        LanguageTokens("it-IT", setOf("italiano", "italian", "it", "it-it"), setOf("wlos"))
    )

    fun detectLanguage(header: String): String {
        val words = HeaderDetector.normalizeHeaderToken(header).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val named = LANGUAGES.firstOrNull { language ->
            words.any { word -> word in language.names || language.prefixes.any { word.startsWith(it) } }
        }
        if (named != null) return named.tag
        return LANGUAGES.firstOrNull { language ->
            words.any { word -> word in language.labels || language.labelPrefixes.any { word.startsWith(it) } }
        }?.tag ?: DEFAULT_PAGE_LANGUAGE
    }
}
