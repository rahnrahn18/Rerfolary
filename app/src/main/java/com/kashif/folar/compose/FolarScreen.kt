package com.kashif.folar.compose

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
 * 
 * @example
 * ```kotlin
 * CompositionLocalProvider(LocalFolarStateHolder provides stateHolder) {
 *     // Descendants can access stateHolder via LocalFolarStateHolder.current
 *     CameraControls()
 * }
 * ```
 */
val LocalFolarStateHolder = compositionLocalOf<FolarStateHolder?> { null }

/**
 * Root camera screen that provides state to all descendants.
 * This composable manages the full camera lifecycle and provides a slot-based API.
 * 
 * @param modifier Modifier for the root container.
 * @param cameraState The current camera state from [rememberFolarState].
 * @param loadingContent Composable to show during initialization (default: loading indicator).
 * @param errorContent Composable to show on error (default: error message).
 * @param showPreview Whether to automatically show the camera preview (default: true).
 * @param content Main content to display when camera is ready. Receives the Ready state.
 * 
 * @example
 * ```kotlin
 * @Composable
 * fun MyCameraApp() {
 *     val cameraState by rememberFolarState()
 *     
 *     FolarScreen(
 *         cameraState = cameraState,
 *         loadingContent = { CustomLoadingSpinner() },
 *         errorContent = { error -> CustomErrorUI(error) }
 *     ) { state ->
 *         // Camera preview is shown automatically
 *         // Add controls overlay here
 *         Box(modifier = Modifier.fillMaxSize()) {
 *             CameraControls(
 *                 uiState = state.uiState,
 *                 modifier = Modifier.align(Alignment.BottomCenter)
 *             )
 *         }
 *     }
 * }
 * ```
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
        when (cameraState) {
            is FolarState.Initializing -> loadingContent()
            is FolarState.Ready -> {
                if (showPreview) {
                    CameraPreviewView(
                        controller = cameraState.controller,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                content(cameraState)
            }
            is FolarState.Error -> errorContent(cameraState)
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
 * 
 * @param errorState The error state containing exception and message.
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
