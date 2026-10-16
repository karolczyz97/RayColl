package com.raycoll.app.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raycoll.app.R
import com.raycoll.app.domain.model.Flashcard
import com.raycoll.app.domain.srs.SrsCardCategory
import com.raycoll.app.ui.components.CardStatsBar
import com.raycoll.app.ui.components.ChoiceChip
import com.raycoll.app.ui.components.CollectEvents
import com.raycoll.app.ui.components.ConfirmDialog
import com.raycoll.app.ui.components.EmptyState
import com.raycoll.app.ui.components.ErrorState
import com.raycoll.app.ui.components.LoadingState
import com.raycoll.app.ui.components.RayCollCard
import com.raycoll.app.ui.components.ScreenScaffold
import com.raycoll.app.ui.components.SwipeToDeleteItem
import com.raycoll.app.ui.components.rememberSnackbarMessages
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
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.browse_add_card)) }
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
    val keyboard = LocalSoftwareKeyboardController.current
    // nagłówek jest elementem listy (przewija się razem z nią, w poziomie zostaje miejsce na karty);
    // jedna lista także przy braku wyników — pole wyszukiwania nie jest odtwarzane i nie traci fokusu
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        item(key = "header") {
            Column {
                CardStatsBar(state.counts, Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s))
                OutlinedTextField(
                    value = query,
                    onValueChange = onSearch,
                    placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onSearch("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.browse_clear_search)
                                )
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    shape = MaterialTheme.shapes.small,
                    singleLine = true,
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
                    shape = MaterialTheme.shapes.large,
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

@Composable
private fun CategoryChips(state: BrowseUiState.Ready, onCategory: (SrsCardCategory?) -> Unit) {
    val counts = state.counts
    val chips = listOf(
        null to (R.string.filter_all to counts.total),
        SrsCardCategory.NEW to (R.string.srs_badge_new to counts.newCount),
        SrsCardCategory.LEARNING to (R.string.srs_badge_learning to counts.learning),
        SrsCardCategory.REVIEW to (R.string.srs_badge_review to counts.review),
        SrsCardCategory.MASTERED to (R.string.srs_badge_mastered to counts.mastered)
    )
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        chips.forEach { (category, label) ->
            val (labelRes, count) = label
            ChoiceChip(
                selected = state.category == category,
                onClick = { onCategory(category) },
                label = { Text(stringResource(R.string.filter_chip_count, stringResource(labelRes), count)) }
            )
        }
    }
}

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
