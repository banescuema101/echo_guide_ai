package com.example.myapplication.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.myapplication.state.LightState
import kotlin.math.abs

object ColorAnalyzer {

    fun analyzeLightColor(crop: Bitmap): LightState {
        val w = crop.width
        val h = crop.height

        // culoarea -> la inceput log
        Log.d("COLOR", "Crop size = ${crop.width} x ${crop.height}")


        if (w <= 0 || h <= 0) return LightState.NONE

        // împărțim în 3 zone egale
        val zoneHeight = h / 3

        var redCount = 0
        var yellowCount = 0
        var greenCount = 0

        // threshold pentru comparația culorilor
        fun isNear(r: Int, g: Int, b: Int, tr: Int, tg: Int, tb: Int): Boolean {
            val tolerance = 60
            return abs(r - tr) < tolerance &&
                    abs(g - tg) < tolerance &&
                    abs(b - tb) < tolerance
        }

        for (y in 0 until h step 2) {  // sampling mai rapid
            for (x in (w * 0.3).toInt() until (w * 0.7).toInt() step 2) {

                val px = crop.getPixel(x, y)
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)

                when (y) {
                    in 0 until zoneHeight -> {
                        // zona ROȘU
                        if (isNear(r, g, b, 255, 0, 0)) redCount++
                    }
                    in zoneHeight until zoneHeight * 2 -> {
                        // zona GALBEN
                        if (isNear(r, g, b, 255, 255, 0)) yellowCount++
                    }
                    else -> {
                        // zona VERDE
                        if (isNear(r, g, b, 0, 255, 0)) greenCount++
                    }
                }
            }
        }

        Log.d("COLOR", "Red=$redCount  Yellow=$yellowCount  Green=$greenCount")

        val maxVal = maxOf(redCount, yellowCount, greenCount)
        if (maxVal < 50) return LightState.NONE   // dacă e prea slab, înseamnă că semaforul nu e clar

        return when (maxVal) {
            redCount -> LightState.RED
            greenCount -> LightState.GREEN
            else -> LightState.NONE
        }
    }
}
