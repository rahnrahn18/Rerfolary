package com.kashif.ocrPlugin

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resumeWithException

@Stable
class OcrPlugin(
    private val coroutineScope: CoroutineScope
) : CameraPlugin, FolarPlugin {
    private var cameraController: CameraController? = null
    private var stateHolder: FolarStateHolder? = null
    private val _ocrFlow = MutableSharedFlow<String>()
    val ocrFlow = _ocrFlow.asSharedFlow()
    private var isRecognizing = atomic(false)
    private var collectorJob: Job? = null

    override fun initialize(cameraController: CameraController) {
        this.cameraController = cameraController
    }

    override fun onAttach(stateHolder: FolarStateHolder) {
        this.stateHolder = stateHolder
        collectorJob = stateHolder.pluginScope.launch {
            stateHolder.cameraState
                .filterIsInstance<FolarState.Ready>()
                .collect { readyState ->
                    this@OcrPlugin.cameraController = readyState.controller
                    startRecognition()
                }
        }
    }

    override fun onDetach() {
        stopRecognition()
        collectorJob?.cancel()
        collectorJob = null
        this.stateHolder = null
        this.cameraController = null
    }

    fun attachToStateHolder(stateHolder: FolarStateHolder) {
        stateHolder.attachPlugin(this)
    }

    fun startRecognition() {
        cameraController?.let { controller ->
            isRecognizing.value = true
            startRecognition(controller = controller) { text ->
                if (isRecognizing.value) {
                    coroutineScope.launch {
                        _ocrFlow.emit(text)
                        stateHolder?.emitEvent(FolarEvent.TextRecognized(text))
                    }
                }
            }
        }
    }

    fun stopRecognition() {
        isRecognizing.value = false
    }

    suspend fun recognizeTextFromImage(image: ImageBitmap): String {
        return recognizeTextFromImageBitmap(image)
    }
}

@Composable
fun rememberOcrPlugin(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): OcrPlugin {
    return remember {
        OcrPlugin(coroutineScope)
    }
}

// Android implementation
fun startRecognition(
    controller: CameraController,
    onTextRecognized: (String) -> Unit
) {
    controller.enableTextRecognition(onTextRecognized)
}

suspend fun recognizeTextFromImageBitmap(bitmap: ImageBitmap): String =
    withContext(Dispatchers.Default) {
        try {
            suspendCancellableCoroutine { continuation ->
                Log.d("TextRecognition", "Starting text extraction from bitmap")

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                if (bitmap.width <= 0 || bitmap.height <= 0) {
                    continuation.resumeWithException(IllegalArgumentException("Invalid bitmap dimensions"))
                    return@suspendCancellableCoroutine
                }

                val androidBitmap = bitmap.asAndroidBitmap()
                val image = InputImage.fromBitmap(androidBitmap, 0)

                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val extractedText = result.text
                        Log.d(
                            "TextRecognition",
                            "Text extracted successfully: ${extractedText.take(100)}..."
                        )
                        continuation.resume(extractedText) { cause, _, _ ->
                            recognizer.close()
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("TextRecognition", "Text extraction failed", exception)
                        continuation.resumeWithException(exception)
                    }

                continuation.invokeOnCancellation {
                    recognizer.close()
                }
            }
        } catch (e: Exception) {
            Log.e("TextRecognition", "Error during text extraction", e)
            throw e
        }
    }

fun CameraController.enableTextRecognition(onTextRecognized: (String) -> Unit) {
    try {
        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analyzer.setAnalyzer(ContextCompat.getMainExecutor(context), TextAnalyzer(onTextRecognized))

        imageAnalyzer = analyzer
        updateImageAnalyzer()
    } catch (e: Exception) {
        Log.e("OcrPlugin", "Failed to enable text recognition: ${e.message}")
    }
}

private class TextAnalyzer(private val onTextRecognized: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (visionText.text.isNotEmpty()) {
                        onTextRecognized(visionText.text)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("OcrPlugin", "Text recognition failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
