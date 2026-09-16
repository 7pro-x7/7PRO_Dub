# CrashLab — Crash/Aviator History Analyzer

An offline Android app that performs **descriptive statistical analysis on crash-game
rounds that have already happened**. It does not predict future rounds, and it cannot.

> **This app does not predict the next round and does not guarantee profits.**
> Crash outcomes come from a random number generator. Each round is statistically
> independent of every round before it. No analysis of past results — and no staking
> strategy — can produce an edge over the house.

---

## Build

```bash
# Requires JDK 17 and the Android SDK (API 34)
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # run the unit test suite
./gradlew installDebug         # install on a connected device
```

Or open the folder in **Android Studio** (Hedgehog or newer) and press Run.

| Setting | Value |
|---|---|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material 3 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 34 |
| Database | Room |
| Permissions | **none** — no `INTERNET` permission at all |

---

## Screens

| Tab | Purpose |
|---|---|
| **Overview** | Traffic-light volatility read-out 🟢🟡🔴, headline stats, distribution, randomness checks |
| **Trends** | Cash-out reach rates vs. the theoretical curve, confidence intervals, percentiles |
| **Simulate** | Backtests staking plans on your own data + 300-run bootstrap showing the spread |
| **Log** | Manual entry, bulk paste/CSV import, per-platform tagging, history management |
| **Safety** | How crash games work, myth debunking, session limits, support helplines |

---

## Architecture

```
domain/     Models.kt, StatsEngine.kt, StrategySimulator.kt   ← pure Kotlin, fully unit-tested
data/       Room database, repository, DataStore settings
ui/         theme/, components/ (custom Canvas charts), screens/, vm/
```

The `domain` package has no Android dependencies, so the entire analytical core runs
on the JVM and is covered by unit tests.

---

## The statistics

The reference model for crash games is `P(X ≥ x) = 0.99 / x`, giving a ~1% house edge.

**Computed over the selected window:**
mean, median, geometric mean, standard deviation, percentiles (P5–P99), rate below
2.00x and 1.20x, rate at/above 10x, current and longest sub-2.00x streaks, empirical
house edge, 9-band histogram, rolling 20-round average.

**Randomness diagnostics** — included specifically to *disprove* pattern-hunting:

- **Lag-1 autocorrelation** on log-multipliers. Verified ≈ 0.001 on a fair 200,000-round
  sample. Consecutive rounds are unrelated; there is no momentum to trade.
- **Chi-square goodness-of-fit** against the theoretical distribution.
- **Wilson score 95% confidence intervals** on every reach rate, so small samples
  visibly show their uncertainty instead of masquerading as signal.

### The risk indicator is a volatility read-out, not a betting signal

🟢🟡🔴 scores how far the *recorded window* deviated from textbook randomness, using five
factors: sub-2.00x rate, sub-1.20x rate, spread (P75/P25), current dry streak, and
autocorrelation. A red light means "this sample was choppy", **never** "a big multiplier
is due". The UI states this on the card itself.

The scale is calibrated so a sample drawn from the textbook distribution reads **green** —
verified by unit test.

### Strategy simulator

Replays Flat, Martingale, D'Alembert, Fibonacci, and %-bankroll plans over your history
with configurable cash-out target, stop-loss, take-profit, and table limit.

Because a single backtest is meaningless, every run is accompanied by a **300-iteration
bootstrap** that resamples your own data and reports the median, 5th–95th percentile
band, ruin rate, and worst drawdown. This is what makes the negative expected value
visible: runs frequently finish slightly ahead, while rare catastrophic runs drag the
mean below the starting bankroll every time.

---

## Testing

`app/src/test/` contains 25 JUnit tests covering the analytical core, validated against
a 200,000-round fair sample:

```
bust rate      0.5058   (theory 0.505)
sub-1.20x      0.1753   (theory 0.175)
median         1.970    (theory 1.980)
autocorrelation 0.00106 (theory 0.000)
chi-square      1.73 on df=5
```

Simulator invariants under test: exact bankroll arithmetic, `wins + losses == rounds`,
bankroll never negative, stop-loss/take-profit boundaries honoured, all five strategies
terminate cleanly, empty input never crashes, and the Monte-Carlo mean is always below
the starting bankroll.

**Two real bugs were caught by these tests during development:**

1. Chi-square used `P(X ≥ 1.00) = 0.99` when it is exactly `1.0` — every round pays at
   least 1.00x. This inflated the bottom bucket and scored fair samples at **chi = 69.6**
   instead of **1.73**, i.e. the app would have told users a provably-fair game looked rigged.
2. The dispersion factor used the coefficient of variation, which is meaningless on a
   heavy-tailed Pareto distribution — a single 5000x round pushed CV to **34** against an
   assumed baseline of 1.8, pinning every window at maximum risk. Replaced with the
   outlier-robust quartile ratio P75/P25 (theoretical baseline 3.0).

---

## Privacy

No internet permission is declared in the manifest. There is no account, no server, no
analytics, and no casino integration. Round data lives in a local Room database and
never leaves the device. The app cannot place bets.

---

## Responsible gambling

Gambling carries real financial risk, and the expected long-run outcome for every player
is a loss. If it is causing you harm:

- [BeGambleAware](https://www.begambleaware.org)
- [Gambling Therapy](https://www.gamblingtherapy.org) (global, multilingual)
- [Gamblers Anonymous](https://www.gamblersanonymous.org)
- [GamCare](https://www.gamcare.org.uk)

Most operators also offer deposit limits, cool-off periods, and self-exclusion.
18+ / 21+ depending on jurisdiction. Gambling may be restricted or illegal where you live.

---

## Licence & disclaimer

Provided for educational and analytical purposes only. Not affiliated with, endorsed by,
or connected to 1xBet, Roobet, Stake, or any gambling operator. The authors accept no
responsibility for losses incurred through gambling.
