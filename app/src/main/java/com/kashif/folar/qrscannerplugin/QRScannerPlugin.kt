package com.kashif.qrscannerplugin

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultPoint
import com.google.zxing.common.HybridBinarizer
import androidx.camera.core.ExperimentalGetImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.kashif.folar.controller.CameraController
import com.kashif.folar.plugins.CameraPlugin
import com.kashif.folar.state.FolarEvent
import com.kashif.folar.state.FolarPlugin
import com.kashif.folar.state.FolarState
import com.kashif.folar.state.FolarStateHolder
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.EnumMap

data class QRResult(
    val text: String,
    val points: List<Pair<Float, Float>> // Normalized 0..1 coordinates
)

@Stable
class QRScannerPlugin(
    private val coroutineScope: CoroutineScope
) : CameraPlugin, FolarPlugin {
    private var cameraController: CameraController? = null
    private var stateHolder: FolarStateHolder? = null
    private val qrCodeFlow = MutableSharedFlow<QRResult>() // Changed to QRResult
    private var isScanning = atomic(false)
    private var collectorJob: Job? = null

    override fun initialize(cameraController: CameraController) {
        println("QRScannerPlugin initialized (legacy API)")
        this.cameraController = cameraController
    }

    override fun onAttach(stateHolder: FolarStateHolder) {
        println("QRScannerPlugin attached (new API)")
        this.stateHolder = stateHolder

        collectorJob = stateHolder.pluginScope.launch {
            stateHolder.cameraState
                .filterIsInstance<FolarState.Ready>()
                .collect { readyState ->
                    this@QRScannerPlugin.cameraController = readyState.controller
                    startScanning()
                }
        }
    }

    override fun onDetach() {
        println("QRScannerPlugin detached")
        pauseScanning()
        collectorJob?.cancel()
        collectorJob = null
        this.stateHolder = null
        this.cameraController = null
    }

    fun attachToStateHolder(stateHolder: FolarStateHolder) {
        stateHolder.attachPlugin(this)
    }

    fun startScanning() {
        cameraController?.let { controller ->
            isScanning.value = true
            try {
                startScanning(controller = controller) { result ->
                    if (isScanning.value) {
                        coroutineScope.launch {
                            qrCodeFlow.emit(result)
                            // Legacy event might need updating or we just emit text for now if FolarEvent is sealed elsewhere
                             stateHolder?.emitEvent(FolarEvent.QRCodeScanned(result.text))
                        }
                    }
                }
            } catch (e: Exception) {
                println("QRScannerPlugin: Failed to start scanning: ${e.message}")
                isScanning.value = false
            }
        } ?: run {
            println("QRScannerPlugin: CameraController is not initialized")
            isScanning.value = false
        }
    }

    fun pauseScanning() {
        isScanning.value = false
    }

    fun resumeScanning() {
        isScanning.value = true
        startScanning()
    }

    fun getQrCodeFlow() = qrCodeFlow.asSharedFlow()
}

@Composable
fun rememberQRScannerPlugin(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): QRScannerPlugin {
    return remember {
        QRScannerPlugin(coroutineScope)
    }
}

// Android implementation moved here
fun startScanning(
    controller: CameraController,
    onQrScanner: (QRResult) -> Unit
) {
    Log.d("QRScanner", "Starting QR scanner")
    controller.enableQrCodeScanner(onQrScanner)
}

fun CameraController.enableQrCodeScanner(onQrScanner: (QRResult) -> Unit) {
    Log.d("QRScanner", "Enabling QR code scanner")
    try {
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {
                setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    QRCodeAnalyzer(onQrScanner)
                )
            }

        updateImageAnalyzer()
    } catch (e: Exception) {
        Log.e("QRScanner", "Failed to enable QR scanner: ${e.message}", e)
    }
}

private class QRCodeAnalyzer(private val onQrScanner: (QRResult) -> Unit) : ImageAnalysis.Analyzer {
    private val decodeHints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
        put(
            DecodeHintType.POSSIBLE_FORMATS,
            listOf(
                com.google.zxing.BarcodeFormat.QR_CODE,
                com.google.zxing.BarcodeFormat.EAN_13,
                com.google.zxing.BarcodeFormat.EAN_8,
                com.google.zxing.BarcodeFormat.CODE_128,
                com.google.zxing.BarcodeFormat.CODE_39,
                com.google.zxing.BarcodeFormat.UPC_A,
                com.google.zxing.BarcodeFormat.UPC_E
            )
        )
    }
    private val reader = MultiFormatReader().apply { setHints(decodeHints) }
    private var lastScannedCode: String? = null
    private var lastScanTime: Long = 0
    private val scanDebounceMs = 1000L

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }

        if (image.format != ImageFormat.YUV_420_888) {
            Log.e("QRScanner", "Unsupported image format: ${image.format}")
            imageProxy.close()
            return
        }

        try {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Basic YUV conversion or just passing bytes if ZXing handles it?
            // RGBLuminanceSource expects RGB ints.
            // But we have YUV bytes.
            // PlanarYUVLuminanceSource is correct for YUV.
            // But `qr_android.txt` used `RGBLuminanceSource` with manual conversion?
            // "val intArray = IntArray(bytes.size) { bytes[it].toInt() and 0xFF }"
            // This treats bytes as grayscale/luminance directly (which Y channel of YUV is).
            // So using RGBLuminanceSource with this data effectively creates a grayscale image.

            val intArray = IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
            val source = RGBLuminanceSource(image.width, image.height, intArray)
            val bitmap = BinaryBitmap(HybridBinarizer(source))

            val result = reader.decode(bitmap)
            val currentTime = System.currentTimeMillis()

            if (result.text != lastScannedCode || (currentTime - lastScanTime) > scanDebounceMs) {
                Log.d("QRScanner", "QR Code detected: ${result.text}")
                lastScannedCode = result.text
                lastScanTime = currentTime

                // Map points to normalized coordinates (0..1)
                // Note: ImageProxy might be rotated relative to screen.
                // Assuming portrait orientation for now.
                // Image is usually 90deg rotated on back camera in portrait.
                // width/height of imageProxy correspond to the buffer dimensions.

                val points = result.resultPoints?.map { point ->
                    // Rotate point 90 degrees clockwise if needed or normalized
                    // Let's just normalize first
                    Pair(point.x / image.width.toFloat(), point.y / image.height.toFloat())
                } ?: emptyList()

                onQrScanner(QRResult(result.text, points))
            }
        } catch (e: Exception) {
            // expected
        } finally {
            imageProxy.close()
        }
    }
}