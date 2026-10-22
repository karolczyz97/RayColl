package com.raycoll.app.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.raycoll.app.core.moved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Przestawianie elementów list przytrzymaniem i przeciągnięciem: element unosi się pod palcem,
// sąsiedzi rozsuwają się, a przy krawędzi lista sama się przewija. Pulpit robi to samo przez
// RecyclerView (DeckList); tu ten sam efekt dla LazyColumn i zwykłej kolumny.

private val LiftElevation = 8.dp
private val EdgeZone = 72.dp
private const val MAX_EDGE_SCROLL_PX = 28f

/** Miejsce elementu na liście: [index] liczy się wśród samych przestawianych elementów, [top] od wspólnego początku. */
internal class ReorderSlot(val key: Any, val index: Int, val top: Float, val size: Float)

/** Przewijanie listy w trakcie przeciągania; [zone] to pas przy krawędzi widoku, w którym lista rusza. */
internal class EdgeScroll(
    val viewport: () -> ClosedFloatingPointRange<Float>,
    val scrollBy: suspend (Float) -> Float,
    val zone: Float
)

/**
 * Wysokości wierszy zwykłej kolumny, w której nie ma układu leniwej listy. Z nich liczymy miejsca wierszy,
 * a [gap] to przerwa między nimi.
 */
internal class ColumnSizes(val gap: Float) {
    val heights: SnapshotStateMap<Any, Float> = mutableStateMapOf()
}

/**
 * Stan przeciągania jednej listy. [slots] opisuje bieżący układ, [onMove] dostaje każdą zamianę miejsc na żywo,
 * a [onDrop] — jedną łączną zmianę po puszczeniu (dla list, których zapis jest kosztowny).
 * [columnSizes] włącza własne rozsuwanie sąsiadów w kolumnie, która sama nie animuje pozycji.
 */
