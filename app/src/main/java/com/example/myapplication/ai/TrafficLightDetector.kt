package com.example.myapplication.ai

import android.graphics.Bitmap
import com.example.myapplication.state.LightState

object TrafficLightDetector {

    // Dummy detector pentru hackathon — întoarce mereu NONE
    fun detect(bitmap: Bitmap): LightState {
        // TODO: aici puneți YOLOv5 în DEV3
        return LightState.NONE
    }
}
