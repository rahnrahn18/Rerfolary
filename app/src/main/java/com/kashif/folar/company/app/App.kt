package com.kashif.folar.company.app

import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.composables.icons.lucide.Aperture
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.Flashlight
import com.composables.icons.lucide.FlashlightOff
import com.composables.icons.lucide.Frame
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SwitchCamera
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
import com.kashif.folar.company.app.theme.AppTheme
import com.kashif.folar.compose.FolarScreen
import com.kashif.folar.compose.rememberFolarState
import com.kashif.folar.controller.CameraController
import com.kashif.folar.enums.AspectRatio
import com.kashif.folar.enums.CameraDeviceType
import com.kashif.folar.enums.CameraLens
import com.kashif.folar.enums.Directory
import com.kashif.folar.enums.FlashMode
import com.kashif.folar.enums.ImageFormat
import com.kashif.folar.enums.QualityPrioritization
import com.kashif.folar.enums.TorchMode
import com.kashif.folar.permissions.Permissions
import com.kashif.folar.permissions.providePermissions
import com.kashif.folar.result.ImageCaptureResult
import com.kashif.folar.state.CameraConfiguration
import com.kashif.folar.state.FolarState
import com.kashif.folar.utils.NativeBridge
import com.kashif.imagesaverplugin.ImageSaverConfig
import com.kashif.imagesaverplugin.ImageSaverPlugin
import com.kashif.imagesaverplugin.rememberImageSaverPlugin
import com.kashif.ocrPlugin.OcrPlugin
import com.kashif.ocrPlugin.rememberOcrPlugin
import com.kashif.qrscannerplugin.QRScannerPlugin
import com.kashif.qrscannerplugin.rememberQRScannerPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

enum class CameraMode {
    PHOTO, VIDEO
}

@Composable
fun App() = AppTheme {
    val permissions: Permissions = providePermissions()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val cameraPermissionState = remember { mutableStateOf(permissions.hasCameraPermission()) }
        val storagePermissionState = remember { mutableStateOf(permissions.hasStoragePermission()) }
        val audioPermissionState = remember { mutableStateOf(permissions.hasAudioPermission()) } // Assuming Permissions class has this, otherwise ignore or add

        // Create all plugin instances
        val imageSaverPlugin = rememberImageSaverPlugin(
            config = ImageSaverConfig(
                isAutoSave = true,
                prefix = "Folar",
                directory = Directory.PICTURES,
                customFolderName = "Folar"
            )
        )
        val qrScannerPlugin = rememberQRScannerPlugin()
        val ocrPlugin = rememberOcrPlugin()

        PermissionsHandler(
            permissions = permissions,
            cameraPermissionState = cameraPermissionState,
            storagePermissionState = storagePermissionState
        )

        if (cameraPermissionState.value && storagePermissionState.value) {
            CameraContent(
                imageSaverPlugin = imageSaverPlugin,
                qrScannerPlugin = qrScannerPlugin,
                ocrPlugin = ocrPlugin
            )
        }
    }
}

@Composable
private fun PermissionsHandler(
    permissions: Permissions,
    cameraPermissionState: MutableState<Boolean>,
    storagePermissionState: MutableState<Boolean>
) {
    if (!cameraPermissionState.value) {
        permissions.RequestCameraPermission(
            onGranted = { cameraPermissionState.value = true },
            onDenied = { println("Camera Permission Denied") }
        )
    }

    if (!storagePermissionState.value) {
        permissions.RequestStoragePermission(
            onGranted = { storagePermissionState.value = true },
            onDenied = { println("Storage Permission Denied") }
        )
    }

    // Request Audio Permission silently or when switching to video (omitted for brevity in this block, assumed handled by CameraController check)
}

