package com.raycoll.app.ui.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import com.raycoll.app.R
import com.raycoll.app.domain.model.activePages
import com.raycoll.app.domain.session.CardDestination
import com.raycoll.app.domain.session.CardNavigation
import com.raycoll.app.domain.session.LastAnswerResult
import com.raycoll.app.domain.session.SessionState
import com.raycoll.app.domain.session.SessionStatus
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private class PagerNavigation {
    var navigation: CardNavigation? = null
    var settleJob: Job? = null
}

/** Pager rysuje podglądy. Tylko rozstrzygnięty gest wysyła pojedynczą komendę do silnika. */
@Composable
internal fun StudyPager(state: StudyUiState.Active, pager: PagerState, actions: CardActions, modifier: Modifier) {
    val session = state.session
    val latestSession by rememberUpdatedState(session)
    val latestActions by rememberUpdatedState(actions)
    val coordinator = remember(pager) { PagerNavigation() }
    DisposableEffect(pager) {
        onDispose {
            coordinator.settleJob?.cancel()
            coordinator.navigation?.let { latestActions.onNavigationEnd(it, null) }
            coordinator.navigation = null
        }
    }
    LaunchedEffect(pager) {
        coroutineScope {
            pager.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        coordinator.settleJob?.cancel()
                        val previous = coordinator.navigation
                        if (previous != null && !latestActions.onNavigationContinue(previous)) {
                            coordinator.navigation = null
                        }
                        if (coordinator.navigation == null) {
                            val s = latestSession
                            val card = s.cards.getOrNull(s.currentCardIndex)
                            if (card != null) {
                                coordinator.navigation = latestActions.onNavigationStart(s.sessionId, card.id)
                            }
                        }
                    }

                    is DragInteraction.Stop, is DragInteraction.Cancel -> {
                        val cancelled = interaction is DragInteraction.Cancel
                        coordinator.settleJob?.cancel()
                        coordinator.settleJob = launch {
                            // Pager musi rozpocząć domykanie gestu, zanim sprawdzimy jego końcową stronę.
                            withFrameNanos { }
                            snapshotFlow { pager.isScrollInProgress }.first { !it }
                            val selected = pager.settledPage
                            val navigation = coordinator.navigation
                            coordinator.navigation = null
                            val s = latestSession
                            if (navigation != null) {
                                val destination = if (cancelled) {
                                    null
                                } else {
                                    s.cards.getOrNull(selected)?.let { CardDestination.Card(it.id) }
                                        ?: CardDestination.Summary
                                }
                                val accepted = latestActions.onNavigationEnd(navigation, destination)
                                    ?: latestSession.currentCardIndex
                                if (accepted != selected && accepted in 0 until pager.pageCount) {
                                    pager.animateScrollToPage(accepted)
                                }
                            } else if (selected != s.currentCardIndex) {
                                pager.animateScrollToPage(s.currentCardIndex)
                            }
                        }
                    }
                }
            }
        }
    }
    // Ocena, przycisk i automatyka przesuwają widok do już wybranej karty, bez drugiej komendy nawigacji.
    LaunchedEffect(pager, session.currentCardIndex) {
        if (coordinator.navigation == null) pager.animateScrollToPage(session.currentCardIndex)
    }
    HorizontalPager(
        state = pager,
        modifier = modifier,
        beyondViewportPageCount = 1,
        key = { page -> session.cards.getOrNull(page)?.id ?: Long.MIN_VALUE },
        verticalAlignment = Alignment.Top
    ) { page ->
        if (page == session.cards.size) {
            SummaryPreview()
        } else {
            StudyPagerPage(
                session = session.forDisplay(page),
                pageCount = state.activePageCount,
                active = page == session.currentCardIndex,
                actions = actions
            )
        }
    }
}

/** Odwiedzony sąsiad korzysta z zapamiętanego wyglądu; nowy pokazuje wyłącznie pytanie. */
private fun SessionState.forDisplay(index: Int): SessionState {
    if (index == currentCardIndex) return this
    val saved = cardHistory[cards[index].id]
    return copy(
        currentCardIndex = index,
        status = SessionStatus.IDLE,
        revealedPages = saved?.revealedPages ?: listOf(0),
        lastAnswerResult = saved?.answer ?: LastAnswerResult(),
        previousAnswerResult = LastAnswerResult(),
        peekedPageIndex = null,
        audioPageIndex = null
    )
}

@Composable
private fun StudyPagerPage(session: SessionState, pageCount: Int, active: Boolean, actions: CardActions) {
    val card = session.cards[session.currentCardIndex]
    val scroll = rememberScrollState()
    val userScroll = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val scrollInput = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) userScroll.tryEmit(Unit)
                return Offset.Zero
            }
        }
    }
    var previousRevealCount by remember { mutableIntStateOf(session.revealedPages.size) }
    LaunchedEffect(active, session.revealedPages.size) {
        val revealedMore = session.revealedPages.size > previousRevealCount
        previousRevealCount = session.revealedPages.size
        if (active && revealedMore) {
            withTimeoutOrNull(700L) {
                coroutineScope {
                    val follow = launch {
                        snapshotFlow { scroll.maxValue }.collectLatest { scroll.animateScrollTo(it) }
                    }
                    try {
                        userScroll.first()
                    } finally {
                        follow.cancel()
                    }
                }
            }
        }
    }
    val compact = LocalCompactCard.current
    val gap = if (compact) Spacing.s else Spacing.screen
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    Column(
        Modifier
            .fillMaxSize()
            .then(if (active) Modifier else Modifier.clearAndSetSemantics { })
            .nestedScroll(scrollInput)
            .verticalScroll(scroll)
            .padding(horizontal = Spacing.screen, vertical = gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        FlashcardView(
            face = CardFace(
                card = card,
                pages = card.activePages(pageCount),
                revealedPages = session.revealedPages,
                peekedPage = session.peekedPageIndex,
                audioPage = session.audioPageIndex,
                listening = session.status == SessionStatus.LISTENING,
                answerPage = session.lastAnswerResult.pageIndex,
                answerStatus = session.lastAnswerResult.status
            ),
            actions = actions,
            modifier = Modifier.fillMaxWidth(),
            interactive = active
        )
        val panel = answerPanelState(session)
        AnimatedVisibility(
            visible = panel.visible,
            enter = fadeIn(effects) + expandVertically(spatialSize),
            exit = fadeOut(effects) + shrinkVertically(spatialSize)
        ) {
            AnswerPanel(
                answer = panel.answer,
                listening = panel.listening,
                onTap = { if (active && session.navigation == null) actions.onTap() },
                listeningLabel = panel.listeningLabel
            )
        }
    }
}

/** Wyłącznie podgląd ostatniego przejścia; prawdziwe podsumowanie pojawia się po zakończeniu sesji. */
@Composable
private fun SummaryPreview() {
    Column(
        Modifier.fillMaxSize().clearAndSetSemantics { }.padding(Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RayCollCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                stringResource(R.string.study_summary_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(Spacing.xl)
            )
        }
    }
}
