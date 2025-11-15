package com.example.myapplication.ui.home

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.camera.view.PreviewView
import com.example.myapplication.ai.VisionPipeline
import com.example.myapplication.state.LightState

class CameraManager(
    private val fragment: Fragment,
    private val previewView: PreviewView,
    private val onLightDetected: (LightState) -> Unit
) {

    private var cameraProvider: ProcessCameraProvider? = null

    @SuppressLint("UnsafeOptInUsageError")
    fun startCamera() {
        val context = fragment.requireContext()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analyzer.setAnalyzer(
                ContextCompat.getMainExecutor(context)
            ) { image ->
                VisionPipeline.process(image) { state ->
                    onLightDetected(state)
                }

                // OBLIGATORIU:
                image.close()
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                fragment, cameraSelector, preview, analyzer
            )

        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraProvider?.unbindAll()
    }
}
