package com.kashif.folar.company.app

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
import androidx.compose.runtime.key
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
// Lucide Icons
import com.composables.icons.lucide.Aperture
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.Flashlight
import com.composables.icons.lucide.FlashlightOff
import com.composables.icons.lucide.Frame
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.SwitchCamera
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
// Folar Core
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
import com.kashif.folar.compose.FolarScreen
import com.kashif.folar.compose.rememberFolarState
import com.kashif.folar.state.CameraConfiguration
import com.kashif.folar.state.FolarState
// Plugins
import com.kashif.imagesaverplugin.ImageSaverConfig
import com.kashif.imagesaverplugin.ImageSaverPlugin
import com.kashif.imagesaverplugin.rememberImageSaverPlugin
import com.kashif.qrscannerplugin.QRScannerPlugin
import com.kashif.qrscannerplugin.rememberQRScannerPlugin
import com.kashif.ocrPlugin.OcrPlugin
import com.kashif.ocrPlugin.rememberOcrPlugin
// Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kashif.folar.company.app.theme.AppTheme

/**
 * Main entry point for the Folar sample application.
 */
@Composable
fun App() = AppTheme {
    val permissions: Permissions = providePermissions()
    val snackbarHostState = remember { SnackbarHostState() }
    // Default ke API baru (Compose)
    var useNewApi by remember { mutableStateOf(true) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
    ) {
        val cameraPermissionState = remember { mutableStateOf(permissions.hasCameraPermission()) }
        val storagePermissionState = remember { mutableStateOf(permissions.hasStoragePermission()) }

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
            if (useNewApi) {
                CameraContent(
                    imageSaverPlugin = imageSaverPlugin,
                    qrScannerPlugin = qrScannerPlugin,
                    ocrPlugin = ocrPlugin,
                    onToggleApi = { useNewApi = !useNewApi }
                )
            } else {
                // Pastikan LegacyAppContent ada atau buat dummy jika belum ada
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Legacy Content Placeholder")
                    FilledTonalButton(onClick = { useNewApi = !useNewApi }) {
                        Text("Switch Back")
                    }
                }
            }
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
}

