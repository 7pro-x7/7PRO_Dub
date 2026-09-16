package com.crashlab.analyzer.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crashlab.analyzer.data.AppSettings
import com.crashlab.analyzer.data.RoundRepository
import com.crashlab.analyzer.domain.Bucket
import com.crashlab.analyzer.domain.MonteCarloResult
import com.crashlab.analyzer.domain.RiskAssessment
import com.crashlab.analyzer.domain.RiskBand
import com.crashlab.analyzer.domain.Round
import com.crashlab.analyzer.domain.SimulationResult
import com.crashlab.analyzer.domain.StatsEngine
import com.crashlab.analyzer.domain.StatsSummary
import com.crashlab.analyzer.domain.StrategyConfig
import com.crashlab.analyzer.domain.StrategySimulator
import com.crashlab.analyzer.domain.SurvivalPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalyticsState(
    val allRounds: List<Round> = emptyList(),
    val window: List<Round> = emptyList(),
    val stats: StatsSummary = StatsSummary.EMPTY,
    val survival: List<SurvivalPoint> = emptyList(),
    val histogram: List<Bucket> = emptyList(),
    val risk: RiskAssessment = RiskAssessment(
        RiskBand.AMBER, 0, "No data", "Add rounds to begin.", emptyList()
    ),
    val autocorrelation: Double = 0.0,
    val chiSquare: Pair<Double, Int> = 0.0 to 0,
    val rollingMean: List<Double> = emptyList(),
    val settings: AppSettings = AppSettings()
)

data class SimState(
    val config: StrategyConfig = StrategyConfig(),
    val result: SimulationResult? = null,
    val monteCarlo: MonteCarloResult? = null,
    val running: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RoundRepository(app)

    private val _sim = MutableStateFlow(SimState())
    val sim: StateFlow<SimState> = _sim.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<AnalyticsState> =
        combine(repo.observeRounds(), repo.settings) { rounds, settings ->
            val filtered = if (settings.platformFilter == "all") rounds
                else rounds.filter { it.platform == settings.platformFilter }
            val window = filtered.take(settings.windowSize)
            val stats = StatsEngine.summarize(window)
            val ac = StatsEngine.lag1Autocorrelation(window)
            AnalyticsState(
                allRounds = rounds,
                window = window,
                stats = stats,
                survival = StatsEngine.survival(window),
                histogram = StatsEngine.histogram(window),
                risk = StatsEngine.assessRisk(stats, ac),
                autocorrelation = ac,
                chiSquare = StatsEngine.chiSquareVsTheory(window),
                rollingMean = StatsEngine.rollingMean(window, 20),
                settings = settings
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun addRound(value: String, platform: String) {
        val d = value.trim().removeSuffix("x").toDoubleOrNull()
        if (d == null || d < 1.0) {
            _message.value = "Enter a multiplier of 1.00 or higher"
            return
        }
        viewModelScope.launch {
            repo.add(d, platform)
            _message.value = "Recorded ${String.format("%.2f", d)}x"
        }
    }

    fun importBulk(raw: String, platform: String) {
        viewModelScope.launch {
            val outcome = repo.parseBulk(raw, platform)
            if (outcome.rounds.isEmpty()) {
                _message.value = "No valid multipliers found"
                return@launch
            }
            repo.addBatch(outcome.rounds)
            _message.value = "Imported ${outcome.rounds.size} rounds" +
                if (outcome.rejected > 0) " (${outcome.rejected} skipped)" else ""
        }
    }

    fun deleteRound(id: Long) = viewModelScope.launch { repo.delete(id) }

    fun clearAll() = viewModelScope.launch {
        repo.clear()
        _message.value = "All rounds deleted"
    }

    fun seedDemo() = viewModelScope.launch {
        repo.seedDemoData(500)
        _message.value = "Loaded 500 simulated reference rounds"
    }

    fun exportCsv(): String = repo.toCsv(state.value.allRounds)

    fun acceptDisclaimer() = viewModelScope.launch {
        repo.setSettings { it.copy(acceptedDisclaimer = true) }
    }

    fun setWindow(size: Int) = viewModelScope.launch {
        repo.setSettings { it.copy(windowSize = size) }
    }

    fun setPlatformFilter(p: String) = viewModelScope.launch {
        repo.setSettings { it.copy(platformFilter = p) }
    }

    fun setSessionLimit(minutes: Int) = viewModelScope.launch {
        repo.setSettings { it.copy(sessionLimitMinutes = minutes) }
    }

    fun setRealityCheck(enabled: Boolean) = viewModelScope.launch {
        repo.setSettings { it.copy(realityCheckEnabled = enabled) }
    }

    fun updateConfig(block: (StrategyConfig) -> StrategyConfig) {
        _sim.value = _sim.value.copy(config = block(_sim.value.config))
    }

    fun runSimulation() {
        val rounds = state.value.allRounds
        if (rounds.size < 20) {
            _message.value = "Add at least 20 rounds to backtest"
            return
        }
        viewModelScope.launch {
            _sim.value = _sim.value.copy(running = true)
            val cfg = _sim.value.config
            // Chronological order, oldest first — a real replay of the session.
            val multipliers = rounds.sortedBy { it.timestamp }.map { it.multiplier }
            val (single, mc) = withContext(Dispatchers.Default) {
                StrategySimulator.simulate(multipliers, cfg) to
                    StrategySimulator.monteCarlo(multipliers, cfg, runs = 300)
            }
            _sim.value = _sim.value.copy(result = single, monteCarlo = mc, running = false)
        }
    }

    fun consumeMessage() { _message.value = null }
}
