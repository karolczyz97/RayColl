package com.raycoll.app

import com.raycoll.app.ui.appsettings.APP_LANGUAGES
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spójność tłumaczeń: wszystkie 5 plików strings.xml ma IDENTYCZNY zestaw kluczy
 * (string + plurals), zgodne placeholdery, nie ma wartości uciętych (np. przez zepsute
 * ucieczki `\'`) ani nieprzetłumaczonych angielskich etykiet. Test czyta pliki źródłowe modułu.
 */
class I18nConsistencyTest {

    @Test
    fun `language picker includes exactly the available translations`() {
        val translated = resDir().listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "strings.xml").isFile }
            .map { if (it.name == "values") "en" else it.name.removePrefix("values-") }
            .toSet()
        assertEquals(translated, APP_LANGUAGES.filter(String::isNotBlank).toSet())
    }

    private val locales = listOf("values", "values-pl", "values-de", "values-es", "values-it")
    private val foreignLocales = locales.drop(1)

    /**
     * Klucze celowo zostawione identyczne z angielskim mimo >= 2 słów (format `values-xx/klucz`,
     * jedna linijka komentarza z powodem na wpis). Pojedyncze słowa (Audio, System, Dashboard, Version)
     * i tak przechodzą, więc wpis jest potrzebny tylko dla dłuższych fraz.
     */
    private val identicalAllowlist: Set<String> = emptySet()

    // --- wczytywanie i parsowanie ---

    private fun resDir(): File {
        // working dir testów = katalog modułu app (fallback: root projektu)
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.first { it.isDirectory }
    }

    private fun keysOf(file: File): Set<String> {
        // teksty `translatable="false"` są tylko w bazowym pliku — nie liczą się do parytetu
        val content = file.readText().replace(notTranslatableTagRegex, "")
        val stringKeys = Regex("<string name=\"([^\"]+)\"").findAll(content).map { it.groupValues[1] }
        val pluralKeys = Regex("<plurals name=\"([^\"]+)\"").findAll(content).map { "plurals:" + it.groupValues[1] }
        return (stringKeys + pluralKeys).toSet()
    }

    /**
     * Zawartość jednego strings.xml. Wartości są po zdekodowaniu encji XML (`&amp;` -> `&`),
     * ale PRZED obróbką ucieczek Androida (`\'`) - dzięki temu widać ucięty, pojedynczy backslash.
     * Teksty z `translatable="false"` są pomijane.
     */
    private class StringsFile(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

    private val commentRegex = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val stringTagRegex = Regex("<string\\s+([^>]*?)(?:/>|>(.*?)</string>)", RegexOption.DOT_MATCHES_ALL)
    private val pluralsTagRegex = Regex("<plurals\\s+([^>]*?)>(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL)
    private val itemTagRegex = Regex("<item\\s+([^>]*?)>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val nameAttrRegex = Regex("name=\"([^\"]*)\"")
    private val quantityAttrRegex = Regex("quantity=\"([^\"]*)\"")
    private val notTranslatableRegex = Regex("translatable=\"false\"")
    private val notTranslatableTagRegex =
        Regex("<string\\s+[^>]*translatable=\"false\"[^>]*>.*?</string>", RegexOption.DOT_MATCHES_ALL)
    private val entityRegex = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

    private fun parse(file: File): StringsFile {
        val content = file.readText().replace(commentRegex, "")
        val strings = LinkedHashMap<String, String>()
        for (m in stringTagRegex.findAll(content)) {
            val attrs = m.groupValues[1]
            if (notTranslatableRegex.containsMatchIn(attrs)) continue
            val name = nameAttrRegex.find(attrs)?.groupValues?.get(1) ?: continue
            strings[name] = decodeEntities(m.groupValues[2])
        }
        val plurals = LinkedHashMap<String, Map<String, String>>()
        for (m in pluralsTagRegex.findAll(content)) {
            val attrs = m.groupValues[1]
            if (notTranslatableRegex.containsMatchIn(attrs)) continue
            val name = nameAttrRegex.find(attrs)?.groupValues?.get(1) ?: continue
            val items = LinkedHashMap<String, String>()
            for (item in itemTagRegex.findAll(m.groupValues[2])) {
                val quantity = quantityAttrRegex.find(item.groupValues[1])?.groupValues?.get(1) ?: continue
                items[quantity] = decodeEntities(item.groupValues[2])
            }
            plurals[name] = items
        }
        return StringsFile(strings, plurals)
    }

    private fun load(locale: String): StringsFile {
        val parsed = parse(File(resDir(), "$locale/strings.xml"))
        assertTrue(
            "Parser nie wczytał stringów z $locale (string: ${parsed.strings.size}, plurals: ${parsed.plurals.size})",
            parsed.strings.size > 100 && parsed.plurals.isNotEmpty()
        )
        return parsed
    }

    private fun decodeEntities(raw: String): String = entityRegex.replace(raw) { m ->
        when (val entity = m.groupValues[1]) {
            "amp" -> "&"

            "lt" -> "<"

            "gt" -> ">"

            "quot" -> "\""

            "apos" -> "'"

            else -> {
                val codePoint =
                    if (entity.startsWith("#x")) entity.substring(2).toInt(16) else entity.substring(1).toInt()
                String(Character.toChars(codePoint))
            }
        }
    }

    /** Ucieczki jak w aapt2: `\'` `\"` `\\` `\n` `\t` `\uXXXX`; pojedynczy backslash na końcu zostaje. */
    private fun unescape(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i == raw.lastIndex) {
                sb.append(c)
                i++
                continue
            }
            val next = raw[i + 1]
            when {
                next == 'n' -> {
                    sb.append('\n')
                    i += 2
                }

                next == 't' -> {
                    sb.append('\t')
                    i += 2
                }

                next == 'u' && i + 6 <= raw.length && raw.substring(i + 2, i + 6).toIntOrNull(16) != null -> {
                    sb.append(raw.substring(i + 2, i + 6).toInt(16).toChar())
                    i += 6
                }

                else -> {
                    sb.append(next)
                    i += 2
                }
            }
        }
        return sb.toString()
    }

    // --- placeholdery ---

    private val positionalRegex = Regex("%\\d+\\$[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")
    private val plainSpecRegex = Regex("%[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")

    private class Placeholders(
        /** Argumenty pozycyjne, np. `%1$d`, `%2$.1f`. */
        val positional: Set<String>,
        /** Argumenty bez numeru (`%s`, `%d`): konwersja -> liczba wystąpień. */
        val plain: Map<String, Int>,
        /** Liczba literalnych procentów `%%`. */
        val percents: Int
    )

    private fun placeholdersOf(value: String): Placeholders {
        val withoutPercents = value.replace("%%", "")
        val percents = (value.length - withoutPercents.length) / 2
        val positional = positionalRegex.findAll(withoutPercents).map { it.value }.toSortedSet()
        val rest = positionalRegex.replace(withoutPercents, "")
        val plain = plainSpecRegex.findAll(rest).groupingBy { it.value.last().toString() }.eachCount()
        return Placeholders(positional, plain, percents)
    }

    // --- testy ---

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
        val base = load("values")
        val problems = mutableListOf<String>()
        for (locale in foreignLocales) {
            for ((key, value) in load(locale).strings) {
                val expected = placeholdersOf(base.strings[key] ?: continue).positional
                val actual = placeholdersOf(value).positional
                if (expected != actual) problems += "$locale/$key: oczekiwano $expected, jest $actual"
            }
        }
        assertTrue("Argumenty pozycyjne formatu: $problems", problems.isEmpty())
    }

    @Test
    fun `plain placeholders and percent signs match across locales`() {
        val base = load("values")
        val problems = mutableListOf<String>()
        for (locale in foreignLocales) {
            for ((key, value) in load(locale).strings) {
                val expected = placeholdersOf(base.strings[key] ?: continue)
                val actual = placeholdersOf(value)
                if (expected.plain != actual.plain) {
                    problems += "$locale/$key: %s/%d bez numeru - oczekiwano ${expected.plain}, jest ${actual.plain}"
                }
                if (expected.percents != actual.percents) {
                    problems += "$locale/$key: liczba %% - oczekiwano ${expected.percents}, jest ${actual.percents}"
                }
            }
        }
        assertTrue("Placeholdery bez numeru / %%: $problems", problems.isEmpty())
    }

    @Test
    fun `plural items only use placeholders of the base other item`() {
        val base = load("values")
        val problems = mutableListOf<String>()
        for (locale in foreignLocales) {
            for ((key, items) in load(locale).plurals) {
                val baseOther = base.plurals[key]?.get("other") ?: continue
                // Niektóre języki w "one" pomijają liczbę, więc wymagamy tylko, by nie pojawiło się nic
                // spoza bazowego "other".
                val allowed = placeholdersOf(baseOther)
                if ("other" !in items) problems += "$locale/$key: brak elementu quantity=\"other\""
                for ((quantity, value) in items) {
                    val actual = placeholdersOf(value)
                    val extraPositional = actual.positional - allowed.positional
                    if (extraPositional.isNotEmpty()) {
                        problems += "$locale/$key[$quantity]: argumenty spoza bazy $extraPositional"
                    }
                    for ((spec, count) in actual.plain) {
                        if (count > (allowed.plain[spec] ?: 0)) {
                            problems += "$locale/$key[$quantity]: za dużo %$spec bez numeru ($count)"
                        }
                    }
                    if (actual.percents > allowed.percents) {
                        problems += "$locale/$key[$quantity]: za dużo %% (${actual.percents} > ${allowed.percents})"
                    }
                }
            }
        }
        assertTrue("Placeholdery w plurals: $problems", problems.isEmpty())
    }

    /** Wartość kończy się nieparzystą liczbą backslashy, czyli ostatni `\` nie ucieka żadnego znaku. */
    private fun endsWithLoneBackslash(raw: String): Boolean {
        val trimmed = raw.trimEnd()
        return (trimmed.length - trimmed.trimEnd('\\').length) % 2 == 1
    }

    private fun truncationProblem(raw: String, baseRaw: String?, compareLength: Boolean): String? {
        if (endsWithLoneBackslash(raw)) return "kończy się pojedynczym backslashem (ucięte ucieczki?): '$raw'"
        val text = unescape(raw)
        if (text.isBlank()) return "pusta wartość"
        if (compareLength && baseRaw != null) {
            val baseText = unescape(baseRaw)
            if (baseText.length >= 25 && text.length * 100 < baseText.length * 35) {
                return "podejrzanie krótka (${text.length} znaków wobec ${baseText.length} w bazie): '$text'"
            }
        }
        return null
    }

    @Test
    fun `no value is empty or truncated`() {
        val base = load("values")
        val problems = mutableListOf<String>()
        for (locale in locales) {
            val isBase = locale == "values"
            val file = load(locale)
            for ((key, raw) in file.strings) {
                truncationProblem(raw, base.strings[key], compareLength = !isBase)
                    ?.let { problems += "$locale/$key: $it" }
            }
            for ((key, items) in file.plurals) {
                val baseOther = base.plurals[key]?.get("other")
                for ((quantity, raw) in items) {
                    truncationProblem(raw, baseOther, compareLength = !isBase)
                        ?.let { problems += "$locale/$key[$quantity]: $it" }
                }
            }
        }
        assertTrue("Ucięte lub puste wartości:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    private val wordRegex = Regex("[A-Za-z]{3,}")

    private fun asciiWordCount(value: String): Int {
        val text = plainSpecRegex.replace(positionalRegex.replace(unescape(value), " "), " ")
        return wordRegex.findAll(text).count()
    }

    @Test
    fun `no untranslated english labels in foreign locales`() {
        val base = load("values")
        val problems = mutableListOf<String>()
        for (locale in foreignLocales) {
            val file = load(locale)
            for ((key, value) in file.strings) {
                val english = base.strings[key] ?: continue
                if (value == english && asciiWordCount(value) >= 2 && "$locale/$key" !in identicalAllowlist) {
                    problems += "$locale/$key: '$value'"
                }
            }
            for ((key, items) in file.plurals) {
                val baseItems = base.plurals[key] ?: continue
                for ((quantity, value) in items) {
                    val english = baseItems[quantity] ?: baseItems["other"] ?: continue
                    if (value == english && asciiWordCount(value) >= 2 && "$locale/$key" !in identicalAllowlist) {
                        problems += "$locale/$key[$quantity]: '$value'"
                    }
                }
            }
        }
        assertTrue(
            "Nieprzetłumaczone angielskie etykiety (dopisz do identicalAllowlist tylko, jeśli celowo):\n" +
                problems.joinToString("\n"),
            problems.isEmpty()
        )
    }
}
