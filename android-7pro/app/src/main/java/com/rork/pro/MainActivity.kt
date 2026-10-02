package com.rork.pro

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.rork.pro.data.AppLaunchState
import com.rork.pro.data.Backend
import com.rork.pro.data.PushCenter
import com.rork.pro.data.SessionCache
import io.github.jan.supabase.auth.handleDeeplinks
import com.rork.pro.ui.i18n.AppLanguage
import com.rork.pro.ui.navigation.AppNavigation
import com.rork.pro.ui.components.initAds
import com.rork.pro.ui.navigation.PendingRoute
import com.rork.pro.ui.theme.AppAppearance
import com.rork.pro.ui.theme.AppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Saved choice wins; otherwise follow the device language (Arabic → Arabic, everything else → English).
        AppLanguage.init(this)
        // Lets the app reopen on the last signed-in state instead of a cold boot.
        SessionCache.init(this)
        // Tracks whether this is the very first time the app has been opened on this device.
        AppLaunchState.init(this)
        // Light or dark, as the user last chose on this device.
        AppAppearance.init(this)
        PushCenter.ensureChannel(this)
        // Warms the ads SDK while the first screen loads; a build without an AdMob id skips it.
        initAds(this)
        // Google sign-in finishes in the browser and returns here as a deep link.
        readAuthDeeplink(intent)
        // A tapped notification carries the screen it belongs to.
        readNotificationRoute(intent)
        askForNotifications()
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
        // If the app closed by itself last time, offer the details so it can be fixed.
        CrashCatcher.showPendingIfAny(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readAuthDeeplink(intent)
        readNotificationRoute(intent)
    }

    override fun onResume() {
        super.onResume()
        // Catches anything that arrived while the app was away, without waiting for the next sync.
        lifecycleScope.launch { runCatching { PushCenter.sync(applicationContext) } }
    }

    /** Turns a `sevenpro://auth` callback into a signed-in session. */
    private fun readAuthDeeplink(intent: Intent?) {
        if (!Backend.isConfigured || intent == null) return
        runCatching { Backend.client.handleDeeplinks(intent) }
    }

    /** Remembers where a tapped notification wants to go, for the navigator to pick up. */
    private fun readNotificationRoute(intent: Intent?) {
        val route = intent?.getStringExtra(PushCenter.EXTRA_ROUTE)?.takeIf { it.isNotBlank() } ?: return
        PendingRoute.set(route)
        intent.removeExtra(PushCenter.EXTRA_ROUTE)
    }

    /** Android 13+ only shows notifications once the person has agreed to them. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}
