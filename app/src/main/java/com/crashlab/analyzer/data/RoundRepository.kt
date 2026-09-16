package com.crashlab.analyzer.data

import android.content.Context
import com.crashlab.analyzer.domain.Round
import com.crashlab.analyzer.domain.StrategySimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoundRepository(context: Context) {

    private val dao = CrashDatabase.get(context).roundDao()
    private val prefs = SettingsStore(context)

    val settings: Flow<AppSettings> = prefs.settings

    fun observeRounds(): Flow<List<Round>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun add(multiplier: Double, platform: String, timestamp: Long = System.currentTimeMillis()): Long =
        withContext(Dispatchers.IO) {
            dao.insert(RoundEntity(multiplier = multiplier, timestamp = timestamp, platform = platform))
        }

    suspend fun addBatch(rounds: List<Round>) = withContext(Dispatchers.IO) {
        dao.insertAll(rounds.map { it.toEntity() })
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }

    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }

    /**
     * Parses free-form pasted text or CSV. Accepts values like:
     *   "1.23x, 4.5, 2.00x" or one value per line, or "timestamp,multiplier,platform".
     */
    fun parseBulk(raw: String, platform: String): ParseOutcome {
        val tokens = raw.split('\n', ',', ';', ' ', '\t')
            .map { it.trim().removeSuffix("x").removeSuffix("X").trim() }
            .filter { it.isNotEmpty() }

        val values = mutableListOf<Double>()
        var rejected = 0
        tokens.forEach { t ->
            val d = t.toDoubleOrNull()
            // Values above 1,000,000 or below 1.0 are not valid crash points.
            if (d != null && d >= 1.0 && d <= 1_000_000.0) values.add(d) else rejected++
        }
        val now = System.currentTimeMillis()
        // Spaced 30s apart so chronological ordering is stable and realistic.
        val rounds = values.mapIndexed { i, v ->
            Round(
                multiplier = v,
                timestamp = now - (values.size - 1 - i) * 30_000L,
                platform = platform
            )
        }
        return ParseOutcome(rounds, rejected)
    }

    fun toCsv(rounds: List<Round>): String = buildString {
        appendLine("timestamp,multiplier,platform")
        rounds.sortedBy { it.timestamp }.forEach {
            appendLine("${it.timestamp},${it.multiplier},${it.platform}")
        }
    }

    /** Seeds a provably-fair style sample so new users can explore the app. */
    suspend fun seedDemoData(n: Int = 500) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val seq = StrategySimulator.syntheticCrashSequence(n, seed = System.currentTimeMillis())
        val rounds = seq.mapIndexed { i, v ->
            Round(multiplier = v, timestamp = now - (n - i) * 45_000L, platform = "demo")
        }
        dao.insertAll(rounds.map { it.toEntity() })
    }

    suspend fun setSettings(block: (AppSettings) -> AppSettings) = prefs.update(block)
}

data class ParseOutcome(val rounds: List<Round>, val rejected: Int)
