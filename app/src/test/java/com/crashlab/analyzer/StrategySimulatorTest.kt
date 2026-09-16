package com.crashlab.analyzer

import com.crashlab.analyzer.domain.StrategyConfig
import com.crashlab.analyzer.domain.StrategySimulator
import com.crashlab.analyzer.domain.StrategyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategySimulatorTest {

    private val fair = StrategySimulator.syntheticCrashSequence(200_000, seed = 12345L)

    private val base = StrategyConfig(
        type = StrategyType.FLAT,
        startingBankroll = 10_000.0,
        baseStake = 10.0,
        cashOutAt = 2.0,
        maxRounds = 5000,
        stopLossPct = 100.0,
        takeProfitPct = 10_000.0
    )

    @Test
    fun `flat betting arithmetic is exact`() {
        val r = StrategySimulator.simulate(fair, base)
        assertEquals(5000, r.roundsPlayed)
        assertEquals(r.roundsPlayed, r.wins + r.losses)
        assertEquals(10_000.0 + (r.wins - r.losses) * 10.0, r.finalBankroll, 1e-6)
    }

    /** The house edge must show up as a loss over a long run. */
    @Test
    fun `flat betting loses money over many rounds`() {
        assertTrue(StrategySimulator.simulate(fair, base).netProfit < 0)
    }

    @Test
    fun `martingale drawdown is worse than flat`() {
        val flat = StrategySimulator.simulate(fair, base)
        val mart = StrategySimulator.simulate(fair, base.copy(type = StrategyType.MARTINGALE))
        assertTrue(mart.maxDrawdownPct >= flat.maxDrawdownPct)
    }

    @Test
    fun `stop loss halts the session`() {
        val r = StrategySimulator.simulate(fair, base.copy(stopLossPct = 5.0))
        assertTrue(r.hitStopLoss || r.roundsPlayed < 5000)
        if (r.hitStopLoss) assertTrue(r.finalBankroll <= 9500.0 + 10.0)
    }

    @Test
    fun `take profit halts the session`() {
        val r = StrategySimulator.simulate(fair, base.copy(takeProfitPct = 0.3))
        assertTrue(r.hitTakeProfit)
        assertTrue(r.finalBankroll >= 10_030.0)
    }

    @Test
    fun `bankroll can never go negative`() {
        val r = StrategySimulator.simulate(
            fair,
            StrategyConfig(
                type = StrategyType.MARTINGALE, startingBankroll = 100.0, baseStake = 10.0,
                cashOutAt = 3.0, maxRounds = 5000, stopLossPct = 100.0,
                takeProfitPct = 100_000.0, maxStakeMultiple = 1024.0
            )
        )
        assertTrue(r.finalBankroll >= 0.0)
        assertTrue(r.equity.all { it.bankroll >= -1e-9 })
    }

    @Test
    fun `every strategy terminates consistently`() {
        StrategyType.entries.forEach { t ->
            val r = StrategySimulator.simulate(fair, base.copy(type = t, maxRounds = 2000))
            assertTrue(r.roundsPlayed > 0)
            assertEquals(r.roundsPlayed, r.wins + r.losses)
            assertTrue(r.finalBankroll >= 0.0)
        }
    }

    @Test
    fun `empty history does not crash`() {
        assertEquals(0, StrategySimulator.simulate(emptyList(), base).roundsPlayed)
        assertEquals(0, StrategySimulator.monteCarlo(emptyList(), base).runs)
    }

    /** The headline honesty result: average outcome is negative. */
    @Test
    fun `monte carlo mean outcome is negative`() {
        val mc = StrategySimulator.monteCarlo(fair, base.copy(maxRounds = 500), runs = 200)
        assertEquals(200, mc.runs)
        assertTrue(mc.p05 <= mc.medianFinal && mc.medianFinal <= mc.p95)
        assertTrue(mc.meanFinal < base.startingBankroll)
    }

    @Test
    fun `monte carlo is deterministic for a fixed seed`() {
        val a = StrategySimulator.monteCarlo(fair, base.copy(maxRounds = 500), runs = 100)
        val b = StrategySimulator.monteCarlo(fair, base.copy(maxRounds = 500), runs = 100)
        assertEquals(a.medianFinal, b.medianFinal, 1e-9)
    }

    @Test
    fun `synthetic generator produces valid crash points`() {
        val seq = StrategySimulator.syntheticCrashSequence(1000, seed = 1L)
        assertTrue(seq.all { it >= 1.0 })
        assertEquals(
            StrategySimulator.syntheticCrashSequence(100, seed = 1L),
            StrategySimulator.syntheticCrashSequence(100, seed = 1L)
        )
    }
}