@Composable
private fun CameraContent(
    imageSaverPlugin: ImageSaverPlugin,
    qrScannerPlugin: QRScannerPlugin,
    ocrPlugin: OcrPlugin
) {
    var aspectRatio by remember { mutableStateOf(AspectRatio.RATIO_4_3) }
    var resolution by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var imageFormat by remember { mutableStateOf(ImageFormat.JPEG) }
    var qualityPrioritization by remember { mutableStateOf(QualityPrioritization.BALANCED) }
    var cameraDeviceType by remember { mutableStateOf(CameraDeviceType.WIDE_ANGLE) }
    var configVersion by remember { mutableStateOf(0) }

    var cameraMode by remember { mutableStateOf(CameraMode.PHOTO) }

    // Plugin output states
    var detectedQR by remember { mutableStateOf<String?>(null) }
    var recognizedText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(qrScannerPlugin) {
        qrScannerPlugin.getQrCodeFlow().collect { qr ->
            detectedQR = qr
        }
    }

    LaunchedEffect(ocrPlugin) {
        ocrPlugin.ocrFlow.collect { text ->
            recognizedText = text
        }
    }

    val cameraState by rememberFolarState(
        config = CameraConfiguration(
            cameraLens = CameraLens.BACK,
            flashMode = FlashMode.OFF,
            imageFormat = imageFormat,
            directory = Directory.PICTURES,
            torchMode = TorchMode.OFF,
            qualityPrioritization = qualityPrioritization,
            cameraDeviceType = cameraDeviceType,
            aspectRatio = aspectRatio,
            targetResolution = resolution
        ),
        setupPlugins = { stateHolder ->
            stateHolder.attachPlugin(imageSaverPlugin)
            stateHolder.attachPlugin(qrScannerPlugin)
            stateHolder.attachPlugin(ocrPlugin)
        }
    )

    FolarScreen(
        cameraState = cameraState,
        showPreview = true,
        loadingContent = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Initializing...", color = Color.White)
            }
        },
        errorContent = { error ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${error.message}", color = Color.Red)
            }
        }
    ) { state ->
        EnhancedCameraScreen(
            cameraState = state,
            cameraMode = cameraMode,
            onCameraModeChange = { cameraMode = it },
            imageSaverPlugin = imageSaverPlugin,
            qrScannerPlugin = qrScannerPlugin,
            ocrPlugin = ocrPlugin,
            detectedQR = detectedQR,
            recognizedText = recognizedText,
            aspectRatio = aspectRatio,
            resolution = resolution,
            imageFormat = imageFormat,
            qualityPrioritization = qualityPrioritization,
            cameraDeviceType = cameraDeviceType,
            onAspectRatioChange = { aspectRatio = it; configVersion++ },
            onResolutionChange = { resolution = it; configVersion++ },
            onImageFormatChange = { imageFormat = it; configVersion++ },
            onQualityPrioritizationChange = { qualityPrioritization = it; configVersion++ },
            onCameraDeviceTypeChange = { cameraDeviceType = it; configVersion++ }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedCameraScreen(
    cameraState: FolarState.Ready,
    cameraMode: CameraMode,
    onCameraModeChange: (CameraMode) -> Unit,
    imageSaverPlugin: ImageSaverPlugin,
    qrScannerPlugin: QRScannerPlugin,
    ocrPlugin: OcrPlugin,
    detectedQR: String?,
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
    onCameraDeviceTypeChange: (CameraDeviceType) -> Unit
) {
    val scope = rememberCoroutineScope()
    val cameraController = cameraState.controller

    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var videoFile by remember { mutableStateOf<File?>(null) }
    var stabilizedFile by remember { mutableStateOf<File?>(null) }

    var isCapturing by remember { mutableStateOf(false) } // Taking photo or starting/stopping video
    var isRecording by remember { mutableStateOf(false) } // Currently recording video
    var isStabilizing by remember { mutableStateOf(false) }

    // Camera settings state
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var torchMode by remember { mutableStateOf(TorchMode.OFF) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var apertureLevel by remember { mutableFloatStateOf(0f) }

    // Plugin control states
    var isQRScanningEnabled by remember { mutableStateOf(true) }
    var isOCREnabled by remember { mutableStateOf(true) }

    val bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    LaunchedEffect(cameraController) {
        maxZoom = cameraController.getMaxZoom()
    }

    LaunchedEffect(isQRScanningEnabled) {
        delay(1000)
        if (isQRScanningEnabled) qrScannerPlugin.startScanning() else qrScannerPlugin.pauseScanning()
    }

    LaunchedEffect(isOCREnabled) {
        delay(1000)
        if (isOCREnabled) ocrPlugin.startRecognition() else ocrPlugin.stopRecognition()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 80.dp,
        containerColor = Color.Transparent,
        sheetContainerColor = Color(0xFFF7F2E9),
        sheetContent = {
            CameraControlsBottomSheet(
                flashMode = flashMode,
                torchMode = torchMode,
                zoomLevel = zoomLevel,
                maxZoom = maxZoom,
                apertureLevel = apertureLevel,
                aspectRatio = aspectRatio,
                resolution = resolution,
                imageFormat = imageFormat,
                qualityPrioritization = qualityPrioritization,
                cameraDeviceType = cameraDeviceType,
                isQRScanningEnabled = isQRScanningEnabled,
                isOCREnabled = isOCREnabled,
                onFlashModeChange = { flashMode = it; cameraController.setFlashMode(it) },
                onTorchModeChange = { torchMode = it; cameraController.setTorchMode(it) },
                onZoomChange = { zoomLevel = it; cameraController.setZoom(it) },
                onApertureChange = { apertureLevel = it; cameraController.setAperture(it) },
                onLensSwitch = { cameraController.toggleCameraLens(); maxZoom = cameraController.getMaxZoom(); zoomLevel = 1f },
                onAspectRatioChange = onAspectRatioChange,
                onResolutionChange = onResolutionChange,
                onImageFormatChange = onImageFormatChange,
                onQualityPrioritizationChange = onQualityPrioritizationChange,
                onCameraDeviceTypeChange = onCameraDeviceTypeChange,
                onQRScanningToggle = { isQRScanningEnabled = it },
                onOCRToggle = { isOCREnabled = it }
            )
        },
        sheetContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PluginOutputsOverlay(
                modifier = Modifier.align(Alignment.TopStart),
                detectedQR = detectedQR,
                recognizedText = recognizedText,
                isQREnabled = isQRScanningEnabled,
                isOCREnabled = isOCREnabled
            )

            OcrOutputOverlay(
                modifier = Modifier.align(Alignment.BottomStart),
                recognizedText = recognizedText,
                isOCREnabled = isOCREnabled
            )

            QuickControlsOverlay(
                modifier = Modifier.align(Alignment.TopEnd),
                flashMode = flashMode,
                torchMode = torchMode,
                onFlashToggle = { cameraController.toggleFlashMode(); flashMode = cameraController.getFlashMode() ?: FlashMode.OFF },
                onTorchToggle = { cameraController.toggleTorchMode(); torchMode = cameraController.getTorchMode() ?: TorchMode.OFF }
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModeSelector(
                    currentMode = cameraMode,
                    onModeSelected = {
                        if (!isRecording) onCameraModeChange(it)
                    }
                )

                CaptureButton(
                    cameraMode = cameraMode,
                    isCapturing = isCapturing,
                    isRecording = isRecording,
                    onCapture = {
                        if (cameraMode == CameraMode.PHOTO) {
                            if (!isCapturing) {
                                isCapturing = true
                                scope.launch {
                                    handleImageCapture(cameraController, imageSaverPlugin) { imageBitmap = it }
                                    isCapturing = false
                                }
                            }
                        } else {
                            // Video Mode
                            if (isRecording) {
                                // Stop Recording
                                isCapturing = true // processing stop
                                cameraController.stopRecording()
                                // The callback in startRecording will handle the rest
                            } else {
                                // Start Recording
                                isRecording = true
                                cameraController.startRecording(
                                    onVideoSaved = { file ->
                                        isRecording = false
                                        isCapturing = false
                                        videoFile = file
                                        stabilizedFile = null
                                    },
                                    onError = { error ->
                                        println("Recording Error: $error")
                                        isRecording = false
                                        isCapturing = false
                                    }
                                )
                            }
                        }
                    }
                )
            }

            CapturedImagePreview(imageBitmap = imageBitmap) { imageBitmap = null }

            CapturedVideoPreview(
                videoFile = videoFile,
                stabilizedFile = stabilizedFile,
                isStabilizing = isStabilizing,
                onStabilize = {
                    videoFile?.let { file ->
                        isStabilizing = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val outputFile = File(file.parent, "STAB_${file.name}")
                                NativeBridge.stabilizeVideo(file.absolutePath, outputFile.absolutePath)
                                withContext(Dispatchers.Main) {
                                    stabilizedFile = outputFile
                                    isStabilizing = false
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    isStabilizing = false
                                }
                            }
                        }
                    }
                },
                onDismiss = {
                    videoFile = null
                    stabilizedFile = null
                }
            )
        }
    }
}

