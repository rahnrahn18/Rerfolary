package com.kashif.folar.compose

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kashif.folar.state.FolarState
import com.kashif.folar.state.FolarStateHolder

/**
 * CompositionLocal for providing [FolarStateHolder] to descendants.
 * Use this to avoid passing the state holder through multiple levels of composables.
 */
val LocalFolarStateHolder = compositionLocalOf<FolarStateHolder?> { null }

/**
 * Root camera screen that provides state to all descendants.
 * This composable manages the full camera lifecycle and provides a slot-based API.
 * 
 * Uses [Crossfade] to stabilize state transitions and prevent "LayoutNode should be attached to an owner"
 * crashes caused by rapid destruction/creation of AndroidView nodes.
 */
@Composable
fun FolarScreen(
    modifier: Modifier = Modifier,
    cameraState: FolarState,
    loadingContent: @Composable () -> Unit = { DefaultLoadingScreen() },
    errorContent: @Composable (FolarState.Error) -> Unit = { DefaultErrorScreen(it) },
    showPreview: Boolean = true,
    content: @Composable (FolarState.Ready) -> Unit
) {
    Box(modifier = modifier) {
        Crossfade(
            targetState = cameraState,
            animationSpec = tween(durationMillis = 300),
            label = "CameraStateTransition"
        ) { state ->
            when (state) {
                is FolarState.Initializing -> {
                    loadingContent()
                }
                is FolarState.Ready -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (showPreview) {
                            CameraPreviewView(
                                controller = state.controller,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        content(state)
                    }
                }
                is FolarState.Error -> {
                    errorContent(state)
                }
            }
        }
    }
}

/**
 * Default loading screen shown during camera initialization.
 */
@Composable
fun DefaultLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color.White)
            Text(
                text = "Initializing Camera...",
                color = Color.White
            )
        }
    }
}

/**
 * Default error screen shown when camera initialization fails.
 */
@Composable
fun DefaultErrorScreen(errorState: FolarState.Error) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Camera Error",
                color = Color.Red
            )
            Text(
                text = errorState.message,
                color = Color.White
            )
            if (errorState.isRetryable) {
                Text(
                    text = "Please try again",
                    color = Color.Gray
                )
            }
        }
    }
}
