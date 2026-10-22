package com.raycoll.app.ui.dashboard

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.raycoll.app.domain.model.StudyMode
import com.raycoll.app.domain.selectors.CardStats
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer

@RunWith(RobolectricTestRunner::class)
class DeckListTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var decks by mutableStateOf(List(20) { summary("deck$it") })
    private var resetKey by mutableStateOf(0)
    private var dragStarts = 0
    private var clicks = 0
    private val saved = mutableListOf<List<String>>()

    private fun showList(): RecyclerView {
        compose.setContent {
            DeckList(
                decks,
                resetKey,
                onReorder = { saved.add(it) },
                onDragStarted = { dragStarts++ }
            ) { deck, startDrag ->
                Box(
                    Modifier.fillMaxWidth().height(100.dp)
                        .combinedClickable(onClick = { clicks++ }, onLongClick = startDrag)
                ) { Text("${deck.name}: ${deck.activeMode.name}") }
            }
        }
        compose.waitForIdle()
        return findList(compose.activity.window.decorView)!!
    }

    @Test
    fun `a vertical gesture after returning scrolls immediately and keeps native momentum`() {
        val list = showList()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        val start = SystemClock.uptimeMillis()
        val y = list.height * 0.7f
        touch(list, start, MotionEvent.ACTION_DOWN, y)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        touch(list, start, MotionEvent.ACTION_MOVE, y - 40f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        touch(list, start, MotionEvent.ACTION_MOVE, y - 140f)
        assertTrue("The first gesture must scroll without waiting", list.computeVerticalScrollOffset() > 0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        touch(list, start, MotionEvent.ACTION_UP, y - 240f)
        assertEquals(RecyclerView.SCROLL_STATE_SETTLING, list.scrollState)
        compose.runOnIdle { list.stopScroll() }
        assertEquals(0, dragStarts)
    }

    @Test
    fun `repeated mode changes update cards without native rebinds or moving neighbours`() {
        val list = showList()
        val card = list.findViewHolderForAdapterPosition(0)!!
        val neighbour = list.findViewHolderForAdapterPosition(1)!!
        val neighbourTop = neighbour.itemView.top
        var nativeChanges = 0
        list.adapter!!.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                nativeChanges += itemCount
            }
        })

        repeat(6) { iteration ->
            val updatedMode = StudyMode("mode-$iteration", "Mode $iteration")
            compose.runOnIdle {
                decks = decks.mapIndexed { index, deck ->
                    if (index == 0) deck.copy(activeMode = updatedMode) else deck
                }
            }
            compose.waitForIdle()

            assertSame("A mode change must keep the existing card", card, list.findViewHolderForAdapterPosition(0))
            assertEquals(updatedMode, (card as DeckListAdapter.Holder).deck!!.activeMode)
            compose.onNodeWithText("${decks.first().name}: ${updatedMode.name}").assertExists()
            assertSame(neighbour, list.findViewHolderForAdapterPosition(1))
            assertEquals(neighbourTop, neighbour.itemView.top)
            assertEquals(0f, neighbour.itemView.translationY, 0.01f)
        }
        assertEquals("Content changes must not rebind native list rows", 0, nativeChanges)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `native long press starts dragging and saves only after dropping`() {
        val list = showList()
        val start = beginDrag(list)
        assertEquals(1, dragStarts)
        assertTrue(saved.isEmpty())
        val target = list.findViewHolderForAdapterPosition(1)!!.itemView
        touch(list, start, MotionEvent.ACTION_MOVE, target.bottom.toFloat() - 2f)
        compose.waitForIdle()
        assertTrue(saved.isEmpty())

        touch(list, start, MotionEvent.ACTION_UP, target.bottom.toFloat() - 2f)
        compose.waitForIdle()

        assertEquals(1, saved.size)
        assertEquals("deck1", saved.single().first())
        assertEquals(0, clicks)
    }

    @Test
    fun `holding near the lower edge scrolls the native list automatically`() {
        val list = showList()
        val start = beginDrag(list)
        val before = list.computeVerticalScrollOffset()

        // Przy stałym palcu kolejne klatki muszą czekać na zegar testu, zamiast wykonywać się bez końca.
        ShadowChoreographer.setPaused(true)
        try {
            touch(list, start, MotionEvent.ACTION_MOVE, list.height.toFloat() - 1f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
            assertTrue(
                "ItemTouchHelper must scroll while the pointer stays at the edge",
                list.computeVerticalScrollOffset() > before
            )
        } finally {
            touch(list, start, MotionEvent.ACTION_UP, list.height.toFloat() - 1f)
            ShadowChoreographer.setPaused(false)
        }
    }

    @Test
    fun `holding near the upper edge scrolls the native list automatically`() {
        val list = showList()
        compose.runOnIdle { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(10, 0) }
        compose.waitForIdle()
        val start = beginDrag(list)
        val before = list.computeVerticalScrollOffset()

        ShadowChoreographer.setPaused(true)
        try {
            touch(list, start, MotionEvent.ACTION_MOVE, 1f)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
            assertTrue("ItemTouchHelper must also scroll upwards", list.computeVerticalScrollOffset() < before)
        } finally {
            touch(list, start, MotionEvent.ACTION_UP, 1f)
            ShadowChoreographer.setPaused(false)
        }
    }

    @Test
    fun `pending order survives a stats refresh and failed writes reset it`() {
        val list = showList()
        val adapter = list.adapter as DeckListAdapter
        val original = decks.map { it.id }
        compose.runOnIdle {
            adapter.startDrag()
            assertTrue(adapter.move(0, 1))
            adapter.finishDrag { saved.add(it) }
            decks = decks.map { it.copy(dueCount = 5) }
        }
        compose.waitForIdle()
        assertEquals("deck1", (list.findViewHolderForAdapterPosition(0) as DeckListAdapter.Holder).deck!!.id)

        compose.runOnIdle { resetKey++ }
        compose.waitForIdle()

        assertEquals(original.first(), (list.findViewHolderForAdapterPosition(0) as DeckListAdapter.Holder).deck!!.id)
    }

    @Test
    fun `a click still opens the deck and a hold without moving does not save`() {
        val list = showList()
        val now = SystemClock.uptimeMillis()
        val y = list.getChildAt(0).height / 2f
        touch(list, now, MotionEvent.ACTION_DOWN, y)
        touch(list, now, MotionEvent.ACTION_UP, y)
        compose.waitForIdle()
        assertEquals(1, clicks)

        val start = beginDrag(list)
        touch(list, start, MotionEvent.ACTION_UP, y)
        compose.waitForIdle()
        assertTrue(saved.isEmpty())
        assertEquals(1, clicks)
    }

    private fun beginDrag(list: RecyclerView): Long {
        val downTime = SystemClock.uptimeMillis()
        val first = (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val item = list.findViewHolderForAdapterPosition(first)!!.itemView
        touch(list, downTime, MotionEvent.ACTION_DOWN, (item.top + item.height / 2).toFloat())
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 100)
        )
        compose.mainClock.advanceTimeBy(ViewConfiguration.getLongPressTimeout().toLong() + 100)
        assertTrue("Long press must start ItemTouchHelper", dragStarts > 0)
        // Pierwszy MOVE przekazuje gest z ComposeView do RecyclerView i anuluje dotyk dziecka.
        touch(list, downTime, MotionEvent.ACTION_MOVE, (item.top + item.height / 2).toFloat())
        return downTime
    }

    private fun touch(list: RecyclerView, downTime: Long, action: Int, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, list.width / 2f, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try {
            list.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun findList(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findList(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun summary(id: String) = DeckSummary(id, id, StudyMode("mode", "Mode"), CardStats(), 0)
}
