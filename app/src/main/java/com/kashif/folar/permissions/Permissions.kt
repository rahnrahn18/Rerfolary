package com.kashif.folar.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

interface Permissions {
    fun hasCameraPermission(): Boolean
    fun hasStoragePermission(): Boolean
    fun hasAudioPermission(): Boolean
    @Composable
    fun RequestCameraPermission(onGranted: () -> Unit, onDenied: () -> Unit)
    @Composable
    fun RequestStoragePermission(onGranted: () -> Unit, onDenied: () -> Unit)
}

/**
 * Android-specific implementation of [Permissions] using Activity Result Contracts.
 * Simplified internal implementation that removes redundant permission checks.
 */
@Composable
fun providePermissions(): Permissions {
    val context = LocalContext.current

    return remember(context) {
        object : Permissions {
            override fun hasCameraPermission(): Boolean =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == 
                PackageManager.PERMISSION_GRANTED

            override fun hasStoragePermission(): Boolean =
                Build.VERSION.SDK_INT >= 32 || 
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                PackageManager.PERMISSION_GRANTED

            override fun hasAudioPermission(): Boolean =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

            @Composable
            override fun RequestCameraPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
                if (hasCameraPermission()) {
                    LaunchedEffect(Unit) { onGranted() }
                } else {
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted -> if (isGranted) onGranted() else onDenied() }
                    
                    LaunchedEffect(Unit) {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            @Composable
            override fun RequestStoragePermission(onGranted: () -> Unit, onDenied: () -> Unit) {
                if (hasStoragePermission()) {
                    LaunchedEffect(Unit) { onGranted() }
                } else {
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted -> if (isGranted) onGranted() else onDenied() }
                    
                    LaunchedEffect(Unit) {
                        launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
            }
        }
    }
}