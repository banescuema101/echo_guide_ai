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
import com.example.myapplication.state.ObstacleState
import java.util.concurrent.Executors

class CameraManager(
    private val fragment: Fragment,
    private val previewView: PreviewView,
    private val onLightDetected: (LightState) -> Unit,
    private val onObstacleDetected: (ObstacleState) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    @SuppressLint("UnsafeOptInUsageError")
    fun startCamera() {

        VisionPipeline.init(fragment.requireContext())

        val future = ProcessCameraProvider.getInstance(fragment.requireContext())

        future.addListener({
            cameraProvider = future.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            analyzer.setAnalyzer(analysisExecutor) { image ->
                // destructurăm Pair<LightState, ObstacleState>
                val (lightState, obstacleState) = VisionPipeline.process(image)

                Handler(Looper.getMainLooper()).post {
                    onLightDetected(lightState)
                    onObstacleDetected(obstacleState)
                }
            }

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                fragment,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer
            )

        }, ContextCompat.getMainExecutor(fragment.requireContext()))
    }

    fun stop() {
        cameraProvider?.unbindAll()
    }
}
