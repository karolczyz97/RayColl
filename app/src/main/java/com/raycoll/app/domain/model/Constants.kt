package com.raycoll.app.domain.model

object PageLimits {
    const val MIN_PAGE_COUNT = 2
    const val MAX_VISIBLE_PAGE_COUNT = 5
    const val MAX_STORED_PAGE_COUNT = 20

    fun clampActivePageCount(candidate: Int, upperBound: Int): Int =
        maxOf(MIN_PAGE_COUNT, minOf(upperBound, candidate.coerceAtLeast(0)))
}

object SrsConstants {
    val W = doubleArrayOf(
        0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01,
        1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61
    )
    const val DECAY = -0.5
    val FACTOR = 0.9.pow(1.0 / DECAY) - 1.0
    const val MIN_INTERVAL_DAYS = 0.1
    const val MAX_INTERVAL_DAYS = 3650.0
    const val MASTERED_MIN_REPS = 3
    const val DAY_MS = 86_400_000L

    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)
}

object MatchConstants {
    const val MATCH_EASY = 85
    const val MATCH_GOOD = 60
    const val MATCH_HARD = 40
    const val WORD_MATCH_MIN = 0.5
}

object StudyConstants {
    const val STUDY_REVIEW_FLUSH_THRESHOLD = 10
    const val PERSISTENCE_DEBOUNCE_MS = 1200L
    const val PEEK_HOLD_THRESHOLD_MS = 500L
    const val PREVIOUS_GOES_BACK_WINDOW_MS = 3000L
    const val DYNAMIC_PAUSE_MS_PER_CHAR = 60
    const val STT_SOFT_TIMEOUT_MS_BASE = 5000L
    const val STT_HARD_EXTRA_MS = 10_000L
    const val BG_WAIT_SUBSTITUTE_MIN = 1500L
    const val BG_WAIT_SUBSTITUTE_MAX = 15_000L
    const val BG_WAIT_MS_PER_CHAR = 120
    const val CARD_FONT_TIER_1 = 70
    const val CARD_FONT_TIER_2 = 140
    const val CARD_FONT_TIER_3 = 260
    const val ARCHIVE_RETENTION_DAYS = 14
    const val ARCHIVE_PURGE_INTERVAL_MS = 3_600_000L
    const val STREAK_MAX_DAYS = 365
    const val TTS_RATE_MIN = 0.5f
    const val TTS_RATE_MAX = 2.0f
    const val TTS_RATE_DEFAULT = 1.0f
    const val STEP_DEFAULT_THRESHOLD = 70
    const val STEP_DEFAULT_WAIT_MS = 500
    const val STEP_DEFAULT_RATING = 3
    const val MAX_PAUSE_MULTIPLIER = 5
    const val RATING_MIN = 1
    const val RATING_MAX = 4
}

object ImportConstants {
    val SEPARATORS = listOf("\t", ";", ",", "|")
    const val SEPARATOR_DEFAULT = ";"
    const val SEP_DETECT_ROWS = 5
    const val HEADER_CONFIDENCE_MIN = 0.5
    val LANG_CODE_REGEX = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})?$")
    const val BACKUP_FILENAME_PATTERN = "raycoll-backup-yyyy-MM-dd-HH-mm.json"
}

object ArchiveConstants {
    const val RETENTION_DAYS = 14
    val DELETE_TOKENS = mapOf(
        "pl" to "USUŃ",
        "en" to "DELETE",
        "de" to "LÖSCHEN",
        "es" to "BORRAR",
        "it" to "ELIMINA",
    )
}
