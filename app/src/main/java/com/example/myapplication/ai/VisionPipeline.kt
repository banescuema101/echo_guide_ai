package com.example.myapplication.ai

import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.myapplication.state.LightState
import com.example.myapplication.utils.ImageUtils

object VisionPipeline {

    fun process(image: ImageProxy): LightState {
        Log.d("VisionPipeline", "Procesăm frame: format=${image.format} rot=${image.imageInfo.rotationDegrees}")

        val bitmap = try {
            ImageUtils.imageProxyToBitmap(image)
        } catch (e: Exception) {
            Log.e("VisionPipeline", "Eroare conversie bitmap: ${e.message}")
            return LightState.NONE
        }

        val state = try {
            TrafficLightDetector.detect(bitmap)
        } catch (e: Exception) {
            Log.e("VisionPipeline", "Eroare la detectie: ${e.message}")
            LightState.NONE
        }

        Log.d("VisionPipeline", "Rezultat detectat: $state")
        return state
    }
}
