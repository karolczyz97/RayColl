package com.raycoll.app.playback

import com.raycoll.app.domain.session.InteractionGateKind
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus

/** Co pokazać w powiadomieniu; w bezczynności [pausedText] zamiast postępu. */
internal fun sessionPlayback(
    state: SessionState,
    deckName: String,
    idle: Boolean,
    pausedText: String
): SessionPlayback = SessionPlayback(
    title = state.cards.getOrNull(state.currentCardIndex)?.pages?.firstOrNull().orEmpty(),
    deckName = deckName,
    progress = if (idle) pausedText else "${state.currentCardIndex + 1}/${state.cards.size}",
    playing = !idle
)

/**
 * Sesja nic nie odtwarza: pauza albo czekanie na użytkownika (błąd, odsłonięte oceny, tap na
 * pierwszym planie). Wtedy oddajemy fokus audio i wyłączamy ciszę, żeby nie trzymać muzyki/nawigacji.
 * W tle bramka tapu nie czeka — sesja bez rąk idzie dalej.
 */
internal fun sessionAudioIdle(state: SessionState, inBackground: Boolean, manuallyPaused: Boolean): Boolean =
    manuallyPaused ||
        state.status == SessionStatus.ERROR ||
        state.status == SessionStatus.REVEALED ||
        (!inBackground && state.interactionGate.kind != InteractionGateKind.NONE)