@Stable
class ReorderController internal constructor(
    private val scope: CoroutineScope,
    private val spec: FiniteAnimationSpec<Float>,
    private val slots: () -> List<ReorderSlot>,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: ((from: Int, to: Int) -> Unit)? = null,
    private val onLift: () -> Unit = {},
    private val edgeScroll: EdgeScroll? = null,
    private val columnSizes: ColumnSizes? = null
) {
    var draggedKey: Any? by mutableStateOf(null)
        private set
    private var releasedKey: Any? by mutableStateOf(null)
    private var startTop = 0f
    private var pointerDelta by mutableFloatStateOf(0f)
    private var originIndex = 0
    private var currentIndex = 0
    private var orderAfterMove: List<Any>? = null
    private var lift by mutableFloatStateOf(0f)
    private var releaseOffset by mutableFloatStateOf(0f)
    private val slides = mutableStateMapOf<Any, Float>()
    private val slideJobs = mutableMapOf<Any, Job>()
    private var liftJob: Job? = null
    private var releaseJob: Job? = null
    private var edgeJob: Job? = null

    internal fun isActive(key: Any): Boolean = key == draggedKey || key == releasedKey

    internal fun sized(key: Any, height: Float) {
        val heights = columnSizes?.heights ?: return
        if (heights[key] != height) heights[key] = height
    }

    internal fun liftOf(key: Any): Float = if (isActive(key)) lift else 0f

    /** Przesunięcie względem miejsca, które element zajmuje w układzie. */
    internal fun translationOf(key: Any): Float = when (key) {
        draggedKey -> startTop + pointerDelta - (slots().firstOrNull { it.key == key }?.top ?: startTop)
        releasedKey -> releaseOffset
        else -> slides[key] ?: 0f
    }

    internal fun start(key: Any) {
        if (draggedKey != null) return
        val slot = slots().firstOrNull { it.key == key } ?: return
        releaseJob?.cancel()
        releasedKey = null
        startTop = slot.top
        pointerDelta = 0f
        originIndex = slot.index
        currentIndex = slot.index
        orderAfterMove = null
        draggedKey = key
        onLift()
        liftJob?.cancel()
        liftJob = scope.launch { animate(lift, 1f, animationSpec = spec) { value, _ -> lift = value } }
    }

    /** Ruch palca na uchwycie elementu [key]; uchwyty innych elementów (drugi palec) są ignorowane. */
    internal fun drag(key: Any, delta: Float) {
        if (key != draggedKey) return
        pointerDelta += delta
        swapIfNeeded()
        scrollNearEdge()
    }

    internal fun end(key: Any) {
        if (key != draggedKey) return
        edgeJob?.cancel()
        val residual = translationOf(key)
        draggedKey = null
        releasedKey = key
        releaseOffset = residual
        releaseJob?.cancel()
        releaseJob = scope.launch {
            animate(residual, 0f, animationSpec = spec) { value, _ -> releaseOffset = value }
            releasedKey = null
        }
        liftJob?.cancel()
        liftJob = scope.launch { animate(lift, 0f, animationSpec = spec) { value, _ -> lift = value } }
        if (currentIndex != originIndex) onDrop?.invoke(originIndex, currentIndex)
    }

    /**
     * Zamiana następuje, gdy krawędź przeciąganego elementu minie środek sąsiada. Po zamianie to samo
     * położenie palca nie spełnia warunku zamiany odwrotnej, więc elementy różnej wysokości nie migają.
     */
    private fun swapIfNeeded() {
        val key = draggedKey ?: return
        val all = slots()
        val order = all.map { it.key }
        // poprzednia zamiana jeszcze się nie ułożyła na ekranie
        if (order == orderAfterMove) return
        val dragged = all.firstOrNull { it.key == key } ?: return
        val top = startTop + pointerDelta
        val bottom = top + dragged.size
        val target = all.lastOrNull { it.index > dragged.index && bottom > it.top + it.size / 2 }
            ?: all.firstOrNull { it.index < dragged.index && top < it.top + it.size / 2 }
            ?: return
        orderAfterMove = order
        currentIndex = target.index
        slideNeighbours(dragged, target, all)
        onMove(dragged.index, target.index)
    }

    /** Sąsiedzi zaczynają w starym miejscu i płynnie wsuwają się w nowe. */
    private fun slideNeighbours(dragged: ReorderSlot, target: ReorderSlot, all: List<ReorderSlot>) {
        val gap = columnSizes?.gap ?: return
        val shift = (dragged.size + gap) * if (target.index > dragged.index) 1f else -1f
        val range = minOf(dragged.index, target.index)..maxOf(dragged.index, target.index)
        all.filter { it.key != dragged.key && it.index in range }.forEach { slot ->
            val from = (slides[slot.key] ?: 0f) + shift
            slides[slot.key] = from
            slideJobs[slot.key]?.cancel()
            slideJobs[slot.key] = scope.launch {
                animate(from, 0f, animationSpec = spec) { value, _ -> slides[slot.key] = value }
                slides.remove(slot.key)
            }
        }
    }

    /** Gdy przeciągany element dotyka krawędzi widoku, lista przewija się klatka po klatce, aż odsunie się od niej. */
    private fun scrollNearEdge() {
        val scroll = edgeScroll ?: return
        if (edgeJob?.isActive == true || edgeStep(scroll) == 0f) return
        edgeJob = scope.launch {
            while (draggedKey != null) {
                val step = edgeStep(scroll)
                if (step == 0f) break
                withFrameNanos { }
                // koniec listy: dalsze przewijanie nic nie da, ruch palca uruchomi je ponownie
                if (scroll.scrollBy(step) == 0f) break
                swapIfNeeded()
            }
        }
    }

    /** O ile pikseli przewinąć w tej klatce: ujemnie w górę, dodatnio w dół, 0 poza pasem przy krawędzi. */
    private fun edgeStep(scroll: EdgeScroll): Float {
        val key = draggedKey ?: return 0f
        val size = slots().firstOrNull { it.key == key }?.size ?: return 0f
        val top = startTop + pointerDelta
        val bottom = top + size
        val view = scroll.viewport()
        val upper = view.start + scroll.zone
        val lower = view.endInclusive - scroll.zone
        return when {
            top < upper -> -MAX_EDGE_SCROLL_PX * ((upper - top) / scroll.zone).coerceAtMost(1f)
            bottom > lower -> MAX_EDGE_SCROLL_PX * ((bottom - lower) / scroll.zone).coerceAtMost(1f)
            else -> 0f
        }
    }
}

/**
 * Kontroler dla [androidx.compose.foundation.lazy.LazyColumn]. [keys] to klucze przestawianych pozycji
 * w kolejności listy (nagłówki i przyciski wokół nich nie wchodzą), [onMove] dostaje każdą zamianę na żywo.
 */
