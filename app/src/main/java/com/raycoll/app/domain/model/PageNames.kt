package com.raycoll.app.domain.model

/** Fallback dla danych bez nazwy; ekrany przekazują nazwę w języku aplikacji. */
fun defaultPageName(index: Int): String = "Page ${index + 1}"
