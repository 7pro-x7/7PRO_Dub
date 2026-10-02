package com.rork.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.media.MediaPlayer
import android.widget.VideoView
import com.rork.pro.ui.i18n.StrQuestionVideo
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink

/**
 * Displays a video with automatic orientation based on aspect ratio.
 * 
 * Aspect ratio detection:
 * - > 1.2: Landscape (16:9, etc.)
 * - 0.85-1.2: Square/nearly square (1:1, 4:5, etc.)
 * - < 0.85: Portrait (9:16, etc.)
 */
@Composable
fun QuestionVideo(
    url: String,
    aspectRatio: Float? = null,
    modifier: Modifier = Modifier,
) {
    val orientation = when {
        aspectRatio == null -> VideoOrientation.LANDSCAPE // Default to 16:9
        aspectRatio > 1.2f -> VideoOrientation.LANDSCAPE
        aspectRatio < 0.85f -> VideoOrientation.PORTRAIT
        else -> VideoOrientation.SQUARE
    }

    val finalAspectRatio = when (orientation) {
        VideoOrientation.LANDSCAPE -> aspectRatio ?: (16f / 9f)
        VideoOrientation.PORTRAIT -> aspectRatio ?: (9f / 16f)
        VideoOrientation.SQUARE -> aspectRatio ?: 1f
        VideoOrientation.FIT_PARENT -> aspectRatio ?: (16f / 9f)
    }

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(finalAspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.Surface),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(url)
                    setOnPreparedListener { mp ->
                        isLoading = false
                        mp.start()
                    }
                    setOnErrorListener { _, _, _ ->
                        hasError = true
                        isLoading = false
                        true
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(finalAspectRatio),
        )

        // Loading state
        if (isLoading) {
            CircularProgressIndicator(
                color = Ink.Teal,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Play icon overlay (optional - can be removed if auto-play is preferred)
        if (!isLoading && !hasError) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = tr(StrQuestionVideo.play),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Error state
        if (hasError) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = tr(StrQuestionVideo.failed),
                tint = Ink.Coral,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

enum class VideoOrientation {
    PORTRAIT,    // Height > Width (9:16, etc.)
    LANDSCAPE,   // Width > Height (16:9, etc.)
    SQUARE,      // Nearly equal (1:1, 4:5, etc.)
    FIT_PARENT,  // Fit to parent
}
