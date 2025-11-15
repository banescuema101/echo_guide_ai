package com.example.myapplication.ai

import androidx.camera.core.ImageProxy
import com.example.myapplication.state.LightState
import com.example.myapplication.utils.ImageUtils

object VisionPipeline {

    fun process(image: ImageProxy, callback: (LightState) -> Unit) {

        val bitmap = ImageUtils.imageProxyToBitmap(image)

        // detect semafor
        val lightState = TrafficLightDetector.detect(bitmap)

        if (lightState != LightState.NONE) {
            callback(lightState)
        }

        // optional depth (pentru mai târziu)
        // val hole = DepthEstimator.detectHole(bitmap)

        image.close()
    }
}