@Composable
private fun ModeSelector(
    currentMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit
) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(20.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CameraMode.values().forEach { mode ->
            val isSelected = mode == currentMode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(onClick = { onModeSelected(mode) })
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = mode.name,
                    color = if (isSelected) Color.White else Color.White.copy(alpha=0.6f),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun CapturedVideoPreview(
    videoFile: File?,
    stabilizedFile: File?,
    isStabilizing: Boolean,
    onStabilize: () -> Unit,
    onDismiss: () -> Unit
) {
    if (videoFile == null) return

    val displayFile = stabilizedFile ?: videoFile

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
             AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoPath(displayFile!!.absolutePath)
                        setOnCompletionListener { start() }
                        start()
                    }
                },
                update = { view ->
                    if (!view.isPlaying && !isStabilizing) {
                        view.setVideoPath(displayFile!!.absolutePath)
                        view.start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                 if (isStabilizing) {
                     CircularProgressIndicator(color = Color.White)
                     Text("Stabilizing Video (OpenCV)...", color = Color.White)
                 } else {
                     if (stabilizedFile == null) {
                         Text("Review Video", color = Color.White, style = MaterialTheme.typography.titleMedium)
                         FilledTonalButton(onClick = onStabilize) {
                             Icon(Lucide.Crop, contentDescription = null)
                             Spacer(Modifier.width(8.dp))
                             Text("Stabilize (OpenCV)")
                         }
                     } else {
                         Text("Stabilization Complete!", color = Color.Green, style = MaterialTheme.typography.titleMedium)
                         Text("Saved to: ${stabilizedFile.name}", color = Color.White.copy(alpha=0.7f), style = MaterialTheme.typography.bodySmall)
                     }

                     FilledTonalButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.error)
                     ) {
                         Text("Close")
                     }
                 }
            }
        }
    }
}

