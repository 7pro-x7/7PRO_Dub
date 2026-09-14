package com.arena.arabicdub

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arena.arabicdub.domain.SourceSpec
import com.arena.arabicdub.ui.DubViewModel
import com.arena.arabicdub.ui.RunStatus
import com.arena.arabicdub.ui.UiState
import com.arena.arabicdub.ui.screens.ErrorScreen
import com.arena.arabicdub.ui.screens.HomeScreen
import com.arena.arabicdub.ui.screens.ProgressScreen
import com.arena.arabicdub.ui.screens.ResultScreen
import com.arena.arabicdub.ui.theme.ArabicDubTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DubViewModel by viewModels()

    // على Android 8 و الأدنى نحتاج إذن الكتابة لحفظ الملف في المكتبة
    private var pendingSpec: SourceSpec? = null
    private var permissionAsked = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val spec = pendingSpec
            pendingSpec = null
            permissionAsked = false
            if (granted && spec != null) {
                viewModel.start(spec)
            }
        }

    private fun startWithPermission(spec: SourceSpec) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingSpec = spec
            if (!permissionAsked) {
                permissionAsked = true
                requestPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            viewModel.start(spec)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ArabicDubTheme {
                // واجهة عربية بالكامل — اتجاه RTL دائمًا
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val state by viewModel.ui.collectAsState()
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = { TopBar() },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            when (state.status) {
                                RunStatus.IDLE -> HomeScreen(
                                    state = state,
                                    onStart = { startWithPermission(it) },
                                    viewModel = viewModel,
                                )

                                RunStatus.RUNNING -> ProgressScreen(state = state, viewModel = viewModel)
                                RunStatus.DONE -> ResultScreen(state = state, viewModel = viewModel)
                                RunStatus.ERROR -> ErrorScreen(state = state, viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = "دُبّل",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "دبلجة الفيديو من الإنجليزية إلى العربية — مجاني ومفتوح المصدر",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Start,
        )
    }
}
