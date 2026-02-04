package com.kashif.folar.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.util.Size
import android.media.ExifInterface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.kashif.folar.enums.*
import kotlinx.atomicfu.atomic
import android.util.Log
import com.kashif.folar.enums.AspectRatio
import com.kashif.folar.plugins.CameraPlugin
import com.kashif.folar.result.ImageCaptureResult
import com.kashif.folar.utils.InvalidConfigurationException
import com.kashif.folar.utils.MemoryManager
import com.kashif.folar.utils.compressToByteArray
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import com.pixpark.gpupixel.GPUPixel
import com.pixpark.gpupixel.GPUPixelSourceImage
import com.pixpark.gpupixel.GPUPixelFilter
import com.pixpark.gpupixel.GPUPixelSinkRawData
import java.nio.ByteBuffer

/**
 * Android-specific implementation of [CameraController] using CameraX.
 */
class CameraController(
    val context: Context,
    val lifecycleOwner: LifecycleOwner,
    internal var flashMode: FlashMode,
    internal var torchMode: TorchMode,
    internal var cameraLens: CameraLens,
    internal var imageFormat: ImageFormat,
    internal var qualityPriority: QualityPrioritization,
    internal var directory: Directory,
    internal var cameraDeviceType: CameraDeviceType,
    internal var returnFilePath: Boolean,
    internal var aspectRatio: AspectRatio,
    internal var plugins: MutableList<CameraPlugin>,
    internal var targetResolution: Pair<Int, Int>? = null
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    var imageAnalyzer: ImageAnalysis? = null
    private var previewView: PreviewView? = null
    private var aperture: Float = 0.0f

    private val imageCaptureListeners = mutableListOf<(ByteArray) -> Unit>()


    private val memoryManager = MemoryManager
    private val pendingCaptures = atomic(0)
    private val maxConcurrentCaptures = 3

    private val imageProcessingExecutor = Executors.newFixedThreadPool(2)

    fun bindCamera(previewView: PreviewView, onCameraReady: () -> Unit = {}) {
        Log.d("Folar", "==> bindCamera() called for deviceType: $cameraDeviceType")
        this.previewView = previewView

        memoryManager.initialize(context)

        try {
            GPUPixel.Init(context)
            Log.d("Folar", "GPUPixel initialized successfully")
        } catch (e: Exception) {
            Log.e("Folar", "Failed to initialize GPUPixel: ${e.message}")
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()
                Log.d("Folar", "==> Unbind all existing cameras")

                val resolutionSelector = createResolutionSelector()

                preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val cameraSelector = createCameraSelector()
                Log.d("Folar", "==> Camera selector created for: $cameraDeviceType")

                configureCaptureUseCase(resolutionSelector)

                val useCaseGroupBuilder = UseCaseGroup.Builder()
                    .addUseCase(preview!!)
                    .addUseCase(imageCapture!!)

                imageAnalyzer?.let { useCaseGroupBuilder.addUseCase(it) }

                previewView.viewPort?.let { useCaseGroupBuilder.setViewPort(it) }

                val useCaseGroup = useCaseGroupBuilder.build()

                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroup
                )
                Log.d("Folar", "==> Camera successfully bound with deviceType: $cameraDeviceType")

                onCameraReady()

            } catch (exc: Exception) {
                Log.e("Folar", "==> Use case binding failed for $cameraDeviceType: ${exc.message}")
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Create a resolution selector based on memory conditions
     */
    private fun createResolutionSelector(): ResolutionSelector {

        memoryManager.updateMemoryStatus()

        return if (targetResolution != null) {
            // When target resolution is set, prioritize it over aspect ratio
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(targetResolution!!.first, targetResolution!!.second),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
        } else {
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspectRatio.toCameraXAspectRatioStrategy())
                .build()
        }
    }

    private fun AspectRatio.toCameraXAspectRatioStrategy(): AspectRatioStrategy = when (this) {
        AspectRatio.RATIO_16_9, AspectRatio.RATIO_9_16 -> AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
        AspectRatio.RATIO_4_3 -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        AspectRatio.RATIO_1_1 -> AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY // closest available
    }
    
    /**
     * Creates a camera selector based on lens facing and device type.
     * 
     * Uses Camera2 Interop to access physical camera characteristics for proper
     * device type selection (telephoto, ultra-wide, macro). Falls back gracefully
     * to the default camera if the requested type is not available.
     * 
     * @return CameraSelector configured for the current lens and device type
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun createCameraSelector(): CameraSelector {
        val builder = CameraSelector.Builder()
            .requireLensFacing(cameraLens.toCameraXLensFacing())

        when (cameraDeviceType) {
            CameraDeviceType.WIDE_ANGLE, CameraDeviceType.DEFAULT -> {
                // Default camera, no filter needed
            }
            CameraDeviceType.TELEPHOTO -> {
                builder.addCameraFilter { cameraInfos ->
                    cameraInfos.filter { cameraInfo ->
                        try {
                            val camera2Info = Camera2CameraInfo.from(cameraInfo)
                            val focalLengths = camera2Info.getCameraCharacteristic(
                                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                            )?.toList() ?: emptyList()
                            focalLengths.any { it > 4.0f }
                        } catch (e: Exception) {
                            false
                        }
                    }.ifEmpty { 
                        Log.w("Folar", "Telephoto camera not available, using default")
                        cameraInfos.take(1)
                    }
                }
            }
            CameraDeviceType.ULTRA_WIDE -> {
                builder.addCameraFilter { cameraInfos ->
                    cameraInfos.filter { cameraInfo ->
                        try {
                            val camera2Info = Camera2CameraInfo.from(cameraInfo)
                            val focalLengths = camera2Info.getCameraCharacteristic(
                                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                            )?.toList() ?: emptyList()
                            focalLengths.any { it < 2.5f }
                        } catch (e: Exception) {
                            false
                        }
                    }.ifEmpty {
                        Log.w("Folar", "Ultra-wide camera not available, using default")
                        cameraInfos.take(1)
                    }
                }
            }
            CameraDeviceType.MACRO -> {
                builder.addCameraFilter { cameraInfos ->
                    cameraInfos.filter { cameraInfo ->
                        try {
                            val camera2Info = Camera2CameraInfo.from(cameraInfo)
                            val minFocusDistance = camera2Info.getCameraCharacteristic(
                                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
                            ) ?: 0f
                            minFocusDistance > 0f && minFocusDistance < 0.2f
                        } catch (e: Exception) {
                            false
                        }
                    }.ifEmpty {
                        Log.w("Folar", "Macro camera not available, using default")
                        cameraInfos.take(1)
                    }
                }
            }
        }
        
        return builder.build()
    }

    /**
     * Configure the image capture use case with settings adapted to current memory conditions
     */
    @OptIn(ExperimentalZeroShutterLag::class)
    private fun configureCaptureUseCase(resolutionSelector: ResolutionSelector) {

        imageCapture = ImageCapture.Builder()
            .setFlashMode(flashMode.toCameraXFlashMode())
            .setCaptureMode(
                when (qualityPriority) {
                    QualityPrioritization.QUALITY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    QualityPrioritization.SPEED -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
                    QualityPrioritization.BALANCED -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    QualityPrioritization.NONE -> {
                        if (memoryManager.isUnderMemoryPressure()) {
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        } else {
                            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        }
                    }
                }
            )
                .setResolutionSelector(resolutionSelector)
            .build()
    }

    fun updateImageAnalyzer() {
        camera?.let {
            cameraProvider?.unbind(imageAnalyzer)
            imageAnalyzer?.let { analyzer ->
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.Builder().requireLensFacing(cameraLens.toCameraXLensFacing())
                        .build(),
                    analyzer
                )
            }
        } ?: throw InvalidConfigurationException("Camera not initialized.")
    }

    @Deprecated(
        message = "Use takePictureToFile() instead for better performance",
        replaceWith = ReplaceWith("takePictureToFile()"),
        level = DeprecationLevel.WARNING
    )
    suspend fun takePicture(): ImageCaptureResult =
        suspendCancellableCoroutine { cont ->
            // Atomic counter rejection pattern - matches native camera apps
            if (pendingCaptures.incrementAndGet() > maxConcurrentCaptures) {
                pendingCaptures.decrementAndGet()
                Log.w("Folar", "Burst queue full, dropping frame (${pendingCaptures.value} in progress)")
                cont.resume(ImageCaptureResult.Error(Exception("Burst queue full, capture rejected")))
                return@suspendCancellableCoroutine
            }

            // Update memory status before capture
            memoryManager.updateMemoryStatus()

            // Perform capture with constant quality (95 for JPEG)
            performCapture(cont, quality = 95)

            cont.invokeOnCancellation {
                pendingCaptures.decrementAndGet()
            }
        }

    /**
     * Fast capture method that returns file path directly without ByteArray processing.
     * Significantly faster than takePicture() - skips decode/encode cycles.
     */
    suspend fun takePictureToFile(): ImageCaptureResult =
        suspendCancellableCoroutine { cont ->
            if (pendingCaptures.incrementAndGet() > maxConcurrentCaptures) {
                pendingCaptures.decrementAndGet()
                Log.w("Folar", "Burst queue full, dropping frame")
                cont.resume(ImageCaptureResult.Error(Exception("Burst queue full, capture rejected")))
                return@suspendCancellableCoroutine
            }

            performCaptureToFile(cont)

            cont.invokeOnCancellation {
                pendingCaptures.decrementAndGet()
            }
        }

    /**
     * Perform fast file-based capture without ByteArray processing.
     * Directly saves to final destination and returns file path.
     */
    private fun performCaptureToFile(continuation: CancellableContinuation<ImageCaptureResult>) {
        // Create final output file directly in desired directory
        val outputFile = createFinalOutputFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Check if aperture processing is needed
                    if (aperture > 0.01f) {
                        imageProcessingExecutor.execute {
                            try {
                                processImageWithGpupixel(outputFile, aperture)
                                pendingCaptures.decrementAndGet()
                                notifyMediaStore(outputFile)
                                continuation.resume(ImageCaptureResult.SuccessWithFile(outputFile.absolutePath))
                            } catch (e: Exception) {
                                Log.e("Folar", "GPUPixel processing failed: ${e.message}", e)
                                pendingCaptures.decrementAndGet()
                                // Fallback: return original file even if processing failed
                                notifyMediaStore(outputFile)
                                continuation.resume(ImageCaptureResult.SuccessWithFile(outputFile.absolutePath))
                            }
                        }
                    } else {
                        pendingCaptures.decrementAndGet()
                        // Notify MediaStore so image appears in Gallery
                        notifyMediaStore(outputFile)
                        continuation.resume(ImageCaptureResult.SuccessWithFile(outputFile.absolutePath))
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Folar", "Image capture failed: ${exc.message}", exc)
                    pendingCaptures.decrementAndGet()
                    outputFile.delete() // Clean up failed capture file
                    continuation.resume(ImageCaptureResult.Error(exc))
                }
            }
        ) ?: run {
            pendingCaptures.decrementAndGet()
            continuation.resume(ImageCaptureResult.Error(Exception("ImageCapture use case is not initialized.")))
        }
    }

    private fun processImageWithGpupixel(file: File, aperture: Float) {
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return

            // Create GPUPixel source from bitmap
            val source = GPUPixelSourceImage.CreateFromBitmap(bitmap)

            // Create Blur Filter (Simulating Aperture)
            val filter = GPUPixelFilter.Create(GPUPixelFilter.IOS_BLUR_FILTER)
            // IOSBlurFilter: blurSigma, saturation, downSampling
            // We want to blur based on aperture (0.0 - 1.0)
            // Aperture 1.0 = Max Blur
            val blurSigma = aperture * 15.0f // Range 0 to 15
            filter.SetProperty("blurSigma", blurSigma)
            filter.SetProperty("saturation", 1.0f) // Keep original saturation
            filter.SetProperty("downSampling", 1.0f) // High quality

            // Create Sink to retrieve data
            val sink = GPUPixelSinkRawData.Create()

            // Build chain: Source -> Filter -> Sink
            source.AddSink(filter)
            filter.AddSink(sink)

            // Execute
            source.Render()

            // Get Result
            val rgbaData = sink.GetRgbaBuffer()
            val width = sink.GetWidth()
            val height = sink.GetHeight()

            if (rgbaData != null && width > 0 && height > 0) {
                // Convert RGBA byte array back to Bitmap
                val processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                processedBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaData))

                // Save processed bitmap back to file, overwriting original
                FileOutputStream(file).use { out ->
                    val format = if (imageFormat == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    processedBitmap.compress(format, 95, out)
                }

                processedBitmap.recycle()
                Log.d("Folar", "Applied Aperture filter with sigma: $blurSigma")
            }

            // Clean up
            source.Destroy()
            filter.Destroy()
            sink.Destroy()
            bitmap.recycle()

        } catch (e: Exception) {
            Log.e("Folar", "Error in processImageWithGpupixel: ${e.message}")
            throw e
        }
    }

    /**
     * Perform the image capture with constant quality
     */
    private fun performCapture(
        continuation: CancellableContinuation<ImageCaptureResult>,
        quality: Int
    ) {
        val outputOptions = ImageCapture.OutputFileOptions.Builder(createTempFile()).build()

        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (returnFilePath) {
                        // File path mode: Return file path from savedUri
                        val fileUri = output.savedUri
                        if (fileUri != null) {
                            // Convert content:// URI to file path
                            val filePath = if (fileUri.scheme == "file") {
                                fileUri.path
                            } else {
                                // For content:// URIs, get the real path
                                val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
                                context.contentResolver.query(fileUri, projection, null, null, null)?.use { cursor ->
                                    val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                                    if (cursor.moveToFirst()) cursor.getString(columnIndex) else null
                                } ?: fileUri.path
                            }
                            
                            if (filePath != null) {
                                continuation.resume(ImageCaptureResult.SuccessWithFile(filePath))
                            } else {
                                Log.e("Folar", "Failed to get file path from capture result")
                                continuation.resume(ImageCaptureResult.Error(Exception("Failed to get file path")))
                            }
                        } else {
                            Log.e("Folar", "Capture result has no savedUri")
                            continuation.resume(ImageCaptureResult.Error(Exception("No file URI returned")))
                        }
                        pendingCaptures.decrementAndGet()
                    } else {
                        // ByteArray mode: Process and return ByteArray
                        imageProcessingExecutor.execute {
                            try {
                                val byteArray = processImageOutput(output, quality)

                                if (byteArray != null) {
                                    imageCaptureListeners.forEach { it(byteArray) }
                                    continuation.resume(ImageCaptureResult.Success(byteArray))
                                } else {
                                    Log.e("Folar", "Failed to convert image to ByteArray")
                                    continuation.resume(ImageCaptureResult.Error(Exception("Failed to convert image to ByteArray.")))
                                }
                            } finally {
                                pendingCaptures.decrementAndGet()
                            }
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Folar", "Image capture failed: ${exc.message}", exc)
                    pendingCaptures.decrementAndGet()
                    continuation.resume(ImageCaptureResult.Error(exc))
                }
            }
        ) ?: run {
            pendingCaptures.decrementAndGet()
            continuation.resume(ImageCaptureResult.Error(Exception("ImageCapture use case is not initialized.")))
        }
    }

    /**
     * Process the saved image output with optimized approach.
     * 
     * CameraX should apply targetRotation to the output file, but some devices (Samsung)
     * strip EXIF data or apply rotation inconsistently. We verify EXIF orientation first.
     * 
     * Fast path: Direct file read when EXIF orientation is correct (NORMAL)
     * Slow path: Process when format conversion, memory pressure, or rotation needed
     */
    private fun processImageOutput(
        output: ImageCapture.OutputFileResults,
        quality: Int
    ): ByteArray? {
        return try {
            output.savedUri?.let { uri ->
                val tempFile = createTempFile("temp_image", ".jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                try {
                    // Check EXIF orientation - Samsung devices may have incorrect orientation
                    val exif = ExifInterface(tempFile.absolutePath)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    
                    val needsRotation = orientation != ExifInterface.ORIENTATION_NORMAL && 
                                       orientation != ExifInterface.ORIENTATION_UNDEFINED
                    val needsProcessing = imageFormat == ImageFormat.PNG || 
                                         memoryManager.isUnderMemoryPressure() ||
                                         needsRotation
                    
                    if (!needsProcessing) {
                        // Fast path: EXIF orientation is correct, read file bytes directly
                        // This avoids decode→rotate→re-encode cycle (saves 2-3 seconds and quality loss)
                        tempFile.readBytes().also { tempFile.delete() }
                    } else {
                        // Slow path: Need format conversion, downsampling, or rotation fix
                        val options = BitmapFactory.Options().apply {
                            if (memoryManager.isUnderMemoryPressure()) {
                                inJustDecodeBounds = true
                                BitmapFactory.decodeFile(tempFile.absolutePath, this)
                                inSampleSize = calculateSampleSize(outWidth, outHeight)
                                inJustDecodeBounds = false
                            }
                        }

                        val originalBitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)
                        tempFile.delete()

                        // Apply rotation if needed (Samsung device fix)
                        val rotatedBitmap = if (originalBitmap != null && needsRotation) {
                            val rotationAngle = when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                else -> 0f
                            }
                            
                            if (rotationAngle != 0f) {
                                Matrix().apply { postRotate(rotationAngle) }.let { matrix ->
                                    Bitmap.createBitmap(
                                        originalBitmap, 0, 0,
                                        originalBitmap.width, originalBitmap.height,
                                        matrix, true
                                    ).also { originalBitmap.recycle() }
                                }
                            } else {
                                originalBitmap
                            }
                        } else {
                            originalBitmap
                        }

                        // Convert format and compress
                        rotatedBitmap?.compressToByteArray(
                            format = when (imageFormat) {
                                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                            },
                            quality = quality,
                            recycleInput = true
                        )
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.e("Folar", "Error processing image output: ${e.message}", e)
            null
        }
    }

    /**
     * Calculate appropriate sample size for bitmap decoding based on memory conditions
     */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        return when {
            memoryManager.isUnderMemoryPressure() -> {
                // Under memory pressure: downsample to ~2MP
                var sampleSize = 1
                val totalPixels = width * height
                val targetPixels = 2_000_000

                while ((totalPixels / (sampleSize * sampleSize)) > targetPixels) {
                    sampleSize *= 2
                }
                sampleSize
            }

            pendingCaptures.value > 1 -> {
                // Multiple captures pending: downsample 2x for faster processing
                2
            }

            else -> {
                // Normal capture: full resolution
                1
            }
        }
    }

    fun toggleFlashMode() {
        flashMode = when (flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        imageCapture?.flashMode = flashMode.toCameraXFlashMode()
    }

    fun setFlashMode(mode: FlashMode) {
        flashMode = mode
        imageCapture?.flashMode = mode.toCameraXFlashMode()
    }

    fun getFlashMode(): FlashMode? {
        fun Int.toFolarFlashMode(): FlashMode? {
            return when (this) {
                ImageCapture.FLASH_MODE_ON -> FlashMode.ON
                ImageCapture.FLASH_MODE_OFF -> FlashMode.OFF
                ImageCapture.FLASH_MODE_AUTO -> FlashMode.AUTO
                else -> null
            }
        }

        return imageCapture?.flashMode?.toFolarFlashMode()
    }

    fun toggleTorchMode() {
        torchMode = when (torchMode) {
            TorchMode.OFF -> TorchMode.ON
            TorchMode.ON -> TorchMode.AUTO
            TorchMode.AUTO -> TorchMode.OFF
        }
        // CameraX doesn't support AUTO torch mode, treat it as ON
        val enableTorch = torchMode == TorchMode.ON || torchMode == TorchMode.AUTO
        if (torchMode == TorchMode.AUTO) {
            Log.w("Folar", "TorchMode.AUTO not natively supported, using ON")
        }
        camera?.cameraControl?.enableTorch(enableTorch)
    }

    fun setTorchMode(mode: TorchMode) {
        torchMode = mode
        // CameraX doesn't support AUTO torch mode, treat it as ON
        val enableTorch = mode == TorchMode.ON || mode == TorchMode.AUTO
        if (mode == TorchMode.AUTO) {
            Log.w("Folar", "TorchMode.AUTO not natively supported, using ON")
        }
        camera?.cameraControl?.enableTorch(enableTorch)
    }
    
    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(1f, getMaxZoom()))
    }
    
    fun getZoom(): Float {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
    }
    
    fun getMaxZoom(): Float {
        return camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
    }

    fun setAperture(aperture: Float) {
        this.aperture = aperture.coerceIn(0.0f, 1.0f)
        Log.d("Folar", "Setting aperture to $aperture (Simulated/Ready for GpuPixel)")
        // TODO: Call gpupixel JNI to set blur strength or use Bokeh logic
    }

    fun getAperture(): Float {
        return aperture
    }
    
    fun getTorchMode(): TorchMode? {
        return torchMode
    }

    fun toggleCameraLens() {

        memoryManager.updateMemoryStatus()


        if (memoryManager.isUnderMemoryPressure()) {
            memoryManager.clearBufferPools()
            System.gc()
        }

        cameraLens = if (cameraLens == CameraLens.BACK) CameraLens.FRONT else CameraLens.BACK
        previewView?.let { bindCamera(it) }
    }
    
    fun getCameraLens(): CameraLens? {
        return cameraLens
    }
    
    fun getImageFormat(): ImageFormat {
        return imageFormat
    }
    
    fun getQualityPrioritization(): QualityPrioritization {
        return qualityPriority
    }
    
    fun getPreferredCameraDeviceType(): CameraDeviceType {
        return cameraDeviceType
    }

    fun startSession() {


        memoryManager.updateMemoryStatus()
        memoryManager.clearBufferPools()
        initializeControllerPlugins()
    }

    fun stopSession() {
        cameraProvider?.unbindAll()
        memoryManager.clearBufferPools()
    }

    fun addImageCaptureListener(listener: (ByteArray) -> Unit) {
        imageCaptureListeners.add(listener)
    }

    fun initializeControllerPlugins() {
        plugins.forEach { it.initialize(this) }
    }


    private fun createTempFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        
        // Use configured directory
        val storageDir = when (directory) {
            Directory.PICTURES -> android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_PICTURES
            )
            Directory.DCIM -> android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DCIM
            )
            Directory.DOCUMENTS -> context.getExternalFilesDir(null) ?: context.filesDir
        }
        
        // Create Folar subdirectory
        val cameraKDir = File(storageDir, "Folar")
        if (!cameraKDir.exists()) {
            cameraKDir.mkdirs()
        }
        
        return File(
            cameraKDir,
            "IMG_${timeStamp}.${imageFormat.extension}"
        )
    }

    /**
     * Creates final output file for direct capture (used by takePictureToFile).
     * Same as createTempFile but semantically represents the final destination.
     */
    private fun createFinalOutputFile(): File = createTempFile()

    /**
     * Notifies Android's MediaStore about a new image file so it appears in Gallery.
     * Uses MediaScannerConnection for compatibility across Android versions.
     */
    private fun notifyMediaStore(file: File) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(when (imageFormat) {
                    ImageFormat.JPEG -> "image/jpeg"
                    ImageFormat.PNG -> "image/png"
                }),
                null
            )
            Log.d("Folar", "MediaStore notified: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("Folar", "Failed to notify MediaStore: ${e.message}")
        }
    }

    private fun FlashMode.toCameraXFlashMode(): Int = when (this) {
        FlashMode.ON -> ImageCapture.FLASH_MODE_ON
        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
    }

    private fun CameraLens.toCameraXLensFacing(): Int = when (this) {
        CameraLens.FRONT -> CameraSelector.LENS_FACING_FRONT
        CameraLens.BACK -> CameraSelector.LENS_FACING_BACK
    }

    /**
     * Clean up resources when no longer needed
     * Should be called when the controller is being destroyed
     */
    fun cleanup() {
        imageProcessingExecutor.shutdown()
        memoryManager.clearBufferPools()
    }
}