package com.crashlab.analyzer.domain

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure, deterministic descriptive statistics over historical rounds.
 * No forecasting. No "next round" output. Ever.
 */
object StatsEngine {

    val DEFAULT_TARGETS = listOf(1.2, 1.5, 2.0, 3.0, 5.0, 10.0, 20.0, 50.0, 100.0)

    fun summarize(rounds: List<Round>): StatsSummary {
        if (rounds.isEmpty()) return StatsSummary.EMPTY
        val values = rounds.map { it.multiplier }
        val n = values.size
        val sorted = values.sorted()

        val mean = values.sum() / n
        val median = percentileOf(sorted, 50.0)
        val logSum = values.sumOf { ln(max(it, 1e-9)) }
        val geo = exp(logSum / n)
        val variance = values.sumOf { (it - mean) * (it - mean) } / n
        val sd = sqrt(variance)

        val bust = values.count { it < 2.0 }.toDouble() / n
        val instant = values.count { it < 1.2 }.toDouble() / n
        val high = values.count { it >= 10.0 }.toDouble() / n

        // Streaks are computed newest-first as the caller supplies chronological order.
        val chronological = rounds.sortedBy { it.timestamp }.map { it.multiplier }
        var longest = 0
        var running = 0
        chronological.forEach {
            if (it < 2.0) { running++; longest = max(longest, running) } else running = 0
        }
        var current = 0
        for (v in chronological.asReversed()) { if (v < 2.0) current++ else break }

        // Empirical house edge proxy: a hypothetical bettor holding to X gets
        // X * P(round >= X). Averaged across targets it approximates 1 - edge.
        val evs = DEFAULT_TARGETS.map { t ->
            t * values.count { it >= t }.toDouble() / n
        }
        val edge = 1.0 - (evs.sum() / evs.size)

        val q = mapOf(
            5 to percentileOf(sorted, 5.0),
            25 to percentileOf(sorted, 25.0),
            50 to median,
            75 to percentileOf(sorted, 75.0),
            95 to percentileOf(sorted, 95.0),
            99 to percentileOf(sorted, 99.0)
        )

        return StatsSummary(
            count = n, mean = mean, median = median, geometricMean = geo,
            stdDev = sd, min = sorted.first(), max = sorted.last(),
            bustRate = bust, instantBustRate = instant, highRate = high,
            longestSubTwoStreak = longest, currentSubTwoStreak = current,
            impliedHouseEdge = edge, quantiles = q
        )
    }

    private fun percentileOf(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        if (sorted.size == 1) return sorted[0]
        val rank = (p / 100.0) * (sorted.size - 1)
        val low = rank.toInt()
        val high = min(low + 1, sorted.size - 1)
        val frac = rank - low
        return sorted[low] + (sorted[high] - sorted[low]) * frac
    }

    /** Empirical vs. theoretical survival curve. */
    fun survival(rounds: List<Round>, targets: List<Double> = DEFAULT_TARGETS): List<SurvivalPoint> {
        val n = rounds.size
        if (n == 0) return targets.map { SurvivalPoint(it, 0.0, 0.99 / it, 0) }
        return targets.map { t ->
            val hits = rounds.count { it.multiplier >= t }
            SurvivalPoint(
                target = t,
                empiricalProbability = hits.toDouble() / n,
                theoreticalProbability = (0.99 / t).coerceIn(0.0, 1.0),
                sampleSize = n
            )
        }
    }

    /** Histogram over the classic crash bands. */
    fun histogram(rounds: List<Round>): List<Bucket> {
        val edges = listOf(1.0, 1.2, 1.5, 2.0, 3.0, 5.0, 10.0, 20.0, 50.0, Double.MAX_VALUE)
        val labels = listOf(
            "1.00–1.19", "1.20–1.49", "1.50–1.99", "2.00–2.99", "3.00–4.99",
            "5.00–9.99", "10.0–19.9", "20.0–49.9", "50.0+"
        )
        val n = rounds.size.coerceAtLeast(1)
        return labels.indices.map { i ->
            val lo = edges[i]; val hi = edges[i + 1]
            val c = rounds.count { it.multiplier >= lo && it.multiplier < hi }
            Bucket(labels[i], lo, hi, c, c.toDouble() / n)
        }
    }

    /** Rolling mean of the last [window] rounds, chronological order in/out. */
    fun rollingMean(rounds: List<Round>, window: Int = 20): List<Double> {
        if (rounds.isEmpty()) return emptyList()
        val v = rounds.sortedBy { it.timestamp }.map { it.multiplier }
        val out = ArrayList<Double>(v.size)
        var sum = 0.0
        for (i in v.indices) {
            sum += v[i]
            if (i >= window) sum -= v[i - window]
            out.add(sum / min(i + 1, window))
        }
        return out
    }

    /**
     * Lag-1 autocorrelation. For a fair RNG this hovers around 0.
     * We show it precisely so users can SEE that history has no memory.
     */
    fun lag1Autocorrelation(rounds: List<Round>): Double {
        val v = rounds.sortedBy { it.timestamp }.map { ln(max(it.multiplier, 1e-9)) }
        if (v.size < 3) return 0.0
        val mean = v.average()
        var num = 0.0
        var den = 0.0
        for (i in v.indices) {
            den += (v[i] - mean) * (v[i] - mean)
            if (i > 0) num += (v[i] - mean) * (v[i - 1] - mean)
        }
        return if (den == 0.0) 0.0 else num / den
    }

