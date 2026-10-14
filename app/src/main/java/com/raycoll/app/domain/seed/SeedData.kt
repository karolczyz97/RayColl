package com.raycoll.app.domain.seed

import com.raycoll.app.core.Ids
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData
import com.raycoll.app.domain.seed.SEED_MODES
import java.util.UUID

/**
 * Startowe dane nowego użytkownika: dwie małe talie z podstawowymi zwrotami
 * w obcych językach, z tłumaczeniem w języku aplikacji.
 *
 * [idScope] (uid konta) daje stałe id talii i kart: dwa urządzenia, które jednocześnie
 * zaczną nowe konto z pustą chmurą, utworzą te same dokumenty, a scalenie ich nie zdubluje.
 * Id to zwykłe UUID zgodne z zapisanymi danymi. Gość (null) dostaje losowe id.
 */
object SeedData {

    fun createSeedData(uiLanguage: String, idScope: String? = null): StoreData = StoreData(
        groups = createSeedGroups(uiLanguage, idScope),
        studyModes = SEED_MODES
    )

    fun createSeedGroups(uiLanguage: String, idScope: String? = null): List<FlashcardGroup> {
        val native = SeedLanguage.fromCode(uiLanguage)
        return SeedLanguage.entries
            .filter { it != native }
            .take(DECK_COUNT)
            .map { foreign -> phraseDeck(foreign, native, idScope) }
    }

    private fun phraseDeck(foreign: SeedLanguage, native: SeedLanguage, idScope: String?): FlashcardGroup {
        val deckKey = "raycoll-seed:$idScope:${foreign.locale}:${native.locale}"
        return FlashcardGroup(
            id = seedId(idScope, deckKey),
            name = native.deckName(foreign),
            pageLanguages = listOf(foreign.locale, native.locale),
            pageNames = listOf(native.languageNames.getValue(foreign), native.languageNames.getValue(native)),
            cards = foreign.phrases.zip(native.phrases).mapIndexed { index, (phrase, translation) ->
                Flashcard(
                    id = seedId(idScope, "$deckKey:card:$index"),
                    pages = listOf(phrase, translation),
                    srsState = SrsState()
                )
            }
        )
    }

    private fun seedId(idScope: String?, key: String): String =
        if (idScope == null) Ids.newId() else UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()

    private const val DECK_COUNT = 2
}

/** Języki interfejsu z tłumaczeniami tych samych pięciu zwrotów (dzień dobry, dziękuję, proszę, do widzenia, tak). */
private enum class SeedLanguage(val locale: String, val phrases: List<String>) {
    EN("en-US", listOf("Good morning", "Thank you", "Please", "Goodbye", "Yes")),
    ES("es-ES", listOf("Buenos días", "Gracias", "Por favor", "Adiós", "Sí")),
    IT("it-IT", listOf("Buongiorno", "Grazie", "Per favore", "Arrivederci", "Sì")),
    DE("de-DE", listOf("Guten Morgen", "Danke", "Bitte", "Auf Wiedersehen", "Ja")),
    PL("pl-PL", listOf("Dzień dobry", "Dziękuję", "Proszę", "Do widzenia", "Tak"));

    /** Nazwy języków zapisane w TYM języku. */
    val languageNames: Map<SeedLanguage, String>
        get() = when (this) {
            EN -> mapOf(EN to "English", ES to "Spanish", IT to "Italian", DE to "German", PL to "Polish")
            ES -> mapOf(EN to "Inglés", ES to "Español", IT to "Italiano", DE to "Alemán", PL to "Polaco")
            IT -> mapOf(EN to "Inglese", ES to "Spagnolo", IT to "Italiano", DE to "Tedesco", PL to "Polacco")
            DE -> mapOf(EN to "Englisch", ES to "Spanisch", IT to "Italienisch", DE to "Deutsch", PL to "Polnisch")
            PL -> mapOf(EN to "Angielski", ES to "Hiszpański", IT to "Włoski", DE to "Niemiecki", PL to "Polski")
        }

    fun deckName(foreign: SeedLanguage): String {
        val language = languageNames.getValue(foreign)
        return when (this) {
            EN -> "$language – basics"
            ES -> "$language – básico"
            IT -> "$language – base"
            DE -> "$language – Grundlagen"
            PL -> "$language – podstawy"
        }
    }

    companion object {
        fun fromCode(code: String): SeedLanguage =
            entries.firstOrNull { it.locale.substringBefore('-').equals(code.substringBefore('-'), ignoreCase = true) }
                ?: EN
    }
}
