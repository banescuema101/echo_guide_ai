package com.example.myapplication.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.myapplication.state.LightState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object TrafficLightApi {

    private const val API_KEY = "Gzx1DRjksJQYkx6ckcbJ"
    private const val WORKFLOW_NAME = "custom-workflow"

    private val client = OkHttpClient()

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun detect(bitmap: Bitmap): LightState {
        return try {

            // 1) conversia imaginii in base64
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            val jpegBytes = baos.toByteArray()
            val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            // 2) json-ul
            val json = """
            {
              "api_key": "$API_KEY",
              "inputs": {
                "image": {
                  "type": "base64",
                  "value": "$base64Image"
                }
              }
            }
            """.trimIndent()

            val requestBody = json.toRequestBody("application/json".toMediaType())

            val url = "https://serverless.roboflow.com/emaproject/workflows/$WORKFLOW_NAME"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            // 3) Executăm request-ul
            val response = client.newCall(request).execute()
            val responseStr = response.body?.string() ?: return LightState.NONE

            Log.d("TRAFFIC_API", "Workflow Response: $responseStr")

            // ----- DE AICI ÎNCEPE PARSAREA JSON-ULUI CORESPUNZĂTOR -----

            val root = JSONObject(responseStr)

// Extragem outputs[0]
            val outputs = root.getJSONArray("outputs")
            if (outputs.length() == 0) return LightState.NONE

            val outputObj = outputs.getJSONObject(0)

// predictions
            val predictionsObj = outputObj
                .getJSONObject("predictions")
                .getJSONArray("predictions")

            if (predictionsObj.length() == 0) return LightState.NONE

// Alegem cea mai bună predicție
            var best = predictionsObj.getJSONObject(0)
            var bestConf = best.getDouble("confidence")

            for (i in 1 until predictionsObj.length()) {
                val obj = predictionsObj.getJSONObject(i)
                val conf = obj.getDouble("confidence")
                if (conf > bestConf) {
                    best = obj
                    bestConf = conf
                }
            }

            // Extragem coordonatele
            val x = best.getDouble("x")
            val y = best.getDouble("y")
            val w = best.getDouble("width")
            val h = best.getDouble("height")

            Log.d("BBOX", "JSON BOX → x=$x  y=$y  w=$w  h=$h")

            val left = max(0, (x - w / 2).toInt())
            val top = max(0, (y - h / 2).toInt())
            val right = min(bitmap.width, (x + w / 2).toInt())
            val bottom = min(bitmap.height, (y + h / 2).toInt())
            Log.d(
                "BBOX",
                "CROP COORDS → left=$left  top=$top  right=$right  bottom=$bottom  (bitmap=${bitmap.width}x${bitmap.height})"
            )



            Log.d("DEBUG_BOX", """
                BBOX:
                x=$x
                y=$y
                width=$w
                height=$h
                ImageSize = ${bitmap.width} x ${bitmap.height}
                CROP = left=$left top=$top right=$right bottom=$bottom
            """.trimIndent())

            if (right <= left || bottom <= top) return LightState.NONE

            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)

            try {
                val file = File(appContext.cacheDir, "debug_crop_${System.currentTimeMillis()}.jpg")
                val fos = FileOutputStream(file)
                crop.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                fos.close()

                Log.d("DEBUG_CROP", "Crop salvat: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e("DEBUG_CROP", "Eroare la salvare crop: ${e.message}")
            }

            return ColorAnalyzer.analyzeLightColor(crop)


        } catch (e: Exception) {
            Log.e("TRAFFIC_API", "Error=${e.message}")
            LightState.NONE
        }
    }
}
