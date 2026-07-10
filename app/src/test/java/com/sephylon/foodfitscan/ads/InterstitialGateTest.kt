package com.sephylon.foodfitscan.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialGateTest {

    private val now = 1_000_000L

    @Test
    fun `does not show before the view threshold`() {
        val gate = InterstitialGate(viewsPerAd = 10)
        repeat(9) { gate.recordProductView() }
        assertFalse(gate.shouldShow(lastShownAtMillis = null, nowMillis = now))
    }

    @Test
    fun `shows once the view threshold is reached and no ad was shown before`() {
        val gate = InterstitialGate(viewsPerAd = 10)
        repeat(10) { gate.recordProductView() }
        assertTrue(gate.shouldShow(lastShownAtMillis = null, nowMillis = now))
    }

    @Test
    fun `respects the minimum interval since the last shown ad`() {
        val gate = InterstitialGate(viewsPerAd = 10, minIntervalMillis = 60_000L)
        repeat(10) { gate.recordProductView() }

        val justShown = now - 30_000L
        assertFalse(gate.shouldShow(lastShownAtMillis = justShown, nowMillis = now))

        val longAgo = now - 60_000L
        assertTrue(gate.shouldShow(lastShownAtMillis = longAgo, nowMillis = now))
    }

    @Test
    fun `onAdShown resets the counter so the next ad needs the full threshold again`() {
        val gate = InterstitialGate(viewsPerAd = 10, minIntervalMillis = 0L)
        repeat(10) { gate.recordProductView() }
        assertTrue(gate.shouldShow(lastShownAtMillis = null, nowMillis = now))

        gate.onAdShown()
        assertEquals(0, gate.pendingViews)
        assertFalse(gate.shouldShow(lastShownAtMillis = null, nowMillis = now))

        repeat(9) { gate.recordProductView() }
        assertFalse(gate.shouldShow(lastShownAtMillis = null, nowMillis = now))
        gate.recordProductView()
        assertTrue(gate.shouldShow(lastShownAtMillis = null, nowMillis = now))
    }

    @Test
    fun `preload hint fires shortly before the threshold`() {
        val gate = InterstitialGate(viewsPerAd = 10)
        repeat(7) { gate.recordProductView() }
        assertFalse(gate.shouldPreload())

        gate.recordProductView() // 8 views = threshold - PRELOAD_LEAD_VIEWS
        assertTrue(gate.shouldPreload())
        assertFalse(gate.shouldShow(lastShownAtMillis = null, nowMillis = now))
    }

    @Test
    fun `default configuration stays within the requested 8 to 12 view window`() {
        assertTrue(InterstitialGate.DEFAULT_VIEWS_PER_AD in 8..12)
    }
}
