package com.crashlab.analyzer.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Replays a staking plan over a sequence of multipliers.
 *
 * The point of this screen is NOT to find a winning system. It is to let people
 * watch, with their own recorded data, how quickly progressive staking plans
 * hit the table limit or wipe the bankroll. Every simulation is a backtest on
 * rounds that already happened.
 */
object StrategySimulator {

    private val FIB = generateSequence(1 to 1) { (a, b) -> b to (a + b) }
        .map { it.first }.take(40).toList()

    fun simulate(multipliers: List<Double>, cfg: StrategyConfig): SimulationResult {
        var bankroll = cfg.startingBankroll
        var peak = bankroll
        var trough = bankroll
        var maxDd = 0.0
        var wins = 0
        var losses = 0
        var staked = 0.0
        var lossStreak = 0
        var longestLossStreak = 0
        var step = 0            // progression index
        var dalembert = 0
        var ruin = false
        var stopLoss = false
        var takeProfit = false
        val equity = ArrayList<EquityPoint>()

        val floor = cfg.startingBankroll * (1 - cfg.stopLossPct / 100.0)
        val ceiling = cfg.startingBankroll * (1 + cfg.takeProfitPct / 100.0)
        val capStake = cfg.baseStake * cfg.maxStakeMultiple
        val rounds = min(cfg.maxRounds, multipliers.size)

        for (i in 0 until rounds) {
            var stake = when (cfg.type) {
                StrategyType.FLAT -> cfg.baseStake
                StrategyType.MARTINGALE -> cfg.baseStake * Math.pow(2.0, step.toDouble())
                StrategyType.DALEMBERT -> cfg.baseStake * (1 + dalembert)
                StrategyType.FIBONACCI -> cfg.baseStake * FIB[min(step, FIB.lastIndex)]
                StrategyType.PERCENT_BANKROLL -> bankroll * (cfg.baseStake / cfg.startingBankroll)
            }
            stake = min(stake, capStake)
            stake = min(stake, bankroll)
            if (stake <= 0.009) { ruin = true; break }

            val m = multipliers[i]
            val won = m >= cfg.cashOutAt
            staked += stake

            if (won) {
                bankroll += stake * (cfg.cashOutAt - 1.0)
                wins++
                step = 0
                dalembert = max(0, dalembert - 1)
                lossStreak = 0
            } else {
                bankroll -= stake
                losses++
                step++
                dalembert++
                lossStreak++
                longestLossStreak = max(longestLossStreak, lossStreak)
            }

            peak = max(peak, bankroll)
            trough = min(trough, bankroll)
            if (peak > 0) maxDd = max(maxDd, (peak - bankroll) / peak * 100.0)
            equity.add(EquityPoint(i + 1, bankroll, stake, won))

            if (bankroll <= 0.01) { ruin = true; break }
            if (bankroll <= floor) { stopLoss = true; break }
            if (bankroll >= ceiling) { takeProfit = true; break }
        }

        return SimulationResult(
            config = cfg,
            finalBankroll = bankroll,
            peakBankroll = peak,
            troughBankroll = trough,
            maxDrawdownPct = maxDd,
            roundsPlayed = equity.size,
            wins = wins,
            losses = losses,
            totalStaked = staked,
            ruin = ruin,
            hitStopLoss = stopLoss,
            hitTakeProfit = takeProfit,
            longestLosingStreak = longestLossStreak,
            equity = equity
        )
    }

    /**
     * Resamples the user's own history (bootstrap) many times to show the
     * SPREAD of outcomes rather than one lucky path. This is the honest way to
     * present a backtest: one run means nothing.
     */
    fun monteCarlo(
        multipliers: List<Double>,
        cfg: StrategyConfig,
        runs: Int = 300,
        seed: Long = 42L
    ): MonteCarloResult {
        if (multipliers.isEmpty()) return MonteCarloResult(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val rng = Random(seed)
        val finals = DoubleArray(runs)
        var ruins = 0
        var profits = 0
        var worstDd = 0.0
        for (r in 0 until runs) {
            val sample = List(min(cfg.maxRounds, 2000)) {
                multipliers[rng.nextInt(multipliers.size)]
            }
            val res = simulate(sample, cfg)
            finals[r] = res.finalBankroll
            if (res.ruin) ruins++
            if (res.finalBankroll > cfg.startingBankroll) profits++
            worstDd = max(worstDd, res.maxDrawdownPct)
        }
        finals.sort()
        fun q(p: Double): Double {
            val idx = ((runs - 1) * p).toInt().coerceIn(0, runs - 1)
            return finals[idx]
        }
        return MonteCarloResult(
            runs = runs,
            medianFinal = q(0.50),
            meanFinal = finals.average(),
            p05 = q(0.05),
            p95 = q(0.95),
            ruinRate = ruins.toDouble() / runs,
            profitRate = profits.toDouble() / runs,
            worstDrawdownPct = worstDd
        )
    }

    /**
     * Generates a provably-fair-style reference sequence, used only for the
     * "compare your sample to textbook randomness" demo and for seeding an
     * empty database in demo mode.
     */
    fun syntheticCrashSequence(n: Int, houseEdge: Double = 0.01, seed: Long = 7L): List<Double> {
        val rng = Random(seed)
        return List(n) {
            val u = rng.nextDouble().coerceIn(1e-9, 1.0)
            val raw = (1.0 - houseEdge) / u
            (Math.floor(raw * 100) / 100).coerceIn(1.0, 100000.0)
        }
    }
}
