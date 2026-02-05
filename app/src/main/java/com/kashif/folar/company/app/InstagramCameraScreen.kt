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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import androidx.compose.ui.platform.LocalContext
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Flashlight
import com.composables.icons.lucide.FlashlightOff
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scan
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.SwitchCamera
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.X
import com.kashif.folar.controller.CameraController
import com.kashif.folar.enums.AspectRatio
import com.kashif.folar.enums.CameraDeviceType
import com.kashif.folar.enums.CameraLens
import com.kashif.folar.enums.FlashMode
import com.kashif.folar.enums.ImageFormat
import com.kashif.folar.enums.QualityPrioritization
import com.kashif.folar.enums.TorchMode
import com.kashif.folar.result.ImageCaptureResult
import com.kashif.folar.state.FolarState
import com.kashif.folar.utils.NativeBridge
import com.kashif.imagesaverplugin.ImageSaverPlugin
import com.kashif.ocrPlugin.OcrPlugin
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import android.net.Uri
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File

enum class CameraMode(val label: String) {
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    TEXT("TEXT OCR")
}

@Composable
fun InstagramCameraScreen(
    cameraState: FolarState.Ready,
    imageSaverPlugin: ImageSaverPlugin,
    ocrPlugin: OcrPlugin,
    recognizedText: String?,
    aspectRatio: AspectRatio,
    resolution: Pair<Int, Int>?,
    imageFormat: ImageFormat,
    qualityPrioritization: QualityPrioritization,
    cameraDeviceType: CameraDeviceType,
    cameraLens: CameraLens,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onResolutionChange: (Pair<Int, Int>?) -> Unit,
    onImageFormatChange: (ImageFormat) -> Unit,
    onQualityPrioritizationChange: (QualityPrioritization) -> Unit,
    onCameraDeviceTypeChange: (CameraDeviceType) -> Unit,
    onCameraLensChange: (CameraLens) -> Unit
) {
    val scope = rememberCoroutineScope()
    val cameraController = cameraState.controller

    // States
    var currentMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("Processing...") }
    var lastCapturedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastMediaUri by remember { mutableStateOf<Uri?>(null) }
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }

    // Smart Features State
    var isSmartStabilizationOn by remember { mutableStateOf(false) }
    var isObjectTrackingOn by remember { mutableStateOf(false) }

    // Video Quality State (HD vs FHD)
    var isFHDQuality by remember { mutableStateOf(false) }

    // Plugin States
    var isOCREnabled by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Logic to handle mode switching side effects
    LaunchedEffect(currentMode, cameraController) {
        // Reset plugin states
        isOCREnabled = (currentMode == CameraMode.TEXT)

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

        // 1. Fullscreen Preview Overlay

        // 1.5 Letterboxing Masks
        // Disable masks in Video mode (Native Ratio)
        if (currentMode != CameraMode.VIDEO) {
            val ratioValue = when(aspectRatio) {
                AspectRatio.RATIO_4_3 -> 3f/4f
                AspectRatio.RATIO_3_4 -> 3f/4f
                AspectRatio.RATIO_16_9 -> 9f/16f
                AspectRatio.RATIO_1_1 -> 1f
                AspectRatio.RATIO_9_16 -> 9f/16f
                AspectRatio.RATIO_4_5 -> 4f/5f
            }

            // Draw black bars (Letterboxing)
            if (aspectRatio != AspectRatio.RATIO_9_16) {
                 Canvas(modifier = Modifier.fillMaxSize()) {
                     val screenW = size.width
                     val screenH = size.height
                     val targetH = screenW / ratioValue

                     if (targetH < screenH) {
                         val barHeight = (screenH - targetH) / 2
                         // Top Bar
                         drawRect(color = Color.Black, topLeft = Offset(0f, 0f), size = Size(screenW, barHeight))
                         // Bottom Bar
                         drawRect(color = Color.Black, topLeft = Offset(0f, screenH - barHeight), size = Size(screenW, barHeight))
                     }
                 }
            }
        }

        // 2. Top Controls
        TopControlBar(
            modifier = Modifier.align(Alignment.TopCenter),
            flashMode = flashMode,
            currentMode = currentMode,
            aspectRatio = aspectRatio,
            isFHDQuality = isFHDQuality,
            onFlashToggle = {
                cameraController.toggleFlashMode()
                flashMode = cameraController.getFlashMode() ?: FlashMode.OFF
            },
            onAspectRatioToggle = {
                val newRatio = when(aspectRatio) {
                    AspectRatio.RATIO_1_1 -> AspectRatio.RATIO_4_5
                    AspectRatio.RATIO_4_5 -> AspectRatio.RATIO_3_4
                    AspectRatio.RATIO_3_4 -> AspectRatio.RATIO_9_16
                    AspectRatio.RATIO_9_16 -> AspectRatio.RATIO_1_1
                    else -> AspectRatio.RATIO_1_1
                }
                onAspectRatioChange(newRatio)
            },
            onQualityToggle = {
                isFHDQuality = !isFHDQuality
                cameraController.setVideoQuality(isFHDQuality)
            },
            onSettingsClick = { /* Open Bottom Sheet if needed */ }
        )

        // 3. Plugin Outputs
        PluginOutputs(
            modifier = Modifier.align(Alignment.Center),
            currentMode = currentMode,
            recognizedText = recognizedText
        )

        // 3.5 Right Control Bar (Video Features)
        if (currentMode == CameraMode.VIDEO) {
            RightControlBar(
                modifier = Modifier.align(Alignment.CenterEnd),
                isStabilizationOn = isSmartStabilizationOn,
                isTrackingOn = isObjectTrackingOn,
                onToggleStabilization = {
                    isSmartStabilizationOn = !isSmartStabilizationOn
                    if (isSmartStabilizationOn) isObjectTrackingOn = false
                },
                onToggleTracking = {
                    isObjectTrackingOn = !isObjectTrackingOn
                    if (isObjectTrackingOn) isSmartStabilizationOn = false
                }
            )
        }

        // 4. Bottom Area
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

                // Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Gallery
                    IconButton(
                        onClick = {
                            try {
                                if (lastMediaUri != null) {
                                    val type = context.contentResolver.getType(lastMediaUri!!) ?: "*/*"
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                         setDataAndType(lastMediaUri, type)
                                         addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                val intent = Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                                context.startActivity(intent)
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
                                        handlePhotoCapture(context, cameraController, imageSaverPlugin, aspectRatio) { bmp, uri ->
                                            lastCapturedImage = bmp
                                            lastMediaUri = uri
                                        }
                                    }
                                }
                                CameraMode.VIDEO -> {
                                    if (isRecording) {
                                        isRecording = false
                                        cameraController.stopRecording()
                                    } else {
                                        isRecording = true
                                        cameraController.startRecording(
                                            onVideoSaved = { videoFile ->
                                                // Scan to get Uri for gallery
                                                MediaScannerConnection.scanFile(context, arrayOf(videoFile.absolutePath), arrayOf("video/mp4")) { _, uri ->
                                                    scope.launch(Dispatchers.Main) {
                                                        lastMediaUri = uri
                                                    }
                                                }

                                                if (isSmartStabilizationOn || isObjectTrackingOn) {
                                                    isProcessing = true
                                                    processingMessage = if (isSmartStabilizationOn) "Stabilizing Video..." else "Tracking Object..."

                                                    scope.launch(Dispatchers.IO) {
                                                        try {
                                                            val outputFile = File(videoFile.parent, "PROCESSED_${videoFile.name}")

                                                            if (isSmartStabilizationOn) {
                                                                 NativeBridge.stabilizeVideo(videoFile.absolutePath, outputFile.absolutePath)
                                                            } else if (isObjectTrackingOn) {
                                                                 NativeBridge.trackObjectVideo(videoFile.absolutePath, outputFile.absolutePath)
                                                            }

                                                            // Update Gallery Uri to processed file
                                                            MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), arrayOf("video/mp4")) { _, uri ->
                                                                scope.launch(Dispatchers.Main) {
                                                                    lastMediaUri = uri
                                                                }
                                                            }

                                                            withContext(Dispatchers.Main) {
                                                                isProcessing = false
                                                                android.widget.Toast.makeText(context, "Video Processed & Saved!", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                            withContext(Dispatchers.Main) {
                                                                isProcessing = false
                                                                android.widget.Toast.makeText(context, "Processing Failed", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    scope.launch(Dispatchers.Main) {
                                                        android.widget.Toast.makeText(context, "Video Saved!", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onError = {
                                                isRecording = false
                                            }
                                        )
                                    }
                                }
                                else -> { }
                            }
                        }
                    )

                    // Switch Camera
                    IconButton(
                        onClick = {
                            val newLens = if (cameraLens == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK
                            onCameraLensChange(newLens)
                        },
                        modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(imageVector = Lucide.SwitchCamera, contentDescription = "Switch", tint = Color.White)
                    }
                }
            }
        }

        // 5. Processing Overlay
        if (isProcessing) {
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
                        Text(processingMessage, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text("Applying Smart Intelligence Logic...", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        // 6. Image Preview Overlay (Temporary)
        lastCapturedImage?.let { bitmap ->
            LaunchedEffect(bitmap) {
                delay(3000)
                lastCapturedImage = null
            }
        }
    }
}

@Composable
fun RightControlBar(
    modifier: Modifier = Modifier,
    isStabilizationOn: Boolean,
    isTrackingOn: Boolean,
    onToggleStabilization: () -> Unit,
    onToggleTracking: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onToggleStabilization,
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isStabilizationOn) Color.Yellow else Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Lucide.Activity,
                    contentDescription = "Stabilize",
                    tint = if (isStabilizationOn) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (isStabilizationOn) {
                Text("Steady", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onToggleTracking,
                modifier = Modifier
                    .size(40.dp)
                    .background(if (isTrackingOn) Color.Yellow else Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Lucide.Scan,
                    contentDescription = "Track",
                    tint = if (isTrackingOn) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (isTrackingOn) {
                Text("Track", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TopControlBar(
    modifier: Modifier = Modifier,
    flashMode: FlashMode,
    currentMode: CameraMode,
    aspectRatio: AspectRatio,
    isFHDQuality: Boolean,
    onFlashToggle: () -> Unit,
    onAspectRatioToggle: () -> Unit,
    onQualityToggle: () -> Unit,
    onSettingsClick: () -> Unit
) {
    // Shared styling
    val ratioText = when(aspectRatio) {
        AspectRatio.RATIO_1_1 -> "1:1"
        AspectRatio.RATIO_4_5 -> "4:5"
        AspectRatio.RATIO_3_4 -> "3:4"
        AspectRatio.RATIO_9_16 -> "9:16"
        AspectRatio.RATIO_4_3 -> "3:4"
        AspectRatio.RATIO_16_9 -> "9:16"
    }

    if (currentMode == CameraMode.PHOTO) {
        // Photo Mode: Settings (Left), Aspect Ratio (Center), Flash (Right)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
            // Left
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(imageVector = Lucide.Settings, contentDescription = "Settings", tint = Color.White)
            }

            // Center: Aspect Ratio
            Text(
                text = ratioText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .border(1.dp, Color.White, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onAspectRatioToggle() }
            )

            // Right
            IconButton(
                onClick = onFlashToggle,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
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
    } else {
        // Video Mode: Settings (Left), Quality (Center), Flash (Right)
        // Ratio removed.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
        ) {
             // Left
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(imageVector = Lucide.Settings, contentDescription = "Settings", tint = Color.White)
            }

            // Center: Quality
            val qualityText = if (isFHDQuality) "FHD" else "HD"
            val borderColor = if (isFHDQuality) Color.Yellow else Color.White
            val textColor = if (isFHDQuality) Color.Yellow else Color.White

            Text(
                text = qualityText,
                color = textColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .border(1.dp, borderColor, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onQualityToggle() }
            )

            // Right
            IconButton(
                onClick = onFlashToggle,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
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
    val cornerRadius by animateFloatAsState(if (isRecording) 8f else 50f)
    val color = if (currentMode == CameraMode.VIDEO) Color.Red else Color.White

    Box(
        modifier = Modifier
            .size(90.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .border(4.dp, Color.White, CircleShape)
        )
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
    recognizedText: String?
) {
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
    context: Context,
    cameraController: CameraController,
    imageSaverPlugin: ImageSaverPlugin,
    aspectRatio: AspectRatio,
    onImageCaptured: (ImageBitmap, Uri?) -> Unit
) {
    when (val result = cameraController.takePictureToFile()) {
        is ImageCaptureResult.SuccessWithFile -> {
            try {
                withContext(Dispatchers.IO) {
                    val file = File(result.filePath)
                    val exif = ExifInterface(file.absolutePath)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    val rawW = options.outWidth
                    val rawH = options.outHeight

                    val isRotated = (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270)
                    val visualW = if (isRotated) rawH else rawW
                    val visualH = if (isRotated) rawW else rawH

                    val targetRatioValue = when(aspectRatio) {
                        AspectRatio.RATIO_1_1 -> 1.0f
                        AspectRatio.RATIO_4_5 -> 0.8f
                        AspectRatio.RATIO_3_4, AspectRatio.RATIO_4_3 -> 0.75f
                        AspectRatio.RATIO_9_16, AspectRatio.RATIO_16_9 -> 0.5625f
                    }

                    val currentRatio = visualW.toFloat() / visualH.toFloat()

                    val cropVisualW: Int
                    val cropVisualH: Int

                    if (currentRatio > targetRatioValue) {
                         cropVisualH = visualH
                         cropVisualW = (visualH * targetRatioValue).toInt()
                    } else {
                         cropVisualW = visualW
                         cropVisualH = (visualW / targetRatioValue).toInt()
                    }

                    val cropRawW = if (isRotated) cropVisualH else cropVisualW
                    val cropRawH = if (isRotated) cropVisualW else cropVisualH

                    val cx = (rawW - cropRawW) / 2
                    val cy = (rawH - cropRawH) / 2

                    val cropRect = android.graphics.Rect(cx, cy, cx + cropRawW, cy + cropRawH)

                    val decoder = android.graphics.BitmapRegionDecoder.newInstance(file.absolutePath, false)
                    val croppedRawBitmap = decoder.decodeRegion(cropRect, BitmapFactory.Options())

                    val finalBitmap = if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                        val matrix = Matrix()
                        when (orientation) {
                             ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                             ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                             ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        }
                        val rotated = Bitmap.createBitmap(croppedRawBitmap, 0, 0, croppedRawBitmap.width, croppedRawBitmap.height, matrix, true)
                        if (rotated != croppedRawBitmap) croppedRawBitmap.recycle()
                        rotated
                    } else {
                        croppedRawBitmap
                    }

                    file.outputStream().use { out ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }

                    // Create thumbnail for preview to avoid OOM
                    val previewBmp = try {
                        val maxDim = 1080
                        if (finalBitmap.width > maxDim || finalBitmap.height > maxDim) {
                            val ratio = maxDim.toFloat() / Math.max(finalBitmap.width, finalBitmap.height)
                            val w = (finalBitmap.width * ratio).toInt()
                            val h = (finalBitmap.height * ratio).toInt()
                            val scaled = Bitmap.createScaledBitmap(finalBitmap, w, h, true)
                            if (scaled != finalBitmap) finalBitmap.recycle()
                            scaled.asImageBitmap()
                        } else {
                            finalBitmap.asImageBitmap()
                        }
                    } catch (e: Exception) {
                        finalBitmap.asImageBitmap()
                    }

                    val scanContinuation = suspendCancellableCoroutine<Uri?> { cont ->
                        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg")) { _, uri ->
                            cont.resume(uri)
                        }
                    }
                    val contentUri = scanContinuation

                    withContext(Dispatchers.Main) {
                        onImageCaptured(previewBmp, contentUri)
                    }
                }
            } catch(e: Exception) {
                e.printStackTrace()
            }
        }
        else -> {}
    }
}
