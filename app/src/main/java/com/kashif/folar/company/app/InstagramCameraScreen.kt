package com.kashif.folar.company.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Flashlight
import com.composables.icons.lucide.FlashlightOff
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.SwitchCamera
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.X
import com.kashif.folar.controller.CameraController
import com.kashif.folar.enums.AspectRatio
import com.kashif.folar.enums.CameraDeviceType
import com.kashif.folar.enums.FlashMode
import com.kashif.folar.enums.ImageFormat
import com.kashif.folar.enums.QualityPrioritization
import com.kashif.folar.enums.TorchMode
import com.kashif.folar.result.ImageCaptureResult
import com.kashif.folar.state.FolarState
import com.kashif.folar.utils.NativeBridge
import com.kashif.imagesaverplugin.ImageSaverPlugin
import com.kashif.ocrPlugin.OcrPlugin
import com.kashif.qrscannerplugin.QRScannerPlugin
import com.kashif.qrscannerplugin.QRResult
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import android.net.Uri
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CameraMode(val label: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    // SCAN mode removed, integrated into Photo/Video
    TEXT("TEXT OCR")
}

@Composable
fun InstagramCameraScreen(
    cameraState: FolarState.Ready,
    imageSaverPlugin: ImageSaverPlugin,
    qrScannerPlugin: QRScannerPlugin,
    ocrPlugin: OcrPlugin,
    detectedQR: QRResult?,
    recognizedText: String?,
    aspectRatio: AspectRatio,
    resolution: Pair<Int, Int>?,
    imageFormat: ImageFormat,
    qualityPrioritization: QualityPrioritization,
    cameraDeviceType: CameraDeviceType,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onResolutionChange: (Pair<Int, Int>?) -> Unit,
    onImageFormatChange: (ImageFormat) -> Unit,
    onQualityPrioritizationChange: (QualityPrioritization) -> Unit,
    onCameraDeviceTypeChange: (CameraDeviceType) -> Unit,
    onToggleApi: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val cameraController = cameraState.controller

    // States
    var currentMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var isRecording by remember { mutableStateOf(false) }
    var isStabilizing by remember { mutableStateOf(false) }
    // Removed isProcessingPhoto (no longer blocking)
    var lastCapturedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var selectedRatio by remember { mutableStateOf(AspectRatio.RATIO_3_4) } // Default to Portrait 3:4

    // Plugin States
    var isQRScanningEnabled by remember { mutableStateOf(false) }
    var isOCREnabled by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Logic to handle mode switching side effects
    LaunchedEffect(currentMode, cameraController) {
        // Reset plugin states
        // QR Scanning enabled in PHOTO and VIDEO modes
        isQRScanningEnabled = (currentMode == CameraMode.PHOTO || currentMode == CameraMode.VIDEO)
        isOCREnabled = (currentMode == CameraMode.TEXT)

        // Handle Plugin Activation
        if (isQRScanningEnabled) {
            // Wait for camera to be ready (rudimentary check, better to have isReady state)
            delay(1000)
            try {
                qrScannerPlugin.startScanning()
            } catch (e: Exception) {
                // Ignore if camera not ready
            }
        } else {
            try {
                qrScannerPlugin.pauseScanning()
            } catch (e: Exception) {}
        }

        if (isOCREnabled) {
            delay(1000)
            try {
                ocrPlugin.startRecognition()
            } catch (e: Exception) {
                // Ignore
            }
        } else {
             try {
                ocrPlugin.stopRecognition()
            } catch (e: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount < -50) {
                        // Swipe Left
                         val modes = CameraMode.entries
                         val nextIndex = (modes.indexOf(currentMode) + 1).coerceAtMost(modes.size - 1)
                         currentMode = modes[nextIndex]
                    } else if (dragAmount > 50) {
                        // Swipe Right
                         val modes = CameraMode.entries
                         val prevIndex = (modes.indexOf(currentMode) - 1).coerceAtLeast(0)
                         currentMode = modes[prevIndex]
                    }
                }
            }
    ) {

        // 1. Fullscreen Preview Overlay (already handled by FolarScreen under this, but we put UI on top)
        // Note: FolarScreen renders the preview. We are just the UI overlay.

        // 1.5 Letterboxing Masks
        val ratioValue = when(selectedRatio) {
            AspectRatio.RATIO_4_3 -> 3f/4f
            AspectRatio.RATIO_3_4 -> 3f/4f // Portrait 3:4 is physically same ratio value (0.75) for math
            AspectRatio.RATIO_16_9 -> 9f/16f
            AspectRatio.RATIO_1_1 -> 1f
            AspectRatio.RATIO_9_16 -> 9f/16f
            AspectRatio.RATIO_4_5 -> 4f/5f
        }

        // Draw black bars (Letterboxing)
        // We show bars for everything except 9:16 which is "Full" for this context
        if (selectedRatio != AspectRatio.RATIO_9_16) {
             Canvas(modifier = Modifier.fillMaxSize()) {
                 val screenW = size.width
                 val screenH = size.height

                 // Calculate target height based on width (assuming width matches screen)
                 // For 1:1, H = W. For 4:3, H = W / (3/4) = W * 1.33
                 val targetH = screenW / ratioValue

                 if (targetH < screenH) {
                     val barHeight = (screenH - targetH) / 2

                     // Top Bar
                     drawRect(
                         color = Color.Black,
                         topLeft = Offset(0f, 0f),
                         size = Size(screenW, barHeight)
                     )

                     // Bottom Bar
                     drawRect(
                         color = Color.Black,
                         topLeft = Offset(0f, screenH - barHeight),
                         size = Size(screenW, barHeight)
                     )
                 }
             }
        }

        // 2. Top Controls
        TopControlBar(
            modifier = Modifier.align(Alignment.TopCenter),
            flashMode = flashMode,
            onFlashToggle = {
                cameraController.toggleFlashMode()
                flashMode = cameraController.getFlashMode() ?: FlashMode.OFF
            },
            selectedRatio = selectedRatio,
            onRatioToggle = {
                // Cycle: 1:1 -> 4:5 -> 3:4 -> 9:16
                val newRatio = when(selectedRatio) {
                    AspectRatio.RATIO_1_1 -> AspectRatio.RATIO_4_5
                    AspectRatio.RATIO_4_5 -> AspectRatio.RATIO_3_4
                    AspectRatio.RATIO_3_4 -> AspectRatio.RATIO_9_16
                    AspectRatio.RATIO_9_16 -> AspectRatio.RATIO_1_1
                    else -> AspectRatio.RATIO_1_1 // Default fallback for old states
                }

                selectedRatio = newRatio

                // Map to CameraX supported ratios (4:3 or 16:9)
                val cameraXRatio = when(newRatio) {
                    AspectRatio.RATIO_1_1 -> AspectRatio.RATIO_4_3
                    AspectRatio.RATIO_4_5 -> AspectRatio.RATIO_4_3
                    AspectRatio.RATIO_3_4 -> AspectRatio.RATIO_4_3
                    AspectRatio.RATIO_9_16 -> AspectRatio.RATIO_16_9
                    else -> AspectRatio.RATIO_4_3
                }
                onAspectRatioChange(cameraXRatio)
            },
            onSettingsClick = { /* Open Bottom Sheet if needed */ },
            onToggleApi = onToggleApi
        )

        // 3. Plugin Outputs (Middle Overlay) - Using AnimatedVisibility instead of if/else to avoid node destruction during measure
        PluginOutputs(
            modifier = Modifier.align(Alignment.Center),
            currentMode = currentMode,
            detectedQR = detectedQR,
            recognizedText = recognizedText
        )

        // 4. Bottom Area (Gradient Background)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Selector
                SmoothModeSelector(
                    currentMode = currentMode,
                    onModeSelected = { currentMode = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Controls Row (Gallery, Shutter, Switch)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Gallery (Placeholder)
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            // Try to open gallery
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        lastCapturedImage?.let {
                             ComposeImage(bitmap = it, contentDescription = "Gallery", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } ?: Icon(imageVector = Lucide.Image, contentDescription = "Gallery", tint = Color.White)
                    }

                    // Shutter Button
                    ShutterButton(
                        currentMode = currentMode,
                        isRecording = isRecording,
                        onClick = {
                            when(currentMode) {
                                CameraMode.PHOTO -> {
                                    scope.launch {
                                        handlePhotoCapture(cameraController, imageSaverPlugin, selectedRatio) { bmp ->
                                            lastCapturedImage = bmp
                                        }
                                    }
                                }
                                CameraMode.VIDEO -> {
                                    if (isRecording) {
                                        // Stop Recording
                                        isRecording = false
                                        cameraController.stopRecording()
                                        // Stabilization is triggered in the callback passed to startRecording
                                    } else {
                                        // Start Recording
                                        isRecording = true
                                        cameraController.startRecording(
                                            onVideoSaved = { videoFile ->
                                                // Trigger Stabilization
                                                isStabilizing = true
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val outputFile = File(videoFile.parent, "STAB_${videoFile.name}")
                                                        NativeBridge.stabilizeVideo(videoFile.absolutePath, outputFile.absolutePath)

                                                        // Update the video file reference if we want to preview/save the stabilized one
                                                        // For now, we just notify the gallery of the new file
                                                        android.media.MediaScannerConnection.scanFile(
                                                            context,
                                                            arrayOf(outputFile.absolutePath),
                                                            arrayOf("video/mp4"),
                                                            null
                                                        )

                                                        withContext(Dispatchers.Main) {
                                                            isStabilizing = false
                                                            // Provide feedback to user
                                                            android.widget.Toast.makeText(context, "Video stabilized and saved!", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        withContext(Dispatchers.Main) {
                                                            isStabilizing = false
                                                        }
                                                    }
                                                }
                                            },
                                            onError = {
                                                isRecording = false
                                            }
                                        )
                                    }
                                }
                                else -> { /* No action for scan modes generally, or maybe capture frame */ }
                            }
                        }
                    )

                    // Switch Camera
                    IconButton(
                        onClick = {
                            cameraController.toggleCameraLens()
                            // Force state update to refresh UI if needed, though toggleCameraLens should trigger recomposition if state is observed correctly
                            // We can also cycle device types if that's what user expects, but usually switch is Front/Back
                        },
                        modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(imageVector = Lucide.SwitchCamera, contentDescription = "Switch", tint = Color.White)
                    }
                }
            }
        }

        // 5. Processing Overlay (Only for Video Stabilization now)
        if (isStabilizing) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = Color.Black)
                        Text("Stabilizing Video...", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Please wait while we process using NativeBridge + OpenCV", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        // 6. Image Preview Overlay (Temporary)
        lastCapturedImage?.let { bitmap ->
            // Only show for 3 seconds then fade out
            LaunchedEffect(bitmap) {
                delay(3000)
                lastCapturedImage = null
            }
            // Small preview handled in gallery button, but if we want full screen preview:
            // Keeping it simple for now as requested "Instagram like"
        }
    }
}

