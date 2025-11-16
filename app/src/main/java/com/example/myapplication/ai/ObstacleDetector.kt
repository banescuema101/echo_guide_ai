package com.example.myapplication.ai

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.example.myapplication.state.ObstacleState
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class ObstacleDetector {

    // configurăm detectorul: imagini individuale, multiple obiecte
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
        .enableMultipleObjects()
        .build()

    private val detector = ObjectDetection.getClient(options)

    /**
     * ANALIZĂ SINCRONĂ
     * - primește un Bitmap (același pe care îl dai la YOLO)
     * - întoarce direct un ObstacleState
     */
    fun detect(bitmap: Bitmap): ObstacleState {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            // blocăm threadul de cameră până termină MLKit (e ok, nu e pe UI thread)
            val objects: List<DetectedObject> = Tasks.await(detector.process(image))
            interpret(objects, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            Log.e("ObstacleDetector", "MLKit error: ${e.message}")
            ObstacleState.NONE
        }
    }

    /**
     * Stabilim dacă avem un obstacol relevant în față
     * - luăm în calcul doar obiectele suficient de MARI
     * - doar din zona centrală-jos a imaginii ("zona de pericol")
     */
    private fun interpret(
        objects: List<DetectedObject>,
        width: Int,
        height: Int
    ): ObstacleState {
        if (objects.isEmpty()) return ObstacleState.NONE

        // zonă de pericol: partea de jos, pe centru
        val dangerRect = Rect(
            (width * 0.25f).toInt(),   // stânga
            (height * 0.5f).toInt(),  // sus
            (width * 0.75f).toInt(),  // dreapta
            height                    // jos
        )

        val minArea = width * height * 0.02f // 2% din imagine, ajustezi dacă vrei

        for (obj in objects) {
            val box = obj.boundingBox
            val area = box.width() * box.height()

            // ignorăm chestii mici (departe sau nesemnificative)
            if (area < minArea) continue

            // ne interesează doar ce intră în zona de pericol
            if (!Rect.intersects(dangerRect, box)) continue

            Log.d("OBSTACLE", "Obstacle box=$box area=$area")
            return ObstacleState.OBSTACLE_AHEAD
        }

        return ObstacleState.NONE
    }
}
