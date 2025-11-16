package com.example.myapplication.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.myapplication.databinding.FragmentHomeBinding
import com.example.myapplication.state.AppState
import com.example.myapplication.state.LightState
import com.example.myapplication.utils.Permissions
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.example.myapplication.ai.LocationHelper
import com.example.myapplication.ai.VisionPipeline

import com.example.myapplication.state.ObstacleState

import org.json.JSONObject

class HomeFragment : Fragment(), SensorEventListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var tts: TextToSpeech? = null
    private lateinit var cameraManager: CameraManager

    private var appState: AppState = AppState.IDLE

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepsSinceStart: Int = 0
    private val targetSteps = 14

    // --- VOICEOVER STATE MANAGEMENT ---
    private var hasAnnouncedTrafficLight = false
    private var lastSpokenLightState = LightState.NONE
    private var lastLightStateTimestamp = 0L
    private val lightStateCooldownMs = 5000L
    private var lastPresenceAnnounceTime = 0L
    private val presenceCooldownMs = 4000L  // 4 secunde anti-spam


    private lateinit var locationHelper: LocationHelper
    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null


    data class RouteStep(
        val lat: Double,
        val lon: Double,
        val instruction: String
    )

    private var routeLoaded = false
    private var routeSteps: List<RouteStep> = emptyList()
    private var currentStepIndex = 0

    private val lastStates = ArrayDeque<LightState>()
    private val smoothingSize = 5

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun handleLocationUpdate(lat: Double, lon: Double) {
        Log.d("NAV", "Locație actualizată: $lat, $lon")

        lastLatitude = lat
        lastLongitude = lon
        if (!routeLoaded) return

        val nextStep = routeSteps[currentStepIndex]

        val stepLat = nextStep.lat
        val stepLon = nextStep.lon

        val dist = distanceInMeters(lat, lon, stepLat, stepLon)

        if (dist < 10) {
            speak("Ai ajuns la pasul ${currentStepIndex + 1}")
            currentStepIndex++
            return
        }

        // verificăm dacă utilizatorul a deviat prea mult
        if (dist > 30) {
            speak("Ai deviat de la traseu, recalculez ruta.")
            recalcRoute(lat, lon)
        }
    }

    private fun distanceInMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0 // raza Pământului în metri
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) *
                Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    private fun recalcRoute(currentLat: Double, currentLon: Double) {

        Log.d("NAV", "Recalculez ruta din: $currentLat, $currentLon")

        // Destinație exemplu – Palatul Parlamentului
        val destLat = 47.4275
        val destLon = 26.0875

        // AICI VINE URL-ul CORECT
        val urlStr =
            "https://router.project-osrm.org/route/v1/foot/" +
                    "$currentLon,$currentLat;" +
                    "$destLon,$destLat" +
                    "?overview=false&steps=true"
        Thread {
            try {
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connect()

                val stream = conn.inputStream.bufferedReader().use { it.readText() }

                val json = org.json.JSONObject(stream)
                val routes = json.getJSONArray("routes")
                val legs = routes.getJSONObject(0).getJSONArray("legs")
                val steps = legs.getJSONObject(0).getJSONArray("steps")

                val newSteps = mutableListOf<RouteStep>()

                for (i in 0 until steps.length()) {
                    val stepObj = steps.getJSONObject(i)
                    val maneuver = stepObj.getJSONObject("maneuver")
                    val loc = maneuver.getJSONArray("location")

                    val lon = loc.getDouble(0)
                    val lat = loc.getDouble(1)

                    // 🔥 AICI e modificarea importantă:
                    val instruction =
                        if (stepObj.has("instruction")) {
                            stepObj.getString("instruction")
                        } else {
                            val type = maneuver.optString("type", "")
                            val modifier = maneuver.optString("modifier", "")

                            when (type) {
                                "turn" -> "Fă o întoarcere spre $modifier"
                                "depart" -> "Pornire spre $modifier"
                                "arrive" -> "Ai ajuns la destinație"
                                "new name" -> "Continuă pe strada următoare"
                                else -> "Continuați înainte"
                            }
                        }

                    newSteps += RouteStep(lat, lon, instruction)
                }

                requireActivity().runOnUiThread {
                    routeSteps = newSteps
                    routeLoaded = true
                    currentStepIndex = 0
                    speak("Ruta a fost recalculată.")
                    Log.d("NAV", "Rută recalculată – ${newSteps.size} pași.")
                }

            } catch (e: Exception) {
                Log.e("NAV", "Eroare recalculare rută: ${e.message}")
            }
        }.start()

    }


    private fun handleLocation(lat: Double, lon: Double) {
        Log.d("HomeFragment", "Locație primită: lat=$lat  lon=$lon")

        lastLatitude = lat
        lastLongitude = lon

        binding.statusText.text = "Locație: $lat , $lon"
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        locationHelper = LocationHelper(requireContext())

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        setupTts()


        VisionPipeline.init(requireContext())
//
//        VisionPipeline.setTrafficLightPresenceListener { found ->
//            if (found) {
//                val now = System.currentTimeMillis()
//
//                if (now - lastPresenceAnnounceTime > presenceCooldownMs) {
//                    speak("Am detectat un semafor.")
//                    lastPresenceAnnounceTime = now
//                }
//            }
//        }
// 🔥 AICI adaugi callback-ul pentru culoare:
        VisionPipeline.setTrafficLightColorListener { color ->
            handleTrafficLightState(color)
        }


        cameraManager = CameraManager(
            fragment = this,
            previewView = binding.cameraPreview,
            onLightDetected = { state: LightState ->
                Log.d("HomeFragment", "LightState din AI = $state")
                handleTrafficLightState(state)
            },
            onObstacleDetected = { obstacleState: ObstacleState ->
                Log.d("HomeFragment", "ObstacleState din AI = $obstacleState")

                // aici poți adăuga și TTS, de exemplu:
                // if (obstacleState == ObstacleState.OBSTACLE_AHEAD) {
                //     speak("Atenție, obstacol în față.")
                // }
            }
        )

        binding.startButton.setOnClickListener {
            onStartAssistantClicked()
            binding.startButton.visibility = View.GONE}

        binding.locationButton.setOnClickListener {
            speakCurrentLocation()
        }

        updateStatusText()
    }


    private fun setupTts() {
        tts = TextToSpeech(requireContext()) {
            if (it == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ro", "RO")
            }
        }
    }

    private fun onStartAssistantClicked() {
        Log.d("HomeFragment", "Start button apăsat")

        if (!Permissions.allRequiredPermissionsGranted(requireContext())) {
            Permissions.requestAllPermissions(this)
            return
        }

        // PORNIM Locația doar la Start
        locationHelper.getLocation { lat, lon ->
            handleLocation(lat, lon)
            if (!routeLoaded) {
                recalcRoute(lat, lon)   // se calculează doar PRIMA DATĂ
            }
        }

        cameraManager.startCamera()

        appState = AppState.WALKING
        stepsSinceStart = 0
        hasAnnouncedTrafficLight = false
        lastSpokenLightState = LightState.NONE
        updateStatusText()

        speak("Ghidarea a început. Mergi drept aproximativ zece metri.")
    }

    private fun updateStatusText() {
        binding.statusText.text = appState.toString()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
    }

    private fun speakCurrentLocation() {
        val lat = lastLatitude
        val lon = lastLongitude

        // Dacă încă nu avem nicio locație primită
        if (lat == null || lon == null) {
            speak("Locația nu este încă disponibilă. Te rog să încerci din nou peste câteva secunde.")
            return
        }

        // Voiceover coordonate
        val text = "Latitudine $lat, longitudine $lon."
        speak(text)
    }

    // --- STEP SENSOR ---
    override fun onResume() {
        super.onResume()
        VisionPipeline.init(requireContext())

        locationHelper.getLocation { lat, lon ->
            handleLocationUpdate(lat, lon)
//            recalcRoute(lat, lon)
        }

        stepSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }


    override fun onPause() {
        super.onPause()
        locationHelper.stop()
        cameraManager.stop()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_DETECTOR) return

        if (appState == AppState.WALKING) {
            stepsSinceStart++
            Log.d("HomeFragment", "Step detected: $stepsSinceStart/$targetSteps")

            if (stepsSinceStart >= targetSteps) {
                appState = AppState.CHECKING_TRAFFIC_LIGHT
                updateStatusText()
                speak("Ai mers aproximativ zece metri. Caut semaforul din față.")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- TRAFFIC LIGHT LOGIC ---
    fun handleTrafficLightState(state: LightState) {

        // 1. Add to buffer
        lastStates.addLast(state)
        if (lastStates.size > smoothingSize) lastStates.removeFirst()

        // 2. Find dominant state
        val stableState = lastStates
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }!!.key

        // 3. If stable state is NONE, do nothing
        if (stableState == LightState.NONE) return

        // 4. Announce "semafor detectat" only once
        if (!hasAnnouncedTrafficLight) {
            speak("Am detectat un semafor.")
            hasAnnouncedTrafficLight = true
        }

        val now = System.currentTimeMillis()

        // 5. Cooldown
        if (stableState == lastSpokenLightState &&
            now - lastLightStateTimestamp < lightStateCooldownMs) {
            return
        }

        lastSpokenLightState = stableState
        lastLightStateTimestamp = now

        // 6. Speak color
        when (stableState) {
            LightState.RED -> speak("Semafor roșu. Așteaptă.")

            LightState.GREEN -> {
                speak("Semafor verde. Poți traversa.")
                appState = AppState.DONE
                updateStatusText()
            }

            else -> Unit
        }
    }



    override fun onDestroyView() {
        tts?.shutdown()
        _binding = null
        super.onDestroyView()
    }
}