@Composable
fun TopControlBar(
    modifier: Modifier = Modifier,
    flashMode: FlashMode,
    selectedRatio: AspectRatio,
    onFlashToggle: () -> Unit,
    onRatioToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleApi: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSettingsClick) {
            Icon(imageVector = Lucide.Settings, contentDescription = "Settings", tint = Color.White)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Aspect Ratio Button
            val ratioText = when(selectedRatio) {
                AspectRatio.RATIO_1_1 -> "1:1"
                AspectRatio.RATIO_4_5 -> "4:5"
                AspectRatio.RATIO_3_4 -> "3:4"
                AspectRatio.RATIO_9_16 -> "9:16"
                // Handle legacy enums just in case to avoid crash, map to nearest visual
                AspectRatio.RATIO_4_3 -> "3:4"
                AspectRatio.RATIO_16_9 -> "9:16"
            }
            Text(
                text = ratioText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(1.dp, Color.White, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable { onRatioToggle() }
            )

            IconButton(onClick = onFlashToggle) {
                Icon(
                    imageVector = when(flashMode) {
                        FlashMode.ON -> Lucide.Flashlight
                        FlashMode.OFF -> Lucide.FlashlightOff
                        FlashMode.AUTO -> Lucide.Flashlight
                    },
                    contentDescription = "Flash",
                    tint = Color.White
                )
            }
        }

        IconButton(onClick = onToggleApi) {
             // Hidden switch logic if needed, or just an icon
             Icon(imageVector = Lucide.ChevronLeft, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
fun SmoothModeSelector(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraMode.entries.forEach { mode ->
            val isSelected = (mode == currentMode)
            val alpha by animateFloatAsState(if (isSelected) 1f else 0.5f)
            val scale by animateFloatAsState(if (isSelected) 1.1f else 0.9f)

            Text(
                text = mode.label,
                color = Color.White.copy(alpha = alpha),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .scale(scale)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onModeSelected(mode) }
            )
        }
    }
}

@Composable
fun ShutterButton(
    currentMode: CameraMode,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val size by animateFloatAsState(if (isRecording) 80f else 72f)
    val innerSize by animateFloatAsState(if (isRecording) 30f else 60f)
    val cornerRadius by animateFloatAsState(if (isRecording) 8f else 50f) // Square when recording
    val color = if (currentMode == CameraMode.VIDEO) Color.Red else Color.White

    Box(
        modifier = Modifier
            .size(90.dp) // Outer ring container
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer Ring
        Box(
            modifier = Modifier
                .size(size.dp)
                .border(4.dp, Color.White, CircleShape)
        )

        // Inner Button
        Box(
            modifier = Modifier
                .size(innerSize.dp)
                .background(color, RoundedCornerShape(cornerRadius.toInt()))
        )
    }
}

@Composable
fun PluginOutputs(
    modifier: Modifier,
    currentMode: CameraMode,
    detectedQR: QRResult?,
    recognizedText: String?
) {
    val context = LocalContext.current

    // Use Crossfade or explicit box with conditional visibility to prevent "LayoutNode detached" issues.
    // Instead of completely removing the nodes from the tree, we keep them but handle visibility.
    // Or simpler: Wrap in AnimatedVisibility which handles measurement transitions safely.

    // QR Code Overlay (Tracking + Content)
    val isQRVisible = (detectedQR != null && (currentMode == CameraMode.PHOTO || currentMode == CameraMode.VIDEO))

    AnimatedVisibility(
        visible = isQRVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        if (detectedQR != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Draw tracking lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                     val canvasWidth = size.width
                     val canvasHeight = size.height

                     if (detectedQR.points.size >= 3) {
                         val path = Path().apply {
                             val p0 = detectedQR.points[0]
                             moveTo(p0.first * canvasWidth, p0.second * canvasHeight)

                             for (i in 1 until detectedQR.points.size) {
                                 val p = detectedQR.points[i]
                                 lineTo(p.first * canvasWidth, p.second * canvasHeight)
                             }
                             close()
                         }

                         drawPath(
                             path = path,
                             color = Color.Yellow,
                             style = Stroke(width = 8f)
                         )

                         for (point in detectedQR.points) {
                             drawCircle(
                                 color = Color.Yellow,
                                 radius = 16f,
                                 center = Offset(point.first * canvasWidth, point.second * canvasHeight)
                             )
                         }
                     }
                }

                // Clickable Content Overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detectedQR.text))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val searchIntent = Intent(Intent.ACTION_WEB_SEARCH)
                                    searchIntent.putExtra(android.app.SearchManager.QUERY, detectedQR.text)
                                    context.startActivity(searchIntent)
                                } catch (e2: Exception) {}
                            }
                        },
                    color = Color.Yellow.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("QR Detected 🔗", fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(detectedQR.text, color = Color.Blue, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("Tap to open", style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    // Text OCR Overlay
    val isTextVisible = (currentMode == CameraMode.TEXT && recognizedText != null)

    AnimatedVisibility(
        visible = isTextVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (recognizedText != null) {
            Surface(modifier = Modifier.padding(16.dp), color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp)) {
                 Text(
                     text = recognizedText,
                     color = Color.White,
                     modifier = Modifier.padding(16.dp),
                     maxLines = 5
                 )
            }
        }
    }
}

private suspend fun handlePhotoCapture(
    cameraController: CameraController,
    imageSaverPlugin: ImageSaverPlugin,
    aspectRatio: AspectRatio,
    onImageCaptured: (ImageBitmap) -> Unit
) {
    when (val result = cameraController.takePictureToFile()) {
        is ImageCaptureResult.SuccessWithFile -> {
            println("Image captured: ${result.filePath}")
            try {
                withContext(Dispatchers.IO) {
                    // 1. Native Processing REMOVED for stability
                    // We rely purely on Kotlin for Aspect Ratio cropping.

                    // 2. Kotlin Cropping (If needed)
                    // CameraX outputs 4:3 or 16:9. We need to crop to target ratio.
                    try {
                        val file = File(result.filePath)
                        // Only crop if ratio is not native (assume native is 4:3 or 16:9)
                        // Simplified: Always check dimensions and crop center

                        // Decode bounds only first
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        val w = options.outWidth
                        val h = options.outHeight

                        if (w > 0 && h > 0) {
                            val targetRatio = when(aspectRatio) {
                                AspectRatio.RATIO_1_1 -> 1.0f
                                AspectRatio.RATIO_4_5 -> 0.8f
                                AspectRatio.RATIO_3_4, AspectRatio.RATIO_4_3 -> 0.75f
                                AspectRatio.RATIO_9_16, AspectRatio.RATIO_16_9 -> 0.5625f
                            }

                            val currentRatio = w.toFloat() / h.toFloat()

                            // Crop only if significant difference
                            if (Math.abs(currentRatio - targetRatio) > 0.01) {
                                val targetW: Int
                                val targetH: Int

                                if (currentRatio > targetRatio) {
                                    // Too wide, crop width
                                    targetH = h
                                    targetW = (h * targetRatio).toInt()
                                } else {
                                    // Too tall, crop height
                                    targetW = w
                                    targetH = (w / targetRatio).toInt()
                                }

                                val cx = (w - targetW) / 2
                                val cy = (h - targetH) / 2

                                // Load only cropped region using BitmapRegionDecoder (Low Memory)
                                val decoder = android.graphics.BitmapRegionDecoder.newInstance(file.absolutePath, false)
                                val region = android.graphics.Rect(cx, cy, cx + targetW, cy + targetH)
                                val croppedBitmap = decoder.decodeRegion(region, BitmapFactory.Options())

                                // Overwrite file
                                file.outputStream().use { out ->
                                    croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                                }
                                croppedBitmap.recycle()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Safe decode for preview
                    val previewOptions = BitmapFactory.Options().apply { inSampleSize = 8 }
                    val bitmap = BitmapFactory.decodeFile(result.filePath, previewOptions)
                    if (bitmap != null) {
                         withContext(Dispatchers.Main) {
                             onImageCaptured(bitmap.asImageBitmap())
                         }
                    }

                    // Notify gallery
                    try {
                        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                        mediaScanIntent.data = Uri.fromFile(File(result.filePath))
                    } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        else -> {}
    }
}