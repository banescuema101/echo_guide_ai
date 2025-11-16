package com.example.myapplication.utils

import android.Manifest
import android.content.Context
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object Permissions {

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun allRequiredPermissionsGranted(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestAllPermissions(fragment: Fragment) {
        ActivityCompat.requestPermissions(
            fragment.requireActivity(),
            REQUIRED_PERMISSIONS,
            10
        )
    }
}