// ... QuickControlsOverlay (same) ...

@Composable
private fun CaptureButton(
    modifier: Modifier = Modifier,
    cameraMode: CameraMode,
    isCapturing: Boolean,
    isRecording: Boolean,
    onCapture: () -> Unit
) {
    FilledTonalButton(
        onClick = onCapture,
        enabled = !(cameraMode == CameraMode.PHOTO && isCapturing), // Disable only if processing photo
        modifier = modifier.size(80.dp).clip(CircleShape),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = if (isRecording) Lucide.Square else (if (cameraMode == CameraMode.VIDEO) Lucide.Video else Lucide.Camera),
            contentDescription = "Capture",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ... Other helpers (same as before) ...
@Composable
private fun QuickControlsOverlay(
    modifier: Modifier = Modifier,
    flashMode: FlashMode,
    torchMode: TorchMode,
    onFlashToggle: () -> Unit,
    onTorchToggle: () -> Unit
) {
    Surface(
        modifier = modifier.padding(16.dp),
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onFlashToggle) {
                Icon(
                    imageVector = when (flashMode) {
                        FlashMode.ON -> Lucide.Flashlight
                        FlashMode.OFF -> Lucide.FlashlightOff
                        FlashMode.AUTO -> Lucide.Flashlight
                    },
                    contentDescription = "Flash: $flashMode",
                    tint = Color.White
                )
            }
            IconButton(onClick = onTorchToggle) {
                Icon(
                    imageVector = if (torchMode != TorchMode.OFF) Lucide.Flashlight else Lucide.FlashlightOff,
                    contentDescription = "Torch: $torchMode",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun CameraControlsBottomSheet(
    flashMode: FlashMode,
    torchMode: TorchMode,
    zoomLevel: Float,
    maxZoom: Float,
    apertureLevel: Float,
    aspectRatio: AspectRatio,
    resolution: Pair<Int, Int>?,
    imageFormat: ImageFormat,
    qualityPrioritization: QualityPrioritization,
    cameraDeviceType: CameraDeviceType,
    isQRScanningEnabled: Boolean,
    isOCREnabled: Boolean,
    onFlashModeChange: (FlashMode) -> Unit,
    onTorchModeChange: (TorchMode) -> Unit,
    onZoomChange: (Float) -> Unit,
    onApertureChange: (Float) -> Unit,
    onLensSwitch: () -> Unit,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onResolutionChange: (Pair<Int, Int>?) -> Unit,
    onImageFormatChange: (ImageFormat) -> Unit,
    onQualityPrioritizationChange: (QualityPrioritization) -> Unit,
    onCameraDeviceTypeChange: (CameraDeviceType) -> Unit,
    onQRScanningToggle: (Boolean) -> Unit,
    onOCRToggle: (Boolean) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(3) }) {
            Text(
                text = "Camera Controls",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF221B00),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (maxZoom > 1f) {
            item(span = { GridItemSpan(2) }) {
                ZoomControl(zoomLevel, maxZoom, onZoomChange)
            }
        }

        item(span = { GridItemSpan(3) }) {
            ApertureControl(apertureLevel, onApertureChange)
        }

        item { FlashModeControl(flashMode, onFlashModeChange) }
        item { TorchModeControl(torchMode, onTorchModeChange) }
        item { AspectRatioControl(aspectRatio, onAspectRatioChange) }
        item { ResolutionControl(resolution, onResolutionChange) }
        item { ImageFormatControl(imageFormat, onImageFormatChange) }
        item { QualityPrioritizationControl(qualityPrioritization, onQualityPrioritizationChange) }
        item { CameraDeviceTypeControl(cameraDeviceType, onCameraDeviceTypeChange) }
        item { CameraLensControl(onLensSwitch) }

        item(span = { GridItemSpan(3) }) {
            Text("Active Plugins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF221B00), modifier = Modifier.padding(top=16.dp, bottom=8.dp))
        }

        item(span = { GridItemSpan(3) }) {
            PluginToggleControl("QR Scanner", isQRScanningEnabled, onQRScanningToggle)
        }

        item(span = { GridItemSpan(3) }) {
            PluginToggleControl("OCR (Text Recognition)", isOCREnabled, onOCRToggle)
        }
    }
}

// ... Re-use the small control components (FlashModeControl, etc) ...
// Since I'm rewriting the file, I must include them.

@Composable
private fun ZoomControl(zoomLevel: Float, maxZoom: Float, onZoomChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Zoom", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("Zoom: ${(zoomLevel * 10).toInt() / 10f}x", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(zoomLevel, onZoomChange, valueRange = 1f..maxZoom, steps = ((maxZoom - 1f) * 10).toInt().coerceAtLeast(0), colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun ApertureControl(apertureLevel: Float, onApertureChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Aperture, "Aperture", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text("Aperture (Depth of Field)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text("${(apertureLevel * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(apertureLevel, onApertureChange, valueRange = 0f..1f, steps = 20, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun FlashModeControl(flashMode: FlashMode, onFlashModeChange: (FlashMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = { expanded = true }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(when (flashMode) { FlashMode.ON -> Lucide.Flashlight; FlashMode.OFF -> Lucide.FlashlightOff; FlashMode.AUTO -> Lucide.Flashlight }, "Flash", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(flashMode.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF221B00))
        DropdownMenu(expanded, { expanded = false }) {
            FlashMode.entries.forEach { mode -> DropdownMenuItem({ Text(mode.name) }, { onFlashModeChange(mode); expanded = false }) }
        }
    }
}

@Composable
private fun TorchModeControl(torchMode: TorchMode, onTorchModeChange: (TorchMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = { expanded = true }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(if (torchMode != TorchMode.OFF) Lucide.Flashlight else Lucide.FlashlightOff, "Torch", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(torchMode.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF221B00))
        DropdownMenu(expanded, { expanded = false }) {
            TorchMode.entries.forEach { mode -> DropdownMenuItem({ Text(mode.name) }, { onTorchModeChange(mode); expanded = false }) }
        }
    }
}

@Composable
private fun AspectRatioControl(aspectRatio: AspectRatio, onAspectRatioChange: (AspectRatio) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = { expanded = true }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Lucide.Crop, "Aspect Ratio", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(aspectRatio.name.replace("RATIO_", "").replace("_", ":"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF221B00))
        DropdownMenu(expanded, { expanded = false }) {
            AspectRatio.entries.forEach { ratio -> DropdownMenuItem({ Text(ratio.name.replace("RATIO_", "").replace("_", ":")) }, { onAspectRatioChange(ratio); expanded = false }) }
        }
    }
}

@Composable
private fun ResolutionControl(resolution: Pair<Int, Int>?, onResolutionChange: (Pair<Int, Int>?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(null, 1920 to 1080, 1280 to 720, 640 to 480)
    fun label(pair: Pair<Int, Int>?): String = pair?.let { "${it.first}x${it.second}" } ?: "Auto"
    Column(
        Modifier.fillMaxWidth().clickable(onClick = { expanded = true }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Lucide.Frame, "Resolution", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(label(resolution), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF221B00))
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem({ Text(label(option)) }, { onResolutionChange(option); expanded = false }) }
        }
    }
}

@Composable
private fun ImageFormatControl(imageFormat: ImageFormat, onImageFormatChange: (ImageFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = { expanded = true }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Lucide.Image, "Format", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(imageFormat.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF221B00))
        DropdownMenu(expanded, { expanded = false }) {
            ImageFormat.entries.forEach { format -> DropdownMenuItem({ Text(format.name) }, { onImageFormatChange(format); expanded = false }) }
        }
    }
}

@Composable
private fun QualityPrioritizationControl(qualityPrioritization: QualityPrioritization, onQualityPrioritizationChange: (QualityPrioritization) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = { expanded = true }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Lucide.Zap, "Quality", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(qualityPrioritization.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF221B00))
        DropdownMenu(expanded, { expanded = false }) {
            QualityPrioritization.entries.forEach { priority -> DropdownMenuItem({ Text(priority.name) }, { onQualityPrioritizationChange(priority); expanded = false }) }
        }
    }
}

