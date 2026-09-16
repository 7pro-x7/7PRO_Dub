package com.crashlab.analyzer

import com.crashlab.analyzer.domain.RiskBand
import com.crashlab.analyzer.domain.Round
import com.crashlab.analyzer.domain.StatsEngine
import com.crashlab.analyzer.domain.StrategySimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StatsEngineTest {

    private val now = System.currentTimeMillis()

    private fun rounds(values: List<Double>) = values.mapIndexed { i, v ->
        Round(id = i.toLong(), multiplier = v, timestamp = now + i * 1000L)
    }

    private fun fairSample(n: Int = 200_000) =
        rounds(StrategySimulator.syntheticCrashSequence(n, seed = 12345L))

    @Test
    fun `summary computes basic statistics`() {
        val s = StatsEngine.summarize(rounds(listOf(1.0, 2.0, 3.0, 4.0, 5.0)))
        assertEquals(5, s.count)
        assertEquals(3.0, s.mean, 1e-9)
        assertEquals(3.0, s.median, 1e-9)
        assertEquals(0.2, s.bustRate, 1e-9)
    }

    @Test
    fun `empty input is safe`() {
        assertEquals(0, StatsEngine.summarize(emptyList()).count)
    }

    @Test
    fun `streaks are measured chronologically`() {
        val s = StatsEngine.summarize(rounds(listOf(1.1, 1.5, 1.9, 5.0, 1.2, 1.3, 1.4, 1.5)))
        assertEquals(4, s.longestSubTwoStreak)
        assertEquals(4, s.currentSubTwoStreak)
    }

    @Test
    fun `fair sample matches the theoretical crash distribution`() {
        val s = StatsEngine.summarize(fairSample())
        assertEquals(0.505, s.bustRate, 0.01)
        assertEquals(0.175, s.instantBustRate, 0.01)
        assertEquals(1.98, s.median, 0.05)
    }

    @Test
    fun `survival curve tracks 0_99 over x`() {
        StatsEngine.survival(fairSample()).forEach { p ->
            assertEquals(p.theoreticalProbability, p.empiricalProbability, 0.012)
        }
    }

    @Test
    fun `histogram shares sum to one`() {
        val h = StatsEngine.histogram(fairSample(5000))
        assertEquals(9, h.size)
        assertEquals(1.0, h.sumOf { it.share }, 1e-6)
    }

    /** A fair RNG has no memory; this guards the app's core honesty claim. */
    @Test
    fun `lag-1 autocorrelation is near zero for random data`() {
        assertTrue(abs(StatsEngine.lag1Autocorrelation(fairSample())) < 0.02)
    }

    /**
     * Regression: P(X >= 1.00) is 1.0, not 0.99. Using 0.99 inflated the bottom
     * bucket and made fair samples look rigged (chi-square ~70 instead of ~2).
     */
    @Test
    fun `chi-square is small for a fair sample`() {
        val (chi, df) = StatsEngine.chiSquareVsTheory(fairSample())
        assertTrue("df should be positive", df > 0)
        assertTrue("chi=$chi should be small", chi < 15.0)
    }

    @Test
    fun `chi-square needs a minimum sample`() {
        assertEquals(0, StatsEngine.chiSquareVsTheory(rounds(listOf(1.0, 2.0))).second)
    }

    /** Calibration: textbook-random data must not be flagged as alarming. */
    @Test
    fun `fair sample is rated green`() {
        val f = fairSample()
        val risk = StatsEngine.assessRisk(
            StatsEngine.summarize(f), StatsEngine.lag1Autocorrelation(f)
        )
        assertEquals(RiskBand.GREEN, risk.band)
        assertEquals(5, risk.factors.size)
    }

    @Test
    fun `degenerate low sample is rated red`() {
        val grim = rounds(List(60) { 1.05 })
        val risk = StatsEngine.assessRisk(
            StatsEngine.summarize(grim), StatsEngine.lag1Autocorrelation(grim)
        )
        assertEquals(RiskBand.RED, risk.band)
    }

    @Test
    fun `tiny samples are guarded`() {
        val risk = StatsEngine.assessRisk(StatsEngine.summarize(rounds(listOf(2.0, 3.0))), 0.0)
        assertEquals(0, risk.score)
    }

    @Test
    fun `rolling mean respects the window`() {
        val r = StatsEngine.rollingMean(rounds(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 3)
        assertEquals(5, r.size)
        assertEquals(1.0, r[0], 1e-9)
        assertEquals(4.0, r[4], 1e-9)
    }
}
