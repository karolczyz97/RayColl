package com.raycoll.app.ui.dashboard

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.raycoll.app.R
import com.raycoll.app.ui.theme.Spacing

/** Gest, podniesienie karty, przesuwanie sąsiadów i przewijanie przy krawędzi zapewnia AndroidX. */
@Composable
internal fun DeckList(
    decks: List<DeckSummary>,
    resetKey: Int,
    onReorder: (List<String>) -> Unit,
    onDragStarted: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (DeckSummary, onStartDrag: () -> Unit) -> Unit
) {
    val composition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val currentReorder by rememberUpdatedState(onReorder)
    val currentDragStarted by rememberUpdatedState(onDragStarted)
    val adapter = remember(composition) {
        DeckListAdapter(composition) { deck, startDrag -> currentContent(deck, startDrag) }
    }
    val touchHelper = remember(adapter) {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun isLongPressDragEnabled() = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    // ComposeView blokuje przechwytywanie dotyku; od tej chwili gest prowadzi RecyclerView.
                    (viewHolder?.itemView?.parent as? RecyclerView)?.apply {
                        requestDisallowInterceptTouchEvent(false)
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                    adapter.startDrag()
                    currentDragStarted()
                } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    adapter.finishDrag(currentReorder)
                }
            }
        })
    }
    adapter.onStartDrag = touchHelper::startDrag
    val density = LocalDensity.current
    val horizontalPadding = with(density) { Spacing.screen.roundToPx() }
    val topPadding = with(density) { Spacing.s.roundToPx() }
    val bottomPadding = with(density) { (Spacing.fabClearance - Spacing.item).roundToPx() }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            RecyclerView(context).apply {
                id = R.id.dashboard_deck_list
                // Rozmiar listy ustala ekran, nie zawartość kafelków.
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
                adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                clipToPadding = false
                // Zmiana trybu czy statystyk ma odświeżyć kafelek w miejscu; animacja zmiany podstawiała kopię i szarpała sąsiadów.
                (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
                touchHelper.attachToRecyclerView(this)
            }
        },
        update = { list ->
            list.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding)
            adapter.update(decks, resetKey)
        },
        onRelease = { list ->
            touchHelper.attachToRecyclerView(null)
            list.adapter = null
        }
    )
}

internal class DeckListAdapter(
    composition: CompositionContext,
    content: @Composable (DeckSummary, onStartDrag: () -> Unit) -> Unit
) : RecyclerView.Adapter<DeckListAdapter.Holder>() {
    private val composition = composition
    private val content = content
    var onStartDrag: (RecyclerView.ViewHolder) -> Unit = {}
    private var decks = emptyList<DeckSummary>()
    private val deckStates = mutableMapOf<String, MutableState<DeckSummary>>()
    private var sourceIds = emptyList<String>()
    private var dragStartIds: List<String>? = null
    private var pendingIds: List<String>? = null
    private var resetKey = 0

    fun update(incoming: List<DeckSummary>, resetKey: Int) {
        sourceIds = incoming.map { it.id }
        val incomingIds = sourceIds.toSet()
        val confirmed = pendingIds?.let { pending ->
            pending.filter { it in incomingIds } == sourceIds.filter { it in pending }
        } ?: false
        if (this.resetKey != resetKey || confirmed) {
            pendingIds = null
        }
        this.resetKey = resetKey
        // Aktualizacja statystyk w trakcie gestu lub oczekiwania na zapis nie cofa kolejności pod palcem.
        val order = if (dragStartIds != null) decks.map { it.id } else pendingIds
        val next = if (order == null) {
            incoming
        } else {
            val byId = incoming.associateBy { it.id }
            val orderedIds = order.toSet()
            order.mapNotNull(byId::get) + incoming.filter { it.id !in orderedIds }
        }
        if (next == decks) return
        // Compose aktualizuje treść istniejącej karty. notifyItemChanged uruchamiał ponowne wiązanie
        // i pomiar ComposeView przez RecyclerView, mimo że pozycja zestawu się nie zmieniła.
        next.forEach { deck ->
            deckStates.getOrPut(deck.id) { mutableStateOf(deck) }.value = deck
        }
        deckStates.keys.retainAll(incomingIds)
        val previous = decks
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition].id == next[newItemPosition].id

            // Zmiany treści obserwuje Compose; lista animuje tylko zmiany swojej struktury.
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = true
        })
        decks = next
        diff.dispatchUpdatesTo(this)
    }

    fun startDrag() {
        dragStartIds = decks.map { it.id }
    }

    fun move(from: Int, to: Int): Boolean {
        if (from !in decks.indices || to !in decks.indices || from == to) return false
        decks = decks.toMutableList().apply { add(to, removeAt(from)) }
        notifyItemMoved(from, to)
        return true
    }

    fun finishDrag(onReorder: (List<String>) -> Unit) {
        val before = dragStartIds ?: return
        dragStartIds = null
        val after = decks.map { it.id }
        if (before == after) return
        pendingIds = after.takeIf { it != sourceIds }
        onReorder(after)
    }

    override fun getItemCount() = decks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        ComposeView(parent.context).apply {
            layoutParams =
                RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setParentCompositionContext(composition)
        },
        content,
        onStartDrag = { onStartDrag(it) }
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.deckState = deckStates.getValue(decks[position].id)
    }

    override fun onViewRecycled(holder: Holder) {
        holder.deckState = null
    }

    class Holder(
        view: ComposeView,
        content: @Composable (DeckSummary, onStartDrag: () -> Unit) -> Unit,
        onStartDrag: (Holder) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        var deckState by mutableStateOf<State<DeckSummary>?>(null)
        val deck: DeckSummary? get() = deckState?.value

        init {
            view.setContent {
                deck?.let { deck ->
                    key(deck.id) {
                        Box(Modifier.padding(bottom = Spacing.item)) { content(deck) { onStartDrag(this@Holder) } }
                    }
                }
            }
        }
    }
}