@Composable
private fun CameraDeviceTypeControl(cameraDeviceType: CameraDeviceType, onCameraDeviceTypeChange: (CameraDeviceType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = { expanded = true }).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Lucide.SwitchCamera, "Type", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(cameraDeviceType.name.replace("_", " "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF221B00))
        DropdownMenu(expanded, { expanded = false }) {
            CameraDeviceType.entries.forEach { type -> DropdownMenuItem({ Text(type.name.replace("_", " ")) }, { onCameraDeviceTypeChange(type); expanded = false }) }
        }
    }
}

@Composable
private fun CameraLensControl(onLensSwitch: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onLensSwitch).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Lucide.SwitchCamera, "Switch", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text("Switch Camera", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF221B00))
    }
}

@Composable
private fun CapturedImagePreview(imageBitmap: ImageBitmap?, onDismiss: () -> Unit) {
    imageBitmap?.let { bitmap ->
        Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.9f)) {
            Box(Modifier.fillMaxSize()) {
                Image(bitmap, "Captured", Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit)
                IconButton(onDismiss, Modifier.align(Alignment.TopEnd).padding(16.dp).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), CircleShape)) {
                    Icon(Lucide.X, "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.rotate(120f))
                }
            }
        }
        LaunchedEffect(bitmap) { delay(3000); onDismiss() }
    }
}