@Composable
private fun CameraContent(
    imageSaverPlugin: ImageSaverPlugin,
    qrScannerPlugin: QRScannerPlugin,
    ocrPlugin: OcrPlugin,
    onToggleApi: () -> Unit = {}
) {
    var aspectRatio by remember { mutableStateOf(AspectRatio.RATIO_4_3) }
    var resolution by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var imageFormat by remember { mutableStateOf(ImageFormat.JPEG) }
    var qualityPrioritization by remember { mutableStateOf(QualityPrioritization.BALANCED) }
    var cameraDeviceType by remember { mutableStateOf(CameraDeviceType.WIDE_ANGLE) }
    var configVersion by remember { mutableStateOf(0) }

    // Plugin output states
    var detectedQR by remember { mutableStateOf<String?>(null) }
    var recognizedText by remember { mutableStateOf<String?>(null) }

    // Collect plugin outputs
    LaunchedEffect(qrScannerPlugin) {
        qrScannerPlugin.getQrCodeFlow().collect { qr ->
            detectedQR = qr
            println("QR Code detected: $qr")
        }
    }

    LaunchedEffect(ocrPlugin) {
        ocrPlugin.ocrFlow.collect { text ->
            recognizedText = text
            println("Text recognized: $text")
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Initializing Camera...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        errorContent = { error ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "Camera Error",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        error.message ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { state ->
        EnhancedCameraScreen(
            cameraState = state,
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
            onAspectRatioChange = { ratio: AspectRatio ->
                aspectRatio = ratio
                configVersion++
            },
            onResolutionChange = { res: Pair<Int, Int>? ->
                resolution = res
                configVersion++
            },
            onImageFormatChange = { format: ImageFormat ->
                imageFormat = format
                configVersion++
            },
            onQualityPrioritizationChange = { quality: QualityPrioritization ->
                qualityPrioritization = quality
                configVersion++
            },
            onCameraDeviceTypeChange = { device: CameraDeviceType ->
                cameraDeviceType = device
                configVersion++
            },
            onToggleApi = onToggleApi
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedCameraScreen(
    cameraState: FolarState.Ready,
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
    onCameraDeviceTypeChange: (CameraDeviceType) -> Unit,
    onToggleApi: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val cameraController = cameraState.controller
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Camera settings state
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var torchMode by remember { mutableStateOf(TorchMode.OFF) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var apertureLevel by remember { mutableFloatStateOf(0f) }

    // Plugin control states
    var isQRScanningEnabled by remember { mutableStateOf(true) }
    var isOCREnabled by remember { mutableStateOf(true) }

    // Bottom sheet state
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = false
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    LaunchedEffect(cameraController) {
        maxZoom = cameraController.getMaxZoom()
    }

    // Control plugin states with safe initialization delay
    LaunchedEffect(isQRScanningEnabled) {
        try {
            if (isQRScanningEnabled) {
                // ADDED: Delay to allow camera to fully initialize before starting scan
                delay(1000) 
                qrScannerPlugin.startScanning()
            } else {
                qrScannerPlugin.pauseScanning()
            }
        } catch (e: Exception) {
            println("QR Scanner error: ${e.message}")
        }
    }

    LaunchedEffect(isOCREnabled) {
        try {
            if (isOCREnabled) {
                // ADDED: Delay to allow camera to fully initialize before starting recognition
                delay(1000)
                ocrPlugin.startRecognition()
            } else {
                ocrPlugin.stopRecognition()
            }
        } catch (e: Exception) {
            println("OCR error: ${e.message}")
        }
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
                onFlashModeChange = {
                    flashMode = it
                    cameraController.setFlashMode(it)
                },
                onTorchModeChange = {
                    torchMode = it
                    cameraController.setTorchMode(it)
                },
                onZoomChange = {
                    zoomLevel = it
                    cameraController.setZoom(it)
                },
                onApertureChange = {
                    apertureLevel = it
                    cameraController.setAperture(it)
                },
                onLensSwitch = {
                    cameraController.toggleCameraLens()
                    maxZoom = cameraController.getMaxZoom()
                    zoomLevel = 1f
                },
                onAspectRatioChange = { onAspectRatioChange(it) },
                onResolutionChange = { onResolutionChange(it) },
                onImageFormatChange = { onImageFormatChange(it) },
                onQualityPrioritizationChange = { onQualityPrioritizationChange(it) },
                onCameraDeviceTypeChange = { onCameraDeviceTypeChange(it) },
                onQRScanningToggle = { isQRScanningEnabled = it },
                onOCRToggle = { isOCREnabled = it },
                onToggleApi = onToggleApi
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
                onFlashToggle = {
                    cameraController.toggleFlashMode()
                    flashMode = cameraController.getFlashMode() ?: FlashMode.OFF
                },
                onTorchToggle = {
                    cameraController.toggleTorchMode()
                    torchMode = cameraController.getTorchMode() ?: TorchMode.OFF
                }
            )

            CaptureButton(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
                isCapturing = isCapturing,
                onCapture = {
                    if (!isCapturing) {
                        isCapturing = true
                        scope.launch {
                            handleImageCapture(
                                cameraController = cameraController,
                                imageSaverPlugin = imageSaverPlugin,
                                onImageCaptured = { imageBitmap = it }
                            )
                            isCapturing = false
                        }
                    }
                }
            )

            CapturedImagePreview(imageBitmap = imageBitmap) {
                imageBitmap = null
            }
        }
    }
}

// ... QuickControlsOverlay and CaptureButton (unchanged) ...
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
private fun CaptureButton(
    modifier: Modifier = Modifier,
    isCapturing: Boolean,
    onCapture: () -> Unit
) {
    FilledTonalButton(
        onClick = onCapture,
        enabled = !isCapturing,
        modifier = modifier.size(80.dp).clip(CircleShape),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    ) {
        Icon(
            imageVector = Lucide.Camera,
            contentDescription = "Capture",
            tint = if (isCapturing) Color.White.copy(alpha = 0.5f) else Color.White,
            modifier = Modifier.size(32.dp)
        )
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
    onOCRToggle: (Boolean) -> Unit,
    onToggleApi: () -> Unit = {}
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Camera Controls",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF221B00)
                )
                
                FilledTonalButton(
                    onClick = onToggleApi,
                    modifier = Modifier.widthIn(min = 100.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFFD4A574),
                        contentColor = Color(0xFF221B00)
                    )
                ) {
                    Text(
                        "Switch API",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Zoom Control
        if (maxZoom > 1f) {
            item(span = { GridItemSpan(2) }) {
                ZoomControl(
                    zoomLevel = zoomLevel,
                    maxZoom = maxZoom,
                    onZoomChange = onZoomChange
                )
            }
        }

        // Aperture Control
        item(span = { GridItemSpan(3) }) {
            ApertureControl(
                apertureLevel = apertureLevel,
                onApertureChange = onApertureChange
            )
        }

        // Controls with FIX for "IndicationNodeFactory" crash
        // Using explicit interactionSource and indication = null
        item { FlashModeControl(flashMode = flashMode, onFlashModeChange = onFlashModeChange) }
        item { TorchModeControl(torchMode = torchMode, onTorchModeChange = onTorchModeChange) }
        item { AspectRatioControl(aspectRatio = aspectRatio, onAspectRatioChange = onAspectRatioChange) }
        item { ResolutionControl(resolution = resolution, onResolutionChange = onResolutionChange) }
        item { ImageFormatControl(imageFormat = imageFormat, onImageFormatChange = onImageFormatChange) }
        item { QualityPrioritizationControl(qualityPrioritization = qualityPrioritization, onQualityPrioritizationChange = onQualityPrioritizationChange) }
        item { CameraDeviceTypeControl(cameraDeviceType = cameraDeviceType, onCameraDeviceTypeChange = onCameraDeviceTypeChange) }
        item { CameraLensControl(onLensSwitch = onLensSwitch) }

        // Plugin Controls Section
        item(span = { GridItemSpan(3) }) {
            Text(
                text = "Active Plugins",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF221B00),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        item(span = { GridItemSpan(3) }) {
            PluginToggleControl(
                label = "QR Scanner",
                isEnabled = isQRScanningEnabled,
                onToggle = onQRScanningToggle
            )
        }

        item(span = { GridItemSpan(3) }) {
            PluginToggleControl(
                label = "OCR (Text Recognition)",
                isEnabled = isOCREnabled,
                onToggle = onOCRToggle
            )
        }
    }
}

// ... Component helpers with CRASH FIX applied ...

@Composable
private fun ZoomControl(
    zoomLevel: Float,
    maxZoom: Float,
    onZoomChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Zoom", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = "Zoom: ${(zoomLevel * 10).toInt() / 10f}x", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = zoomLevel,
            onValueChange = onZoomChange,
            valueRange = 1f..maxZoom,
            steps = ((maxZoom - 1f) * 10).toInt().coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun ApertureControl(
    apertureLevel: Float,
    onApertureChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Lucide.Aperture, contentDescription = "Aperture", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(text = "Aperture (Depth of Field)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${(apertureLevel * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = apertureLevel, onValueChange = onApertureChange, valueRange = 0f..1f, steps = 20, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary))
    }
}

@Composable
private fun FlashModeControl(flashMode: FlashMode, onFlashModeChange: (FlashMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // FIX: Disable defective ripple
                onClick = { expanded = true }
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = when (flashMode) { FlashMode.ON -> Lucide.Flashlight; FlashMode.OFF -> Lucide.FlashlightOff; FlashMode.AUTO -> Lucide.Flashlight }, contentDescription = "Flash Mode", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text = flashMode.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color(0xFF221B00))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FlashMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.name) }, onClick = { onFlashModeChange(mode); expanded = false })
            }
        }
    }
}

@Composable
private fun TorchModeControl(torchMode: TorchMode, onTorchModeChange: (TorchMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // FIX: Disable defective ripple
                onClick = { expanded = true }
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = if (torchMode != TorchMode.OFF) Lucide.Flashlight else Lucide.FlashlightOff, contentDescription = "Torch Mode", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text = torchMode.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color(0xFF221B00))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TorchMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(mode.name) }, onClick = { onTorchModeChange(mode); expanded = false })
            }
        }
    }
}