@Composable
fun rememberLazyReorderController(
    listState: LazyListState,
    keys: List<Any>,
    onMove: (from: Int, to: Int) -> Unit
): ReorderController {
    val currentKeys by rememberUpdatedState(keys)
    val move by rememberUpdatedState(onMove)
    val haptics = LocalHapticFeedback.current
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val zone = with(LocalDensity.current) { EdgeZone.toPx() }
    val scope = rememberCoroutineScope()
    return remember(listState) {
        ReorderController(
            scope = scope,
            spec = spec,
            slots = {
                val indexOfKey = currentKeys.withIndex().associate { (index, key) -> key to index }
                listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    indexOfKey[info.key]?.let { ReorderSlot(info.key, it, info.offset.toFloat(), info.size.toFloat()) }
                }
            },
            onMove = { from, to -> move(from, to) },
            onLift = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            edgeScroll = EdgeScroll(
                viewport = {
                    val info = listState.layoutInfo
                    info.viewportStartOffset.toFloat()..info.viewportEndOffset.toFloat()
                },
                scrollBy = { listState.scrollBy(it) },
                zone = zone
            )
        )
    }
}

/** Kolejność wierszy w trakcie przeciągania; po puszczeniu [controller] zgłasza jedną zmianę. */
class ColumnReorder internal constructor(val controller: ReorderController, val order: List<Any>)

/**
 * Kontroler dla zwykłej kolumny wierszy o kluczach [keys] (kolejność z danych). Podczas przeciągania zmienia się
 * tylko pokazywana kolejność, a po puszczeniu [onCommit] dostaje jedno przeniesienie. Pokazywana kolejność
 * obowiązuje, dopóki [keys] i [version] się nie zmienią: nowe dane (albo zmieniona [version], np. po nieudanym
 * zapisie) zastępują ją kolejnością z danych.
 */
@Composable
fun rememberColumnReorder(keys: List<Any>, version: Any?, onCommit: (from: Int, to: Int) -> Unit): ColumnReorder {
    val stamp = keys to version
    var reordered by remember { mutableStateOf<Pair<Any, List<Any>>?>(null) }
    val shown = reordered?.takeIf { it.first == stamp }?.second ?: keys
    val currentStamp by rememberUpdatedState(stamp)
    val currentShown by rememberUpdatedState(shown)
    val commit by rememberUpdatedState(onCommit)
    val haptics = LocalHapticFeedback.current
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val gap = with(LocalDensity.current) { ListItemDefaults.SegmentedGap.toPx() }
    val sizes = remember { ColumnSizes(gap) }
    val scope = rememberCoroutineScope()
    val controller = remember {
        ReorderController(
            scope = scope,
            spec = spec,
            slots = {
                var top = 0f
                currentShown.mapIndexedNotNull { index, key ->
                    sizes.heights[key]?.let { size ->
                        ReorderSlot(key, index, top, size).also { top += size + sizes.gap }
                    }
                }
            },
            onMove = { from, to -> reordered = currentStamp to currentShown.moved(from, to) },
            onDrop = { from, to -> commit(from, to) },
            onLift = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
            columnSizes = sizes
        )
    }
    return ColumnReorder(controller, shown)
}

/** Unoszenie, przesuwanie pod palcem i rozmiar elementu; przeciągany element rysuje się nad sąsiadami. */
@Composable
fun Modifier.reorderItem(
    controller: ReorderController,
    key: Any,
    shape: Shape = MaterialTheme.shapes.medium
): Modifier {
    val elevation = with(LocalDensity.current) { LiftElevation.toPx() }
    return this
        .onSizeChanged { controller.sized(key, it.height.toFloat()) }
        .zIndex(if (controller.isActive(key)) 1f else 0f)
        .graphicsLayer {
            translationY = controller.translationOf(key)
            shadowElevation = elevation * controller.liftOf(key)
            this.shape = shape
            clip = false
        }
}

/**
 * Przytrzymanie elementu [key] unosi go, a pionowy ruch palca prowadzi (jak kafelek talii na pulpicie).
 * Element, który zniknie w trakcie gestu, kończy przeciąganie.
 */
@Composable
fun Modifier.reorderOnLongPress(controller: ReorderController, key: Any): Modifier {
    DisposableEffect(controller, key) { onDispose { controller.end(key) } }
    return pointerInput(controller, key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { controller.start(key) },
            onDragEnd = { controller.end(key) },
            onDragCancel = { controller.end(key) },
            onDrag = { change, delta ->
                change.consume()
                controller.drag(key, delta.y)
            }
        )
    }
}
