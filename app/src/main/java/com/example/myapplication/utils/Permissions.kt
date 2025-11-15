package com.example.myapplication.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object Permissions {

    const val REQUEST_CODE_PERMISSIONS = 1001

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasActivityRecognitionPermission(context: Context): Boolean {
        // Pentru API < 29 nu trebuie cerută la runtime
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun allRequiredPermissionsGranted(context: Context): Boolean {
        return hasCameraPermission(context) && hasActivityRecognitionPermission(context)
    }

    fun requestAllPermissions(fragment: Fragment) {
        val permissions = mutableListOf<String>()

        if (!hasCameraPermission(fragment.requireContext())) {
            permissions += Manifest.permission.CAMERA
        }
        if (!hasActivityRecognitionPermission(fragment.requireContext()) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            permissions += Manifest.permission.ACTIVITY_RECOGNITION
        }

        if (permissions.isNotEmpty()) {
            fragment.requestPermissions(
                permissions.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }
}
