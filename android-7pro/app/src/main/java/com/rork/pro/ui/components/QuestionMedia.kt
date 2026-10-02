package com.rork.pro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.delay

/**
 * Compact audio player used by listening questions.
 *
 * A test is not a media library: there is one clip, one button and a progress line, so the
 * learner can replay it without ever leaving the question.
 */
@Composable
fun QuestionAudio(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var playing by remember(url) { mutableStateOf(false) }
    var ended by remember(url) { mutableStateOf(false) }
    var progress by remember(url) { mutableFloatStateOf(0f) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    ended = true
                    progress = 1f
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Leaving the screen must stop the sound, otherwise a clip keeps playing over the next question.
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(playing) {
        while (playing) {
            val total = player.duration
            progress = if (total > 0) (player.currentPosition.toFloat() / total).coerceIn(0f, 1f) else 0f
            delay(200)
        }
    }

    val width by animateFloatAsState(progress, tween(220), label = "audio")

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(
            onClick = {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (ended) {
                        player.seekTo(0)
                        ended = false
                    }
                    player.play()
                }
            },
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Ink.AmberSoft),
        ) {
            Icon(
                when {
                    playing -> Icons.Default.Pause
                    ended -> Icons.Default.Replay
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = tr(Str.play),
                tint = Ink.Amber,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(tr(Str.listen), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.SurfaceHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(width)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Ink.Amber),
                )
            }
        }
    }
}

/** Fixed 16:9 frame for a question's illustration. */
@Composable
fun QuestionImage(url: String, modifier: Modifier = Modifier) {
    CoverImage(
        url,
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp)),
    )
}
