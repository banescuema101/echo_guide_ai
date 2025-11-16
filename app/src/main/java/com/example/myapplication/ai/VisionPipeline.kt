package com.example.myapplication.ai

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.example.myapplication.state.LightState
import kotlin.system.measureTimeMillis

object VisionPipeline {

    private var appContext: Context? = null
    private var lastApiCall = 0L
    private const val API_INTERVAL_MS = 1200L    // 1.2 secunde între request-uri
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
        appContext = context.applicationContext
    }

    fun process(image: ImageProxy): LightState {

        val ctx = appContext
        if (ctx == null) {
            image.close()
            return LightState.NONE
        }

        val now = System.currentTimeMillis()

        // Throttling: ignorăm frame-urile prea dese
        if (now - lastApiCall < API_INTERVAL_MS) {
            image.close()
            return LightState.NONE
        }

        val bitmap = try {
            YuvToJpegConverter.yuv420ToBitmap(image, ctx)
        } catch (e: Exception) {
            Log.e("VisionPipeline", "Bitmap error: ${e.message}")
            image.close()
            return LightState.NONE
        }

        image.close()

        lastApiCall = now

        // Trimitem doar 1 request la ~1.2 secunde
        return try {
            val time = measureTimeMillis {
                TrafficLightApi.detect(bitmap)
            }
            Log.d("VisionPipeline", "API inference time = ${time}ms")

            TrafficLightApi.detect(bitmap)
        } catch (e: Exception) {
            Log.e("VisionPipeline", "API error: ${e.message}")
            LightState.NONE
        }
    }
}
