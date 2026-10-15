package com.raycoll.app.domain.seed

import com.raycoll.app.core.Ids
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.model.FlashcardGroup
import com.raycoll.app.domain.model.SrsState
import com.raycoll.app.domain.model.StoreData

/** Przykladowe talie z losowymi identyfikatorami i tlumaczeniami w jezyku interfejsu. */
object SeedData {

    fun createSeedData(uiLanguage: String): StoreData = StoreData(
        groups = createSeedGroups(uiLanguage),
        studyModes = SEED_MODES
    )

    fun createSeedGroups(uiLanguage: String): List<FlashcardGroup> {
        val native = SeedLanguage.fromCode(uiLanguage)
        return SeedLanguage.entries
            .filter { it != native }
            .take(DECK_COUNT)
            .map { foreign -> phraseDeck(foreign, native) }
    }

    private fun phraseDeck(foreign: SeedLanguage, native: SeedLanguage): FlashcardGroup = FlashcardGroup(
        id = Ids.newId(),
        name = native.deckName(foreign),
        pageLanguages = listOf(foreign.locale, native.locale),
        pageNames = listOf(native.languageNames.getValue(foreign), native.languageNames.getValue(native)),
        cards = foreign.phrases.zip(native.phrases).map { (phrase, translation) ->
            Flashcard(
                id = Ids.newId(),
                pages = listOf(phrase, translation),
                srsState = SrsState()
            )
        }
    )

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
    val languageNames: Map<SeedLanguage, String> by lazy {
        when (this) {
            EN -> mapOf(EN to "English", ES to "Spanish", IT to "Italian", DE to "German", PL to "Polish")
            ES -> mapOf(EN to "Inglés", ES to "Español", IT to "Italiano", DE to "Alemán", PL to "Polaco")
            IT -> mapOf(EN to "Inglese", ES to "Spagnolo", IT to "Italiano", DE to "Tedesco", PL to "Polacco")
            DE -> mapOf(EN to "Englisch", ES to "Spanisch", IT to "Italienisch", DE to "Deutsch", PL to "Polnisch")
            PL -> mapOf(EN to "Angielski", ES to "Hiszpański", IT to "Włoski", DE to "Niemiecki", PL to "Polski")
        }
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
