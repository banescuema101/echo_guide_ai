package com.example.myapplication.ui.home

import android.annotation.SuppressLint
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.camera.view.PreviewView
import com.example.myapplication.ai.VisionPipeline
import com.example.myapplication.state.LightState
import java.util.concurrent.Executors

class CameraManager(
    private val fragment: Fragment,
    private val previewView: PreviewView,
    private val onLightDetected: (LightState) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    @SuppressLint("UnsafeOptInUsageError")
    fun startCamera() {
        Log.d("CameraManager", "Pornim camera...")

        val context = fragment.requireContext()
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            cameraProvider = future.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(analysisExecutor) { image ->

                Log.d("CameraManager", "Frame primit")

                try {
                    val state = VisionPipeline.process(image)

                    Handler(Looper.getMainLooper()).post {
                        onLightDetected(state)
                    }
                } catch (e: Exception) {
                    Log.e("CameraManager", "Eroare în analyzer: ${e.message}")
                } finally {
                    try {
                        image.close()
                        Log.d("CameraManager", "Frame închis OK")
                    } catch (e: Exception) {
                        Log.e("CameraManager", "CRASH la image.close(): ${e.message}")
                    }
                }
            }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(fragment, selector, preview, analyzer)

            Log.d("CameraManager", "Camera pornită cu succes.")

        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        Log.d("CameraManager", "Oprim camera...")
        cameraProvider?.unbindAll()
    }
}
