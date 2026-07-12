package com.raycoll.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spójność i18n (T1): wszystkie 5 plików strings.xml ma IDENTYCZNY zestaw kluczy
 * (string + plurals). Test czyta pliki źródłowe modułu.
 */
class I18nConsistencyTest {

    private val locales = listOf("values", "values-pl", "values-de", "values-es", "values-it")

    private fun resDir(): File {
        // working dir testów = katalog modułu app (fallback: root projektu)
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.first { it.isDirectory }
    }

    private fun keysOf(file: File): Set<String> {
        val content = file.readText()
        val stringKeys = Regex("<string name=\"([^\"]+)\"").findAll(content).map { it.groupValues[1] }
        val pluralKeys = Regex("<plurals name=\"([^\"]+)\"").findAll(content).map { "plurals:" + it.groupValues[1] }
        return (stringKeys + pluralKeys).toSet()
    }

    @Test
    fun `all five locales define the same keys`() {
        val res = resDir()
        val keysByLocale = locales.associateWith { keysOf(File(res, "$it/strings.xml")) }
        val base = keysByLocale.getValue("values")
        assertTrue("Bazowy strings.xml jest pusty?", base.size > 100)
        for ((locale, keys) in keysByLocale) {
            val missing = base - keys
            val extra = keys - base
            assertEquals("Braki w $locale: $missing; nadmiar: $extra", emptySet<String>(), missing + extra)
        }
    }

    @Test
    fun `positional format args match across locales`() {
        val res = resDir()
        val argRegex = Regex("%\\d+\\$[sdf]")
        val base = File(res, "values/strings.xml").readText()
        val baseArgs = Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(base).associate { it.groupValues[1] to argRegex.findAll(it.groupValues[2]).map { m -> m.value }.toSortedSet() }
        for (locale in locales.drop(1)) {
            val content = File(res, "$locale/strings.xml").readText()
            Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(content).forEach { m ->
                val key = m.groupValues[1]
                val args = argRegex.findAll(m.groupValues[2]).map { it.value }.toSortedSet()
                val expected = baseArgs[key] ?: return@forEach
                assertEquals("Argumenty formatu w $locale/$key", expected, args)
            }
        }
    }
}