@Composable
private fun AspectRatioControl(aspectRatio: AspectRatio, onAspectRatioChange: (AspectRatio) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // FIX: Disable defective ripple
                onClick = { expanded = true }
            )
            .padding(8.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Lucide.Crop, contentDescription = "Aspect Ratio", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text = aspectRatio.name.replace("RATIO_", "").replace("_", ":"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color(0xFF221B00))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AspectRatio.entries.forEach { ratio -> DropdownMenuItem(text = { Text(ratio.name.replace("RATIO_", "").replace("_", ":")) }, onClick = { onAspectRatioChange(ratio); expanded = false }) }
        }
    }
}

@Composable
private fun ResolutionControl(resolution: Pair<Int, Int>?, onResolutionChange: (Pair<Int, Int>?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(null, 1920 to 1080, 1280 to 720, 640 to 480)
    fun label(pair: Pair<Int, Int>?): String = pair?.let { "${it.first}x${it.second}" } ?: "Auto"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // FIX: Disable defective ripple
                onClick = { expanded = true }
            )
            .padding(8.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Lucide.Frame, contentDescription = "Resolution", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text = label(resolution), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color(0xFF221B00))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = { onResolutionChange(option); expanded = false }) }
        }
    }
}

@Composable
private fun ImageFormatControl(imageFormat: ImageFormat, onImageFormatChange: (ImageFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // FIX: Disable defective ripple
                onClick = { expanded = true }
            )
            .padding(8.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Lucide.Image, contentDescription = "Image Format", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text = imageFormat.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color(0xFF221B00))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ImageFormat.entries.forEach { format -> DropdownMenuItem(text = { Text(format.name) }, onClick = { onImageFormatChange(format); expanded = false }) }
        }
    }
}

