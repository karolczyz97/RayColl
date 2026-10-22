package com.raycoll.app.ui.components

import androidx.compose.animation.core.snap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.TestMonotonicFrameClock
import com.raycoll.app.core.moved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Logika przeciągania bez ekranu: układ listy jest atrapą, która od razu układa się po każdej zamianie. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ReorderControllerTest {
    private class FakeList(private val sizes: List<Float>, private val gap: Float = 8f) {
        var order: List<Int> = sizes.indices.toList()
        val moves = mutableListOf<Pair<Int, Int>>()
        val drops = mutableListOf<Pair<Int, Int>>()

        fun slots(): List<ReorderSlot> {
            var top = 0f
            return order.mapIndexed { index, key ->
                ReorderSlot(key, index, top, sizes[key]).also {
                    top +=
                        sizes[key] + gap
                }
            }
        }

        fun move(from: Int, to: Int) {
            moves += from to to
            order = order.moved(from, to)
        }
    }

    private fun kotlinx.coroutines.test.TestScope.controller(list: FakeList) = ReorderController(
        scope = CoroutineScope(backgroundScope.coroutineContext + TestMonotonicFrameClock(this)),
        spec = snap(),
        slots = list::slots,
        onMove = list::move,
        onDrop = { from, to -> list.drops += from to to }
    )

    @Test
    fun `a short card dragged over a tall one does not jump back and forth`() = runTest {
        val list = FakeList(listOf(140f, 300f, 140f))
        val controller = controller(list)
        controller.start(0)

        controller.drag(0, 100f)
        assertEquals("below the middle of the tall card nothing moves yet", emptyList<Pair<Int, Int>>(), list.moves)

        controller.drag(0, 60f)
        assertEquals(listOf(0 to 1), list.moves)
        assertEquals(listOf(1, 0, 2), list.order)

        // ten sam palec w tym samym miejscu i jeszcze kilka drobnych ruchów: żadnej zamiany z powrotem
        repeat(5) { controller.drag(0, 1f) }
        assertEquals(listOf(0 to 1), list.moves)
    }

    @Test
    fun `dragging back up swaps once the top edge passes the middle of the neighbour`() = runTest {
        val list = FakeList(listOf(140f, 300f, 140f))
        val controller = controller(list)
        controller.start(2)

        controller.drag(2, -100f)
        assertEquals(emptyList<Pair<Int, Int>>(), list.moves)
        controller.drag(2, -100f)

        assertEquals(listOf(2 to 1), list.moves)
    }

    @Test
    fun `fast dragging across several items moves to the furthest one passed`() = runTest {
        val list = FakeList(listOf(100f, 100f, 100f, 100f))
        val controller = controller(list)
        controller.start(0)

        // dół karty (350) minął środek drugiej (266), ale nie trzeciej (374)
        controller.drag(0, 250f)

        assertEquals(listOf(0 to 2), list.moves)
        assertEquals(listOf(1, 2, 0, 3), list.order)
    }

    @Test
    fun `a handle of another item cannot move or end the drag`() = runTest {
        val list = FakeList(listOf(100f, 100f, 100f))
        val controller = controller(list)
        controller.start(0)

        controller.drag(1, 400f)
        controller.end(1)

        assertEquals(emptyList<Pair<Int, Int>>(), list.moves)
        assertEquals(0, controller.draggedKey)
        controller.drag(0, 150f)
        assertEquals(listOf(0 to 1), list.moves)
    }

    @Test
    fun `dropping reports one move from where the item started to where it ended`() = runTest {
        val list = FakeList(listOf(100f, 100f, 100f, 100f))
        val controller = controller(list)
        controller.start(0)
        controller.drag(0, 120f)
        controller.drag(0, 100f)

        controller.end(0)

        assertEquals(listOf(0 to 2), list.drops)
        assertNull(controller.draggedKey)
    }

    @Test
    fun `dropping where the item started reports nothing`() = runTest {
        val list = FakeList(listOf(100f, 100f))
        val controller = controller(list)
        controller.start(0)
        controller.drag(0, 40f)
        controller.drag(0, -40f)

        controller.end(0)

        assertEquals(emptyList<Pair<Int, Int>>(), list.drops)
    }
}
