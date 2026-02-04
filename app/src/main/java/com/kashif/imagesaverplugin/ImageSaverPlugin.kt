package com.kashif.imagesaverplugin

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.kashif.folar.enums.Directory
import com.kashif.folar.enums.ImageFormat
import com.kashif.folar.plugins.CameraPlugin
import com.kashif.folar.state.FolarStateHolder
import com.kashif.folar.state.FolarPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Configuration for the ImageSaverPlugin.
 */
@Immutable
data class ImageSaverConfig(
    val isAutoSave: Boolean = false,
    val prefix: String? = null,
    val directory: Directory = Directory.PICTURES,
    val customFolderName: String? = null,
    val imageFormat : ImageFormat= ImageFormat.JPEG
)

/**
 * Abstract plugin for saving captured images to device storage.
 */
@Stable
abstract class ImageSaverPlugin(
    val config: ImageSaverConfig
) : CameraPlugin, FolarPlugin {

    private var stateHolder: FolarStateHolder? = null

    abstract suspend fun saveImage(byteArray: ByteArray, imageName: String? = null): String?

    override fun initialize(cameraController: com.kashif.folar.controller.CameraController) {
        if (config.isAutoSave) {
            cameraController.addImageCaptureListener { byteArray ->
                CoroutineScope(Dispatchers.IO).launch {
                    val imageName = config.prefix?.let { "Folar" }
                    saveImage(byteArray, imageName)
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override fun onAttach(stateHolder: FolarStateHolder) {
        this.stateHolder = stateHolder

        if (config.isAutoSave) {
            stateHolder.pluginScope.launch {
                val controller = stateHolder.getReadyCameraController()
                controller?.addImageCaptureListener { byteArray ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val imageName = config.prefix?.let { "${it}_${Clock.System.now().toEpochMilliseconds()}" }
                        saveImage(byteArray, imageName)
                    }
                }
            }
        }
    }

    override fun onDetach() {
        stateHolder = null
    }

    abstract fun getByteArrayFrom(path: String): ByteArray
}

@Composable
fun rememberImageSaverPlugin(
    config: ImageSaverConfig,
    context: PlatformContext = LocalPlatformContext.current
): ImageSaverPlugin {
    return remember(config) {
        createPlatformImageSaverPlugin(context, config)
    }
}

/**
 * Android-specific implementation of [ImageSaverPlugin] using Scoped Storage.
 */
class AndroidImageSaverPlugin(
    private val context: Context,
    config: ImageSaverConfig
) : ImageSaverPlugin(config) {

    override suspend fun saveImage(byteArray: ByteArray, imageName: String?): String? {
        return withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = imageName ?: "IMG_$timeStamp"
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    "$fileName.${config.imageFormat.extension}"
                )
                put(MediaStore.MediaColumns.MIME_TYPE, config.imageFormat.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val basePath = when (config.directory) {
                        Directory.PICTURES -> "Pictures"
                        Directory.DCIM -> "DCIM"
                        Directory.DOCUMENTS -> "Documents"
                    }
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "$basePath/${config.customFolderName ?: "Folar"}"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            try {
                val imageUri = resolver.insert(collection, contentValues)
                    ?: throw IOException("Failed to create new MediaStore record.")

                resolver.openOutputStream(imageUri).use { outputStream ->
                    if (outputStream == null) throw IOException("Failed to get output stream.")
                    outputStream.write(byteArray)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }

                println("Image saved successfully at URI: $imageUri")
                imageUri.toString()
            } catch (e: IOException) {
                e.printStackTrace()
                println("Failed to save image: ${e.message}")
                null
            }
        }
    }

    override fun getByteArrayFrom(path: String): ByteArray {
        try {
            val uri = Uri.parse(path)
            return context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: throw IOException("Failed to open input stream")
        } catch (e: Exception) {
            throw IOException("Failed to read image from URI: $path", e)
        }
    }
}

fun createPlatformImageSaverPlugin(
    context: PlatformContext,
    config: ImageSaverConfig
): ImageSaverPlugin {
    return AndroidImageSaverPlugin(context, config)
}
