package com.example.myapplication.ai

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.myapplication.state.LightState

object VisionPipeline {

    var detector: YoloV5OnnxDetector? = null
    private var appContext: Context? = null   // 🔥 salvăm contextul
    // --- CALLBACK-URI ---
    private var onTrafficLightPresence: ((Boolean) -> Unit)? = null
    private var onTrafficLightColor: ((LightState) -> Unit)? = null

    fun setTrafficLightPresenceListener(cb: (Boolean) -> Unit) {
        onTrafficLightPresence = cb
    }

    fun setTrafficLightColorListener(cb: (LightState) -> Unit) {
        onTrafficLightColor = cb
    }

    fun notifyTrafficLightPresence(found: Boolean) {
        onTrafficLightPresence?.invoke(found)
    }

    fun notifyTrafficLightColor(color: LightState) {
        onTrafficLightColor?.invoke(color)
    }
    fun init(context: Context) {
        Log.d("VisionPipeline", "YOLO INIT...")
        appContext = context.applicationContext
        detector = YoloV5OnnxDetector(context)
    }

    fun process(image: ImageProxy): LightState {

        val d = detector
        if (d == null || appContext == null) {
            Log.e("VisionPipeline", "YOLO NOT INITIALIZED!")
            image.close()
            return LightState.NONE
        }

        val bitmap = try {
            YuvToJpegConverter.yuv420ToBitmap(image, appContext!!)
        } catch (e: Exception) {
            Log.e("VisionPipeline", "Bitmap error: ${e.message}")
            image.close()
            return LightState.NONE
        }

        // Închidem frame-ul înainte de YOLO
        image.close()

        return try {
            d.detectTrafficLight(bitmap)
        } catch (e: Exception) {
            Log.e("VisionPipeline", "YOLO error: ${e.message}")
            image.close()
            LightState.NONE
        }
    }
}