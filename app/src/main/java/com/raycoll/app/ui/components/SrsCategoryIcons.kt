package com.raycoll.app.ui.components

import androidx.annotation.DrawableRes
import com.raycoll.app.R
import com.raycoll.app.domain.srs.SrsCardCategory

/**
 * Ikona kategorii karty (Material Symbols) — ta sama w filtrze przeglądania i w statystykach;
 * null = wszystkie karty.
 */
@DrawableRes
fun srsCategoryIcon(category: SrsCardCategory?): Int = when (category) {
    null -> R.drawable.ic_style
    SrsCardCategory.NEW -> R.drawable.ic_auto_awesome
    SrsCardCategory.LEARNING -> R.drawable.ic_menu_book
    SrsCardCategory.REVIEW -> R.drawable.ic_history
    SrsCardCategory.MASTERED -> R.drawable.ic_school
}