    /**
     * Chi-square goodness-of-fit against the theoretical crash distribution
     * P(X >= x) = 0.99/x. Returns the statistic and degrees of freedom.
     * A large value only means "this sample looks unusual", never "next round is X".
     */
    fun chiSquareVsTheory(rounds: List<Round>): Pair<Double, Int> {
        val n = rounds.size
        if (n < 30) return 0.0 to 0
        val edges = listOf(1.0, 1.5, 2.0, 3.0, 5.0, 10.0, Double.MAX_VALUE)
        var chi = 0.0
        var df = 0
        for (i in 0 until edges.size - 1) {
            val lo = edges[i]; val hi = edges[i + 1]
            val observed = rounds.count { it.multiplier >= lo && it.multiplier < hi }.toDouble()
            // P(X >= 1.00) is exactly 1.0: every round pays at least 1.00x.
            // Using 0.99 here would systematically inflate chi-square on the
            // bottom bucket and make fair samples look rigged.
            val pLo = if (lo <= 1.0) 1.0 else (0.99 / lo).coerceAtMost(1.0)
            val pHi = if (hi == Double.MAX_VALUE) 0.0 else 0.99 / hi
            val expected = n * (pLo - pHi)
            if (expected >= 5.0) {
                chi += (observed - expected) * (observed - expected) / expected
                df++
            }
        }
        return chi to max(df - 1, 1)
    }

    /**
     * Traffic-light assessment. This describes how CHOPPY the recorded window
     * was — it is a volatility read-out, not a signal to bet on.
     */
    fun assessRisk(stats: StatsSummary, autocorr: Double): RiskAssessment {
        if (stats.count < 10) {
            return RiskAssessment(
                RiskBand.AMBER, 0,
                "Not enough data",
                "Add at least 10 rounds for a meaningful volatility read-out.",
                emptyList()
            )
        }
        val factors = mutableListOf<RiskFactor>()

        // 1. Bust rate vs the ~50.5% theoretical baseline for <2.00x.
        val bustDelta = stats.bustRate - 0.505
        val bustPts = (bustDelta * 160).roundToInt().coerceIn(-25, 30)
        factors += RiskFactor(
            "Rounds under 2.00x", pct(stats.bustRate), bustPts,
            bandFor(stats.bustRate, 0.52, 0.60)
        )

        // 2. Instant-bust density (<1.20x), theoretical ~17.5%.
        val instPts = ((stats.instantBustRate - 0.175) * 200).roundToInt().coerceIn(-15, 25)
        factors += RiskFactor(
            "Instant busts under 1.20x", pct(stats.instantBustRate), instPts,
            bandFor(stats.instantBustRate, 0.20, 0.28)
        )

        // 3. Dispersion: interquartile ratio P75/P25.
        // Standard deviation and CV are useless on this distribution — it is
        // heavy-tailed (Pareto), so a single 5000x round swamps the variance.
        // The quartile ratio is robust to outliers. Theoretical baseline:
        // P75/P25 = (0.99/0.25) / (0.99/0.75) = 3.0.
        val p25 = stats.quantiles[25] ?: 1.0
        val p75 = stats.quantiles[75] ?: 1.0
        val iqrRatio = if (p25 > 0) p75 / p25 else 1.0
        val dispPts = ((iqrRatio - 3.0) * 12).roundToInt().coerceIn(-15, 25)
        factors += RiskFactor(
            "Spread (P75/P25)", fmt(iqrRatio), dispPts, bandFor(iqrRatio, 3.8, 5.0)
        )

        // 4. Dry spell: current streak below 2.00x.
        val streakPts = (stats.currentSubTwoStreak * 3).coerceAtMost(20)
        factors += RiskFactor(
            "Current run under 2.00x", "${stats.currentSubTwoStreak} rounds", streakPts,
            bandFor(stats.currentSubTwoStreak.toDouble(), 4.0, 7.0)
        )

        // 5. Serial dependence — should be ~0 in a fair game.
        val acPts = (abs(autocorr) * 60).roundToInt().coerceAtMost(15)
        factors += RiskFactor(
            "Lag-1 autocorrelation", fmt(autocorr), acPts,
            bandFor(abs(autocorr), 0.12, 0.25)
        )

        // Baseline 32 is calibrated so that a sample drawn from the textbook
        // crash distribution lands in the GREEN band. The score only rises when
        // the window genuinely deviates from fair-RNG expectations.
        val score = (32 + factors.sumOf { it.contribution }).coerceIn(0, 100)
        val band = when {
            score < 40 -> RiskBand.GREEN
            score < 68 -> RiskBand.AMBER
            else -> RiskBand.RED
        }
        val headline = when (band) {
            RiskBand.GREEN -> "Calm window"
            RiskBand.AMBER -> "Mixed window"
            RiskBand.RED -> "Choppy window"
        }
        val detail = when (band) {
            RiskBand.GREEN -> "The last ${stats.count} recorded rounds sat close to the " +
                "textbook crash distribution. This says nothing about what comes next."
            RiskBand.AMBER -> "The last ${stats.count} rounds show moderate deviation from the " +
                "textbook distribution — normal sampling noise. Not a signal."
            RiskBand.RED -> "The last ${stats.count} rounds were unusually low or volatile. " +
                "Streaks like this are expected in random data and do not make a " +
                "high multiplier 'due'."
        }
        return RiskAssessment(band, score, headline, detail, factors)
    }

    private fun bandFor(v: Double, amber: Double, red: Double) = when {
        v >= red -> RiskBand.RED
        v >= amber -> RiskBand.AMBER
        else -> RiskBand.GREEN
    }

    fun pct(v: Double): String = "${(v * 100).roundToInt()}%"
    fun fmt(v: Double): String = String.format("%.2f", v)
}
