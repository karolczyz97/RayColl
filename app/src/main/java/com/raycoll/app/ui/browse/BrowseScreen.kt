package com.raycoll.app.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.ui.components.CardStatsBar
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.EmptyState
import com.raycoll.app.ui.components.ErrorState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SingleChoiceButtonGroup
import com.raycoll.app.ui.components.SwipeToDeleteItem
import com.raycoll.app.ui.components.rememberSnackbarMessages
import com.raycoll.app.ui.components.srsCategoryIcon
import com.raycoll.app.ui.navigation.sharedDeckTitle
import com.raycoll.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Co edytuje okno karty: nową kartę albo istniejącą (po id — przeżywa obrót ekranu). */
private sealed interface CardEditTarget {
    val draftKey: String

    data object New : CardEditTarget {
        override val draftKey = ""
    }

    data class Existing(val cardId: String) : CardEditTarget {
        override val draftKey get() = cardId
    }
}

private val CardEditTargetSaver = Saver<CardEditTarget?, String>(
    save = { it?.draftKey },
    restore = { key -> if (key.isEmpty()) CardEditTarget.New else CardEditTarget.Existing(key) }
)

@Composable
fun BrowseScreen(onBack: () -> Unit, viewModel: BrowseViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.search.collectAsStateWithLifecycle()
    var editTarget by rememberSaveable(stateSaver = CardEditTargetSaver) { mutableStateOf(null) }
    // karta przywrócona po „Cofnij” — wjeżdża z powrotem na listę
    var restoredCardId by rememberSaveable { mutableStateOf<String?>(null) }
    var hintPlayed by rememberSaveable { mutableStateOf(false) }
    val swipeHintsLeft by viewModel.swipeHintsLeft.collectAsStateWithLifecycle()
    var saveAttempt by rememberSaveable { mutableIntStateOf(0) }
    val ready = uiState as? BrowseUiState.Ready
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberSnackbarMessages(snackbarHostState)
    val cardMissing = stringResource(R.string.browse_card_missing)
    val saveFailed = stringResource(R.string.errors_save_failed)
    val cardAdded = stringResource(R.string.browse_card_added)
    val cardDeleted = stringResource(R.string.browse_card_deleted)
    val undo = stringResource(R.string.btn_undo)
    val scope = rememberCoroutineScope()

    CollectEvents(viewModel.events) { event ->
        when (event) {
            BrowseEvent.Saved -> editTarget = null

            BrowseEvent.CardAdded -> {
                editTarget = null
                messages.showLatest(cardAdded)
            }

            BrowseEvent.CardMissing -> messages.showLatest(cardMissing)

            BrowseEvent.SaveFailed -> {
                saveAttempt++
                messages.showLatest(saveFailed)
            }

            // każde kolejne usunięcie zastępuje poprzedni komunikat (jak w Gmailu)
            is BrowseEvent.CardDeleted -> scope.launch {
                if (messages.showAction(cardDeleted, undo, key = "cardDeleted") == SnackbarResult.ActionPerformed) {
                    restoredCardId = event.cardId
                    viewModel.restoreCard(event.cardId)
                }
            }
        }
    }

    ScreenScaffold(
        title = ready?.deckName ?: stringResource(R.string.route_browse),
        titleModifier = Modifier.sharedDeckTitle(viewModel.deckId),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (ready != null) {
                FloatingActionButton(
                    onClick = { editTarget = CardEditTarget.New },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.browse_add_card)
                    )
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (val state = uiState) {
            BrowseUiState.Loading -> LoadingState(contentModifier)

            BrowseUiState.DeckMissing -> ErrorState(stringResource(R.string.errors_deck_missing), contentModifier)

            is BrowseUiState.Ready -> BrowseContent(
                state = state,
                query = query,
                onSearch = viewModel::setSearch,
                onCategory = viewModel::setCategory,
                onEdit = { editTarget = CardEditTarget.Existing(it.id) },
                onDelete = { card, swiped -> viewModel.deleteCard(card.id, swiped) },
                onAdd = { editTarget = CardEditTarget.New },
                hintCardId = state.cards.firstOrNull()?.id?.takeIf { swipeHintsLeft > 0 && !hintPlayed },
                onHintShown = {
                    hintPlayed = true
                    viewModel.swipeHintShown()
                },
                restoredCardId = restoredCardId,
                onRestoredShown = { restoredCardId = null },
                modifier = contentModifier.imePadding()
            )
        }
    }

    if (ready != null) {
        when (val target = editTarget) {
            null -> Unit

            CardEditTarget.New -> CardEditorDialog(
                title = stringResource(R.string.browse_add_card),
                draftKey = target.draftKey,
                autoFocusFirst = true,
                saveAttempt = saveAttempt,
                initialPages = List(ready.activePageCount) { "" },
                pageNames = ready.pageNames,
                onDismiss = { editTarget = null },
                onSave = { pages ->
                    viewModel.addCard(pages)
                }
            )

            is CardEditTarget.Existing -> {
                val card = ready.allCards.find { it.id == target.cardId }
                if (card == null) {
                    // karta zniknęła (usunięta na innym urządzeniu) — okno nie ma czego edytować
                    LaunchedEffect(target) { editTarget = null }
                } else {
                    CardEditorDialog(
                        title = stringResource(R.string.browse_edit_card),
                        draftKey = target.draftKey,
                        saveAttempt = saveAttempt,
                        initialPages = List(ready.activePageCount) { card.pages.getOrElse(it) { "" } },
                        pageNames = ready.pageNames,
                        onDismiss = { editTarget = null },
                        onSave = { visiblePages ->
                            // edytujemy tylko widoczne strony; ukryte zostają bez zmian
                            viewModel.updateCard(card.id, visiblePages)
                        },
                        onDelete = {
                            editTarget = null
                            viewModel.deleteCard(card.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseContent(
    state: BrowseUiState.Ready,
    query: String,
    onSearch: (String) -> Unit,
    onCategory: (SrsCardCategory?) -> Unit,
    onEdit: (Flashcard) -> Unit,
    onDelete: (card: Flashcard, swiped: Boolean) -> Unit,
    onAdd: () -> Unit,
    hintCardId: String?,
    onHintShown: () -> Unit,
    restoredCardId: String?,
    onRestoredShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    // nagłówek jest elementem listy (przewija się razem z nią, w poziomie zostaje miejsce na karty);
    // jedna lista także przy braku wyników — pole wyszukiwania nie jest odtwarzane i nie traci fokusu
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.item)
    ) {
        item(key = "header") {
            Column {
                CardStatsBar(state.counts, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s))
                BrowseSearchField(
                    query = query,
                    onSearch = onSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen)
                )
                CategoryChips(state, onCategory)
            }
        }
        if (state.cards.isEmpty()) {
            item(key = "empty") {
                val deckEmpty = state.counts.total == 0
                EmptyState(
                    message = stringResource(if (deckEmpty) R.string.browse_empty_deck else R.string.browse_no_cards),
                    modifier = Modifier.fillParentMaxWidth(),
                    action = if (deckEmpty) {
                        {
                            Button(onClick = onAdd, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.browse_add_card))
                            }
                        }
                    } else {
                        {
                            Button(onClick = {
                                onSearch("")
                                onCategory(null)
                            }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.browse_clear_filters))
                            }
                        }
                    }
                )
            }
        } else {
            items(state.cards, key = { it.id }) { card ->
                SwipeToDeleteItem(
                    onDelete = { onDelete(card, true) },
                    deleteLabel = stringResource(R.string.btn_delete),
                    shape = CardDefaults.elevatedShape,
                    showHint = card.id == hintCardId,
                    onHintShown = onHintShown,
                    enterFromStart = card.id == restoredCardId,
                    onEntered = onRestoredShown,
                    modifier = Modifier
                        .animateItem(
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec()
                        )
                        .padding(horizontal = Spacing.screen)
                ) {
                    CardItem(card = card, activePageCount = state.activePageCount, onEdit = { onEdit(card) })
                }
            }
        }
    }
}

