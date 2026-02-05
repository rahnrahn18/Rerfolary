package com.kashif.folar.state

import androidx.compose.runtime.Stable
import com.kashif.folar.controller.CameraController
import com.kashif.folar.result.ImageCaptureResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pure Kotlin state holder for camera operations.
 * This class is the reactive bridge between platform-specific [CameraController] 
 * and Compose UI layer.
 */
@Stable
class FolarStateHolder(
    private val cameraConfiguration: CameraConfiguration,
    private val controllerFactory: suspend () -> CameraController,
    private val coroutineScope: CoroutineScope
) {
    // ═══════════════════════════════════════════════════════════════
    // State Management
    // ═══════════════════════════════════════════════════════════════
    
    private val _cameraState = MutableStateFlow<FolarState>(FolarState.Initializing)
    val cameraState: StateFlow<FolarState> = _cameraState.asStateFlow()
    
    private val _uiState = MutableStateFlow(CameraUIState())
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<FolarEvent>()
    val events: SharedFlow<FolarEvent> = _events.asSharedFlow()
    
    val pluginScope: CoroutineScope = coroutineScope
    
    // ═══════════════════════════════════════════════════════════════
    // Internal State
    // ═══════════════════════════════════════════════════════════════
    
    private var controller: CameraController? = null
    private val attachedPlugins = mutableListOf<FolarPlugin>()
    private var isInitialized = false
    
    // ═══════════════════════════════════════════════════════════════
    // Lifecycle Management
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Initializes the camera controller and starts the session.
     * Safe to call multiple times - subsequent calls are no-ops.
     */
    suspend fun initialize() {
        if (isInitialized) return
        
        try {
            _cameraState.value = FolarState.Initializing
            
            // Create controller
            val newController = controllerFactory()
            controller = newController
            
            // Start session
            newController.startSession()
            
            // Initialize UI state from controller
            updateUIStateFromController(newController)
            
            // Initialize plugins
            attachedPlugins.forEach { plugin ->
                plugin.onAttach(this)
            }
            
            // Update to ready state
            _cameraState.value = FolarState.Ready(
                controller = newController,
                uiState = _uiState.value
            )
            
            isInitialized = true
            
        } catch (e: Exception) {
            _cameraState.value = FolarState.Error(
                exception = e,
                message = "Failed to initialize camera: ${e.message}",
                isRetryable = true
            )
            throw e
        }
    }
    
    /**
     * Updates the camera configuration dynamically without full re-initialization where possible.
     */
    fun updateConfiguration(config: CameraConfiguration) {
        val currentController = controller ?: return

        // Update Aspect Ratio
        currentController.setAspectRatio(config.aspectRatio)

        // Update Resolution
        if (config.targetResolution != null) {
            currentController.setResolution(config.targetResolution.first, config.targetResolution.second)
        }

        // Update Lens
        currentController.setCameraLens(config.cameraLens)

        // Update Flash/Torch
        currentController.setFlashMode(config.flashMode)
        currentController.setTorchMode(config.torchMode)

        // Update Device Type
        currentController.setPreferredCameraDeviceType(config.cameraDeviceType)

        // Update other params
        currentController.setImageFormat(config.imageFormat)
        currentController.setQualityPrioritization(config.qualityPrioritization)
        currentController.setDirectory(config.directory)

        // Sync UI state
        updateUIStateFromController(currentController)
    }

    /**
     * Shuts down the camera controller and releases resources.
     * Safe to call multiple times.
     */
    fun shutdown() {
        if (!isInitialized) return
        
        try {
            // Detach plugins
            attachedPlugins.forEach { plugin ->
                plugin.onDetach()
            }
            attachedPlugins.clear()
            
            // Stop session and cleanup
            controller?.stopSession()
            controller?.cleanup()
            controller = null
            
            isInitialized = false
            _cameraState.value = FolarState.Initializing
            
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                lastError = "Shutdown error: ${e.message}"
            )
        }
    }
    
    suspend fun getReadyCameraController(): CameraController? {
        return cameraState
            .filterIsInstance<FolarState.Ready>()
            .first()
            .controller
    }
    
    fun attachPlugin(plugin: FolarPlugin) {
        attachedPlugins.add(plugin)
        if (isInitialized) {
            plugin.onAttach(this)
        }
    }
    
    fun detachPlugin(plugin: FolarPlugin) {
        if (attachedPlugins.remove(plugin)) {
            plugin.onDetach()
        }
    }
    
    fun getController(): CameraController? = controller
    
    fun captureImage() {
        val currentController = controller ?: run {
            coroutineScope.launch {
                _events.emit(FolarEvent.CaptureFailed(
                    Exception("Camera not initialized")
                ))
            }
            return
        }
        
        coroutineScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isCapturing = true)
                val result = currentController.takePictureToFile()
                _events.emit(FolarEvent.ImageCaptured(result))
            } catch (e: Exception) {
                _events.emit(FolarEvent.CaptureFailed(e))
                _uiState.value = _uiState.value.copy(
                    lastError = "Capture failed: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isCapturing = false)
            }
        }
    }
    
    fun setZoom(zoom: Float) {
        val currentController = controller ?: return
        currentController.setZoom(zoom)
        _uiState.value = _uiState.value.copy(
            zoomLevel = currentController.getZoom()
        )
    }
    
    fun toggleFlashMode() {
        val currentController = controller ?: return
        currentController.toggleFlashMode()
        _uiState.value = _uiState.value.copy(
            flashMode = currentController.getFlashMode()
        )
    }
    
    fun setFlashMode(mode: com.kashif.folar.enums.FlashMode) {
        val currentController = controller ?: return
        currentController.setFlashMode(mode)
        _uiState.value = _uiState.value.copy(flashMode = mode)
    }
    
    fun toggleTorchMode() {
        val currentController = controller ?: return
        currentController.toggleTorchMode()
        _uiState.value = _uiState.value.copy(
            torchMode = currentController.getTorchMode()
        )
    }
    
    fun setTorchMode(mode: com.kashif.folar.enums.TorchMode) {
        val currentController = controller ?: return
        currentController.setTorchMode(mode)
        _uiState.value = _uiState.value.copy(torchMode = mode)
    }
    
    fun toggleCameraLens() {
        val currentController = controller ?: return
        currentController.toggleCameraLens()
        _uiState.value = _uiState.value.copy(
            cameraLens = currentController.getCameraLens()
        )
    }
    
    suspend fun emitEvent(event: FolarEvent) {
        _events.emit(event)
    }
    
    fun updateUIState(update: (CameraUIState) -> CameraUIState) {
        _uiState.value = update(_uiState.value)
    }
    
    private fun updateUIStateFromController(controller: CameraController) {
        _uiState.value = CameraUIState(
            zoomLevel = controller.getZoom(),
            maxZoom = controller.getMaxZoom(),
            flashMode = controller.getFlashMode(),
            torchMode = controller.getTorchMode(),
            cameraLens = controller.getCameraLens(),
            imageFormat = controller.getImageFormat(),
            qualityPrioritization = controller.getQualityPrioritization(),
            cameraDeviceType = controller.getPreferredCameraDeviceType(),
            isCapturing = false,
            lastError = null
        )
    }
}

@Stable
interface FolarPlugin {
    fun onAttach(stateHolder: FolarStateHolder)
    fun onDetach()
}
