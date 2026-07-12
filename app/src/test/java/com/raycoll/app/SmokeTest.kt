package com.raycoll.app

import com.raycoll.app.core.Clock
import com.raycoll.app.core.SystemClock
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmokeTest {
    @Test
    fun `SystemClock returns valid millis`() {
        val clock: Clock = SystemClock()
        val now = clock.millis()
        assertTrue(now > 0, "Clock should return positive timestamp")
    }

    @Test
    fun `project is configured`() {
        assertNotNull(RayCollApplication::class.java)
    }
}
