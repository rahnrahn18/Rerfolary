package com.kashif.folar.company.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
// Lucide Icons
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
// Folar Core
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
import com.kashif.folar.compose.FolarScreen
import com.kashif.folar.compose.rememberFolarState
import com.kashif.folar.state.CameraConfiguration
// Plugins
import com.kashif.imagesaverplugin.ImageSaverConfig
import com.kashif.imagesaverplugin.ImageSaverPlugin
import com.kashif.imagesaverplugin.rememberImageSaverPlugin
import com.kashif.qrscannerplugin.QRScannerPlugin
import com.kashif.qrscannerplugin.rememberQRScannerPlugin
import com.kashif.ocrPlugin.OcrPlugin
import com.kashif.ocrPlugin.rememberOcrPlugin
// Utils
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
    var detectedQR by remember { mutableStateOf<com.kashif.qrscannerplugin.QRResult?>(null) }
    var recognizedText by remember { mutableStateOf<String?>(null) }

    // Collect plugin outputs
    LaunchedEffect(qrScannerPlugin) {
        qrScannerPlugin.getQrCodeFlow().collect { qr ->
            detectedQR = qr
            println("QR Code detected: ${qr.text}")
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
        InstagramCameraScreen(
            cameraState = state,
            imageSaverPlugin = imageSaverPlugin,
            qrScannerPlugin = qrScannerPlugin,
            ocrPlugin = ocrPlugin,
            detectedQR = detectedQR, // Now type QRResult?
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