private suspend fun handleImageCapture(cameraController: CameraController, imageSaverPlugin: ImageSaverPlugin, onImageCaptured: (ImageBitmap) -> Unit) {
    when (val result = cameraController.takePictureToFile()) {
        is ImageCaptureResult.SuccessWithFile -> println("Image saved: ${result.filePath}")
        is ImageCaptureResult.Success -> println("Image captured")
        is ImageCaptureResult.Error -> println("Error: ${result.exception.message}")
    }
}

@Composable
private fun PluginOutputsOverlay(modifier: Modifier = Modifier, detectedQR: String?, recognizedText: String?, isQREnabled: Boolean, isOCREnabled: Boolean) {
    if ((isQREnabled && detectedQR != null) || (isOCREnabled && recognizedText != null)) {
        Surface(modifier.padding(16.dp), color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plugin Outputs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                if (isQREnabled && detectedQR != null) {
                    Column { Text("QR:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f)); Text(detectedQR, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50)) }
                }
                if (isOCREnabled && recognizedText != null) {
                    Column { Text("Text:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f)); Text(recognizedText.take(100), style = MaterialTheme.typography.bodySmall, color = Color(0xFF2196F3)) }
                }
            }
        }
    }
}

@Composable
private fun PluginToggleControl(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF221B00))
            Switch(isEnabled, onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50), checkedTrackColor = Color(0xFF4CAF50).copy(0.5f)))
        }
    }
}

@Composable
private fun OcrOutputOverlay(modifier: Modifier = Modifier, recognizedText: String?, isOCREnabled: Boolean) {
    if (isOCREnabled && recognizedText != null) {
        Surface(modifier.padding(16.dp).border(2.dp, Color(0xFF2196F3), RoundedCornerShape(12.dp)), color = Color(0xFF1F1F1F), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(12.dp).widthIn(max = 250.dp)) {
                Text("📝 Text", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                Text(recognizedText, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
