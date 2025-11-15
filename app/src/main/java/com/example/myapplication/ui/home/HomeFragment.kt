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
import android.widget.Toast

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

    private var lastSpokenLightState = LightState.NONE
    private var lastLightStateTimestamp = 0L
    private val lightStateCooldownMs = 5000L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("HomeFragment", "onViewCreated")

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        setupTts()

        cameraManager = CameraManager(
            fragment = this,
            previewView = binding.cameraPreview
        ) { state ->
            Log.d("HomeFragment", "State primit din AI = $state")
            handleTrafficLightState(state)
        }

        binding.startButton.setOnClickListener { onStartAssistantClicked() }

        updateStatusText()
    }

    private fun setupTts() {
        tts = TextToSpeech(requireContext()) {
            if (it == TextToSpeech.SUCCESS) tts?.language = Locale("ro", "RO")
        }
    }

    private fun onStartAssistantClicked() {
        Log.d("HomeFragment", "Start button apăsat")

        if (!Permissions.allRequiredPermissionsGranted(requireContext())) {
            Log.e("HomeFragment", "Permisiuni lipsă")
            Permissions.requestAllPermissions(this)
            return
        }

        cameraManager.startCamera()

        appState = AppState.WALKING
        stepsSinceStart = 0
        updateStatusText()

        speak("Ghidarea a început. Mergi drept aproximativ zece metri.")
    }

    private fun updateStatusText() {
        binding.statusText.text = appState.toString()
    }

    private fun speak(text: String) {
        Log.d("HomeFragment", "TTS: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        cameraManager.stop()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_DETECTOR) return

        if (appState == AppState.WALKING) {
            stepsSinceStart++
            Log.d("HomeFragment", "Step detected: $stepsSinceStart/${targetSteps}")

            if (stepsSinceStart >= targetSteps) {
                appState = AppState.CHECKING_TRAFFIC_LIGHT
                updateStatusText()
                speak("Ai mers aproximativ zece metri. Caut semaforul din față.")
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("HomeFragment", "onAccuracyChanged: $accuracy")
    }

    fun handleTrafficLightState(state: LightState) {
        if (appState != AppState.CHECKING_TRAFFIC_LIGHT) return

        val now = System.currentTimeMillis()
        if (state == lastSpokenLightState && now - lastLightStateTimestamp < lightStateCooldownMs) return

        lastSpokenLightState = state
        lastLightStateTimestamp = now

        when (state) {
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
