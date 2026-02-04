package com.kashif.folar.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.kashif.folar.builder.CameraControllerBuilder
import com.kashif.folar.builder.createCameraControllerBuilder
import com.kashif.folar.controller.CameraController

/**
 * Android-specific implementation of [CameraPreview].
 *
 * @param modifier Modifier to be applied to the camera preview.
 * @param cameraConfiguration Lambda to configure the [CameraControllerBuilder].
 * @param onCameraControllerReady Callback invoked with the initialized [CameraController].
 */
@Composable
fun CameraPreview(
    modifier: Modifier,
    cameraConfiguration: CameraControllerBuilder.() -> Unit,
    onCameraControllerReady: (CameraController) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val isCameraReady = remember { mutableStateOf(false) }
    val cameraController = remember {
        createCameraControllerBuilder(context, lifecycleOwner)
            .apply(cameraConfiguration)
            .build()
    }


    val previewView = remember { PreviewView(context) }

    DisposableEffect(previewView) {
        cameraController.bindCamera(previewView) {
            cameraController.startSession()
            onCameraControllerReady(cameraController)
        }
        onDispose {
            cameraController.stopSession()
            cameraController.cleanup()
        }
    }



    AndroidView(
        factory = { previewView },
        modifier = modifier,

        )

}