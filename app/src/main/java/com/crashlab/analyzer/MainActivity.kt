package com.crashlab.analyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crashlab.analyzer.ui.screens.DashboardScreen
import com.crashlab.analyzer.ui.screens.DisclaimerScreen
import com.crashlab.analyzer.ui.screens.LogScreen
import com.crashlab.analyzer.ui.screens.SafetyScreen
import com.crashlab.analyzer.ui.screens.SimulatorScreen
import com.crashlab.analyzer.ui.screens.TrendsScreen
import com.crashlab.analyzer.ui.theme.CrashLabTheme
import com.crashlab.analyzer.ui.theme.RiskAmber
import com.crashlab.analyzer.ui.vm.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CrashLabTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) { CrashLabApp() }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("Overview", Icons.Default.Insights),
    TRENDS("Trends", Icons.Default.ShowChart),
    SIMULATOR("Simulate", Icons.Default.Science),
    LOG("Log", Icons.Default.PlaylistAdd),
    SAFETY("Safety", Icons.Default.Shield)
}

@Composable
fun CrashLabApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sim by vm.sim.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    val snackbar = remember { SnackbarHostState() }

    // Session timer for the reality check banner.
    val sessionStart = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsedMinutes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            elapsedMinutes = (System.currentTimeMillis() - sessionStart.longValue) / 60_000L
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    if (!state.settings.acceptedDisclaimer) {
        DisclaimerScreen(onAccept = vm::acceptDisclaimer)
        return
    }

    val showRealityCheck = state.settings.realityCheckEnabled &&
        elapsedMinutes >= state.settings.sessionLimitMinutes

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (showRealityCheck) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(RiskAmber.copy(alpha = 0.18f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⏱ You have had this app open for $elapsedMinutes minutes. " +
                            "Consider taking a break.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.DASHBOARD -> DashboardScreen(
                        state = state,
                        onWindowChange = vm::setWindow
                    )
                    Tab.TRENDS -> TrendsScreen(state)
                    Tab.SIMULATOR -> SimulatorScreen(
                        sim = sim,
                        dataCount = state.allRounds.size,
                        onConfig = vm::updateConfig,
                        onRun = vm::runSimulation
                    )
                    Tab.LOG -> LogScreen(
                        rounds = state.allRounds,
                        onAdd = vm::addRound,
                        onImport = vm::importBulk,
                        onDelete = vm::deleteRound,
                        onSeedDemo = vm::seedDemo,
                        onClearAll = vm::clearAll
                    )
                    Tab.SAFETY -> SafetyScreen(
                        realityCheck = state.settings.realityCheckEnabled,
                        sessionLimit = state.settings.sessionLimitMinutes,
                        onRealityCheck = vm::setRealityCheck,
                        onSessionLimit = vm::setSessionLimit
                    )
                }
            }
        }
    }
}