private class CategoryOption(val category: SrsCardCategory?, val name: String, val count: Int)

/**
 * Filtr kategorii jako połączona grupa przycisków. Gdy nazwy z liczbą nie mieszczą się w jednym rzędzie
 * (zwykły telefon w pionie), przyciski pokazują ikonę i liczbę; nazwa zostaje dla czytnika ekranu.
 */
@Composable
private fun CategoryChips(state: BrowseUiState.Ready, onCategory: (SrsCardCategory?) -> Unit) {
    val counts = state.counts
    val options = listOf(
        CategoryOption(null, stringResource(R.string.filter_all), counts.total),
        CategoryOption(
            SrsCardCategory.NEW,
            stringResource(R.string.srs_badge_new),
            counts.newCount
        ),
        CategoryOption(
            SrsCardCategory.LEARNING,
            stringResource(R.string.srs_badge_learning),
            counts.learning
        ),
        CategoryOption(
            SrsCardCategory.REVIEW,
            stringResource(R.string.srs_badge_review),
            counts.review
        ),
        CategoryOption(
            SrsCardCategory.MASTERED,
            stringResource(R.string.srs_badge_mastered),
            counts.mastered
        )
    )
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s)) {
        val labelWidths = options.map { option ->
            with(density) {
                measurer.measure("${option.name} ${option.count}", labelStyle, maxLines = 1).size.width.toDp()
            }
        }
        val needed = labelWidths.sumOf { (it + CategoryLabelPadding * 2).value.toDouble() } +
            ButtonGroupDefaults.ConnectedSpaceBetween.value * (options.size - 1)
        val iconsOnly = needed > maxWidth.value
        val byCategory = options.associateBy { it.category }
        SingleChoiceButtonGroup(
            options = options.map { it.category to it.name },
            selected = state.category,
            onSelect = onCategory,
            compact = true,
            weight = { index -> if (iconsOnly) 1f else labelWidths[index].value }
        ) { category, _ ->
            val option = byCategory.getValue(category)
            val description = stringResource(R.string.filter_chip_count, option.name, option.count)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.clearAndSetSemantics { contentDescription = description }
            ) {
                if (iconsOnly) {
                    Icon(
                        painterResource(srsCategoryIcon(option.category)),
                        contentDescription = null,
                        modifier = Modifier.size(CategoryIconSize)
                    )
                } else {
                    Text(option.name, style = labelStyle, maxLines = 1)
                }
                Text(option.count.toString(), style = labelStyle, maxLines = 1)
            }
        }
    }
}

private val CategoryLabelPadding = Spacing.s
private val CategoryIconSize = 18.dp

/** Pełny tekst strony widać w edytorze po dotknięciu karty. */
private const val MAX_PAGE_LINES = 4

/** Karta na liście: dotknięcie otwiera edycję, przesunięcie w lewo usuwa. */
@Composable
private fun CardItem(card: Flashcard, activePageCount: Int, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val editLabel = stringResource(R.string.browse_edit_card)
    RayCollCard(onClick = onEdit, onClickLabel = editLabel, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(Spacing.l)
            ) {
                card.pages.take(activePageCount).forEachIndexed { index, page ->
                    val isFirst = index == 0
                    if (!isFirst) {
                        HorizontalDivider(
                            Modifier.padding(vertical = Spacing.s),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    Text(
                        page,
                        style = if (isFirst) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (isFirst) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = MAX_PAGE_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
