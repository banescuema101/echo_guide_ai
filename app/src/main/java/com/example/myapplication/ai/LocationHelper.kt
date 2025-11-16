package com.example.myapplication.ai

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

class LocationHelper(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    private var callback: ((Double, Double) -> Unit)? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            callback?.invoke(loc.latitude, loc.longitude)
        }
    }

    @SuppressLint("MissingPermission")
    fun getLocation(onLocation: (Double, Double) -> Unit) {
        this.callback = onLocation

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5000L
        ).build()

        client.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }


    fun stop() {
        client.removeLocationUpdates(locationCallback)
    }
}