@Composable
private fun QualityPrioritizationControl(qualityPrioritization: QualityPrioritization, onQualityPrioritizationChange: (QualityPrioritization) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // FIX: Disable defective ripple
                onClick = { expanded = true }
            )
            .padding(8.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Lucide.Zap, contentDescription = "Quality Priority", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text = qualityPrioritization.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color(0xFF221B00))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QualityPrioritization.entries.forEach { priority -> DropdownMenuItem(text = { Text(priority.name) }, onClick = { onQualityPrioritizationChange(priority); expanded = false }) }
        }
    }
}

@Composable
private fun CameraDeviceTypeControl(cameraDeviceType: CameraDeviceType, onCameraDeviceTypeChange: (CameraDeviceType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // FIX: Disable defective ripple
                onClick = { expanded = true }
            )
            .padding(8.dp), 
        horizontalAlignment = Alignment.CenterHorizontally, 
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Lucide.SwitchCamera, contentDescription = "Camera Type", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text = cameraDeviceType.name.replace("_", " "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color(0xFF221B00))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CameraDeviceType.entries.forEach { type -> DropdownMenuItem(text = { Text(type.name.replace("_", " ")) }, onClick = { onCameraDeviceTypeChange(type); expanded = false }) }
        }
    }
}

@Composable
private fun CameraLensControl(onLensSwitch: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // FIX: Disable defective ripple
                onClick = onLensSwitch
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = Lucide.SwitchCamera, contentDescription = "Switch Camera", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(text = "Switch Camera", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = Color(0xFF221B00))
    }
}


@Composable
private fun CapturedImagePreview(imageBitmap: ImageBitmap?, onDismiss: () -> Unit) {
    imageBitmap?.let { bitmap ->
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.9f)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(bitmap = bitmap, contentDescription = "Captured Image", modifier = Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit)
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), CircleShape)) {
                    Icon(imageVector = Lucide.X, contentDescription = "Close Preview", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.rotate(120f))
                }
            }
        }
        LaunchedEffect(bitmap) {
            delay(3000)
            onDismiss()
        }
    }
}

private suspend fun handleImageCapture(
    cameraController: CameraController,
    imageSaverPlugin: ImageSaverPlugin,
    onImageCaptured: (ImageBitmap) -> Unit
) {
    when (val result = cameraController.takePictureToFile()) {
        is ImageCaptureResult.SuccessWithFile -> {
            println("Image captured and saved at: ${result.filePath}")
        }
        is ImageCaptureResult.Success -> println("Image captured successfully (${result.byteArray.size} bytes)")
        is ImageCaptureResult.Error -> println("Image Capture Error: ${result.exception.message}")
    }
}

@Composable
private fun PluginOutputsOverlay(modifier: Modifier = Modifier, detectedQR: String?, recognizedText: String?, isQREnabled: Boolean, isOCREnabled: Boolean) {
    if ((isQREnabled && detectedQR != null) || (isOCREnabled && recognizedText != null)) {
        Surface(modifier = modifier.padding(16.dp), color = Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Plugin Outputs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                if (isQREnabled && detectedQR != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("QR Code:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(detectedQR, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                    }
                }
                if (isOCREnabled && recognizedText != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recognized Text:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        Text(recognizedText.take(100) + if (recognizedText.length > 100) "..." else "", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2196F3))
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginToggleControl(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF221B00))
            Switch(checked = isEnabled, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50), checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f), uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)))
        }
    }
}

@Composable
private fun OcrOutputOverlay(modifier: Modifier = Modifier, recognizedText: String?, isOCREnabled: Boolean) {
    if (isOCREnabled && recognizedText != null) {
        Surface(modifier = modifier.padding(16.dp).border(2.dp, Color(0xFF2196F3), RoundedCornerShape(12.dp)), color = Color(0xFF1F1F1F), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(12.dp).widthIn(max = 250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📝 Recognized Text", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                Text(recognizedText, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Text("Length: ${recognizedText.length} chars", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
