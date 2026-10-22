package com.raycoll.app.core

/** Kopia listy z elementem z miejsca [from] przeniesionym na miejsce [to]; pozostałe przesuwają się o jedno. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }
