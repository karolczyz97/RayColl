package com.raycoll.app.domain.model

import kotlin.math.pow

/** Id wbudowanego trybu "klasycznego" — domyślny tryb nowej talii. */
const val DEFAULT_MODE_ID = "classic"

/** Język strony, gdy talia nie ma go zapisanego. */
const val DEFAULT_PAGE_LANGUAGE = "en-US"

object PageLimits {
    const val MIN_PAGE_COUNT = 2
    const val MAX_VISIBLE_PAGE_COUNT = 5
    const val MAX_STORED_PAGE_COUNT = 20

    fun clampActivePageCount(candidate: Int, upperBound: Int): Int =
        candidate.coerceAtMost(upperBound).coerceAtLeast(MIN_PAGE_COUNT)
}

object SrsConstants {
    /**
     * Domyślne 21 wag FSRS-6: FSRS6_DEFAULT_PARAMETERS z fsrs-rs (BSD-3),
     * https://github.com/open-spaced-repetition/fsrs-rs/blob/0a5737406a0c5c7a5259b144738cbe4a0b649d76/src/inference_v6.rs
     * Indeksy: w[0..3] początkowa stabilność, w[4]/w[5] początkowa trudność, w[6]/w[7] zmiana trudności,
     * w[8..10] stabilność po przypomnieniu, w[11..14] po zapomnieniu, w[15]/w[16] kara „trudne”
     * i premia „łatwe”, w[17]/w[18] górna granica po pomyłce, w[20] zanik krzywej zapominania.
     */
    val W: List<Double> = listOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001,
        1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014,
        1.8729, 0.5425, 0.0912, 0.0658, 0.1542
    )
    const val DIFFICULTY_MIN = 1.0
    const val DIFFICULTY_MAX = 10.0

    /** FSRS-6: zanik krzywej zapominania jest trenowalny (w20). */
    val DECAY = -W[20]

    /** Dobrany tak, by R(t = S) = 0,9 — to definicja stabilności, nie docelowa retencja. */
    val FACTOR = 0.9.pow(1.0 / DECAY) - 1.0
    const val DESIRED_RETENTION = 0.9
    const val MIN_INTERVAL_DAYS = 0.1
    const val MAX_INTERVAL_DAYS = 3650.0
    const val MASTERED_MIN_REPS = 3
    const val DAY_MS = 86_400_000L
}

object Ratings {
    const val AGAIN = 1
    const val HARD = 2
    const val GOOD = 3
    const val EASY = 4
    const val MIN = 1
    const val MAX = EASY
}

object MatchConstants {
    const val WORD_MATCH_MIN = 0.5
}

object StudyConstants {
    const val PEEK_HOLD_THRESHOLD_MS = 500L
    const val PREVIOUS_GOES_BACK_WINDOW_MS = 3000L
    const val DYNAMIC_PAUSE_MS_PER_CHAR = 60
    const val STT_SOFT_TIMEOUT_MS_BASE = 5000L
    const val STT_HARD_EXTRA_MS = 10_000L
    const val STT_TIMEOUT_TTS_FACTOR = 3
    const val BG_WAIT_SUBSTITUTE_MIN = 1500L
    const val BG_WAIT_SUBSTITUTE_MAX = 15_000L
    const val BG_WAIT_MS_PER_CHAR = 120
    const val STEP_DEFAULT_THRESHOLD = 70
    const val STEP_DEFAULT_WAIT_MS = 500L
    const val MAX_PAUSE_MULTIPLIER = 5.0
    const val MAX_THRESHOLD_PERCENT = 100

    // Wartości wbudowanych trybów są porównywane z zapisanymi — nie zmieniać.
    const val PAUSE_AFTER_CORRECT_MS = 2000L
    const val PAUSE_AFTER_WRONG_MS = 2200L
    const val PAUSE_BEFORE_RETRY_MS = 800L
}

object TtsRate {
    const val MIN = 0.5f
    const val MAX = 2.0f
    const val DEFAULT = 1.0f

    fun clamp(rate: Float): Float = rate.coerceIn(MIN, MAX)
}

object ImportConstants {
    const val SEP_DETECT_ROWS = 5
    const val HEADER_CONFIDENCE_MIN = 0.5
    val LANG_CODE_REGEX = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$")
}

object ArchiveConstants {
    const val RETENTION_DAYS = 14
    const val RETENTION_MS = RETENTION_DAYS * SrsConstants.DAY_MS
}
