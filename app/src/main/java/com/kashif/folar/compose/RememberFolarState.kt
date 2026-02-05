package com.kashif.folar.compose

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kashif.folar.builder.createCameraControllerBuilder
import com.kashif.folar.state.CameraConfiguration
import com.kashif.folar.state.FolarState
import com.kashif.folar.state.FolarStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of [rememberFolarState].
 * Automatically manages Context and LifecycleOwner dependencies.
 */
@Composable
fun rememberFolarState(
    config: CameraConfiguration,
    setupPlugins: suspend (FolarStateHolder) -> Unit
): State<FolarState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    // Remember StateHolder once, not keyed by config to prevent full recreation loop
    val stateHolder = remember {
        FolarStateHolder(
            cameraConfiguration = config,
            controllerFactory = {
                withContext(Dispatchers.Main) {
                    createCameraControllerBuilder(context, lifecycleOwner)
                        .apply {
                            setFlashMode(config.flashMode)
                            setTorchMode(config.torchMode)
                            setCameraLens(config.cameraLens)
                            setImageFormat(config.imageFormat)
                            setQualityPrioritization(config.qualityPrioritization)
                            setPreferredCameraDeviceType(config.cameraDeviceType)
                            setAspectRatio(config.aspectRatio)
                            setDirectory(config.directory)
                            config.targetResolution?.let { (width, height) ->
                                setResolution(width, height)
                            }
                        }
                        .build()
                }
            },
            coroutineScope = scope
        )
    }
    
    // Initial setup
    LaunchedEffect(stateHolder) {
        setupPlugins(stateHolder)
        stateHolder.initialize()
    }
    
    // Handle configuration updates dynamically without recreating StateHolder/Controller
    LaunchedEffect(config) {
        stateHolder.updateConfiguration(config)
    }

    // Cleanup on disposal
    DisposableEffect(stateHolder) {
        onDispose {
            stateHolder.shutdown()
        }
    }
    
    return stateHolder.cameraState.collectAsStateWithLifecycle()
}
