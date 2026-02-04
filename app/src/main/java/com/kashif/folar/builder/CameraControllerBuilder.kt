package com.kashif.folar.builder

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.kashif.folar.controller.CameraController
import com.kashif.folar.enums.AspectRatio
import com.kashif.folar.enums.CameraDeviceType
import com.kashif.folar.enums.CameraLens
import com.kashif.folar.enums.Directory
import com.kashif.folar.enums.FlashMode
import com.kashif.folar.enums.ImageFormat
import com.kashif.folar.enums.QualityPrioritization
import com.kashif.folar.enums.TorchMode
import com.kashif.folar.plugins.CameraPlugin
import com.kashif.folar.utils.InvalidConfigurationException

interface CameraControllerBuilder {
    fun setFlashMode(flashMode: FlashMode): CameraControllerBuilder
    fun setCameraLens(cameraLens: CameraLens): CameraControllerBuilder
    fun setPreferredCameraDeviceType(deviceType: CameraDeviceType): CameraControllerBuilder
    fun setTorchMode(torchMode: TorchMode): CameraControllerBuilder
    fun setQualityPrioritization(prioritization: QualityPrioritization): CameraControllerBuilder
    fun setReturnFilePath(returnFilePath: Boolean): CameraControllerBuilder
    fun setAspectRatio(aspectRatio: AspectRatio): CameraControllerBuilder
    fun setResolution(width: Int, height: Int): CameraControllerBuilder
    fun setImageFormat(imageFormat: ImageFormat): CameraControllerBuilder
    fun setDirectory(directory: Directory): CameraControllerBuilder
    fun addPlugin(plugin: CameraPlugin): CameraControllerBuilder
    fun build(): CameraController
}

fun createCameraControllerBuilder(
    context: Any,
    lifecycleOwner: Any
): CameraControllerBuilder {
    return AndroidCameraControllerBuilder(context as Context, lifecycleOwner as LifecycleOwner)
}

class AndroidCameraControllerBuilder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : CameraControllerBuilder {

    private var flashMode: FlashMode = FlashMode.OFF
    private var cameraLens: CameraLens = CameraLens.BACK
    private var imageFormat: ImageFormat? = null
    private var directory: Directory? = null
    private var torchMode: TorchMode = TorchMode.AUTO
    private var qualityPriority: QualityPrioritization = QualityPrioritization.NONE
    private var cameraDeviceType: CameraDeviceType = CameraDeviceType.DEFAULT
    private var returnFilePath: Boolean = false
    private var aspectRatio: AspectRatio = AspectRatio.RATIO_4_3
    private var targetResolution: Pair<Int, Int>? = null
    private val plugins = mutableListOf<CameraPlugin>()

    override fun setFlashMode(flashMode: FlashMode): CameraControllerBuilder {
        this.flashMode = flashMode
        return this
    }

    override fun setCameraLens(cameraLens: CameraLens): CameraControllerBuilder {
        this.cameraLens = cameraLens
        return this
    }
    
    override fun setPreferredCameraDeviceType(deviceType: CameraDeviceType): CameraControllerBuilder {
        this.cameraDeviceType = deviceType
        return this
    }

    override fun setTorchMode(torchMode: TorchMode): CameraControllerBuilder {
        this.torchMode = torchMode
        return this
    }

    override fun setQualityPrioritization(prioritization: QualityPrioritization): CameraControllerBuilder {
        this.qualityPriority = prioritization
        return this
    }

    override fun setReturnFilePath(returnFilePath: Boolean): CameraControllerBuilder {
        this.returnFilePath = returnFilePath
        return this
    }

    override fun setAspectRatio(aspectRatio: AspectRatio): CameraControllerBuilder {
        this.aspectRatio = aspectRatio
        return this
    }

    override fun setResolution(width: Int, height: Int): CameraControllerBuilder {
        this.targetResolution = width to height
        return this
    }

    override fun setImageFormat(imageFormat: ImageFormat): CameraControllerBuilder {
        this.imageFormat = imageFormat
        return this
    }

    override fun setDirectory(directory: Directory): CameraControllerBuilder {
        this.directory = directory
        return this
    }

    override fun addPlugin(plugin: CameraPlugin): CameraControllerBuilder {
        plugins.add(plugin)
        return this
    }

    override fun build(): CameraController {
        val format = imageFormat ?: throw InvalidConfigurationException("ImageFormat must be set.")
        val dir = directory ?: throw InvalidConfigurationException("Directory must be set.")

        val cameraController = CameraController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            flashMode = flashMode,
            cameraLens = cameraLens,
            imageFormat = format,
            directory = dir,
            plugins = plugins,
            torchMode = torchMode,
            qualityPriority = qualityPriority,
            cameraDeviceType = cameraDeviceType,
            returnFilePath = returnFilePath,
            aspectRatio = aspectRatio,
            targetResolution = targetResolution
        )
        plugins.forEach {
            it.initialize(cameraController)
        }

        return cameraController
    }
}
