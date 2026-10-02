package com.rork.pro.ui.screens.onboarding

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.pro.data.AppLaunchState
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.i18n.StrOnboarding
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.theme.appBackdrop
import kotlinx.coroutines.launch

private data class OnboardingPage(val icon: ImageVector, val accent: Color, val title: Tr, val body: Tr)

private val onboardingPages
    get() = listOf(
        OnboardingPage(Icons.Default.AutoAwesome, Ink.Amber, StrOnboarding.title1, StrOnboarding.body1),
        OnboardingPage(Icons.Default.Route, Ink.Teal, StrOnboarding.title2, StrOnboarding.body2),
        OnboardingPage(Icons.AutoMirrored.Filled.TrendingUp, Ink.Sky, StrOnboarding.title3, StrOnboarding.body3),
    )

/**
 * The one-time "what is 7PRO" carousel, shown before Auth on a device's very first launch only
 * — [AppLaunchState.hasSeenOnboarding] gates it out of the tree entirely afterwards, so it never
 * costs a recomposition or a route on any later launch. [onDone] hands control back to
 * `AppNavigation`, which re-reads that flag and moves straight to Auth or Home.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val items = remember { onboardingPages }
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val isLastPage by remember { derivedStateOf { pagerState.currentPage == items.lastIndex } }

    fun finish() {
        AppLaunchState.markOnboardingSeen()
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .appBackdrop(),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!isLastPage) {
                    TextButton(onClick = { finish() }) {
                        Text(tr(StrOnboarding.skip), color = Ink.TextMuted, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                OnboardingPageContent(items[page])
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                items.indices.forEach { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) items[pagerState.currentPage].accent else Ink.Hairline),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp),
            ) {
                PrimaryAction(
                    label = if (isLastPage) tr(StrOnboarding.getStarted) else tr(StrOnboarding.next),
                ) {
                    if (isLastPage) {
                        finish()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(320))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(page.accent.copy(alpha = 0.22f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(page.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, null, tint = page.accent, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(36.dp))
        Text(
            tr(page.title),
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            tr(page.body),
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
