package com.example.myapplication.ai

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.myapplication.state.LightState
import com.example.myapplication.state.ObstacleState

object VisionPipeline {

    var detector: YoloV5OnnxDetector? = null
    private var appContext: Context? = null   // 🔥 salvăm contextul
    // --- CALLBACK-URI ---
    private var onTrafficLightPresence: ((Boolean) -> Unit)? = null
    private var onTrafficLightColor: ((LightState) -> Unit)? = null
    var obstacleDetector: ObstacleDetector? = null
    private var frameIndex: Int = 0

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
        obstacleDetector = ObstacleDetector()
    }

    fun process(image: ImageProxy): Pair<LightState, ObstacleState> {

        val d = detector
        val o = obstacleDetector

        if (d == null || appContext == null) {
            Log.e("VisionPipeline", "YOLO NOT INITIALIZED!")
            image.close()
            return LightState.NONE to ObstacleState.NONE
        }

        val bitmap = try {
            YuvToJpegConverter.yuv420ToBitmap(image, appContext!!)
        } catch (e: Exception) {
            Log.e("VisionPipeline", "Bitmap error: ${e.message}")
            image.close()
            return LightState.NONE to ObstacleState.NONE
        }

        // Închidem frame-ul după ce am scos bitmap-ul
        image.close()

        // 1) SEMAFOR
        val lightState = try {
            d.detectTrafficLight(bitmap)
        } catch (e: Exception) {
            Log.e("VisionPipeline", "YOLO error: ${e.message}")
            LightState.NONE
        }

        // 2) OBSTACOL
        var obstacleState = ObstacleState.NONE
        if (o != null) {
            // ca să nu omorâm performanța, facem din 3 în 3 frame-uri
            obstacleState = if (frameIndex++ % 3 == 0) {
                o.detect(bitmap)
            } else {
                ObstacleState.NONE
            }
            Log.d("OBSTACLE", "state=$obstacleState")
        }

        return lightState to obstacleState
    }

}