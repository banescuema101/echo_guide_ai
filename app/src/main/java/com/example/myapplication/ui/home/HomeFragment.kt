package com.example.myapplication.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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

    // TTS
    private var tts: TextToSpeech? = null

    // State
    private var appState: AppState = AppState.IDLE

    // Steps
    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepsSinceStart: Int = 0
    private val targetSteps: Int = 14   // ~10m, se poate ajusta

    // Traffic light voice throttling
    private var lastSpokenLightState: LightState = LightState.NONE
    private var lastLightStateTimestamp: Long = 0L
    private val lightStateCooldownMs = 5000L // nu repetăm același mesaj mai des de 5s

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Sensor manager
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        setupTts()

        binding.startButton.setOnClickListener {
            onStartAssistantClicked()
        }

        updateStatusText()
    }

    private fun setupTts() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ro", "RO")
            }
        }
    }

    private fun onStartAssistantClicked() {
        if (!Permissions.allRequiredPermissionsGranted(requireContext())) {
            Permissions.requestAllPermissions(this)
            return
        }

        // Reset state
        appState = AppState.WALKING
        stepsSinceStart = 0
        updateStatusText()

        speak("Ghidarea a început. Mergi drept aproximativ zece metri.")
    }

    private fun updateStatusText() {
        binding.statusText.text = when (appState) {
            AppState.IDLE -> "Idle"
            AppState.WALKING -> "Walking - counting steps"
            AppState.CHECKING_TRAFFIC_LIGHT -> "Checking traffic light"
            AppState.DONE -> "Done"
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "app_tts_id")
    }

    // ====== SensorEventListener (pași) ======

    override fun onResume() {
        super.onResume()
        stepSensor?.also { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_DETECTOR) return

        if (appState == AppState.WALKING) {
            // Fiecare eveniment STEP_DETECTOR = un pas
            stepsSinceStart += 1

            if (stepsSinceStart >= targetSteps) {
                appState = AppState.CHECKING_TRAFFIC_LIGHT
                updateStatusText()
                speak("Ai mers aproximativ zece metri. Caut semaforul din față.")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // not used
    }

    // ====== Hook pentru AI: va fi apelat de Membrul 2/3 ======

    fun handleTrafficLightState(state: LightState) {
        if (appState != AppState.CHECKING_TRAFFIC_LIGHT) return
        if (state == LightState.NONE) return

        val now = System.currentTimeMillis()

        // Nu mai spune același lucru dacă a fost zis recent
        if (state == lastSpokenLightState && now - lastLightStateTimestamp < lightStateCooldownMs) {
            return
        }

        lastSpokenLightState = state
        lastLightStateTimestamp = now

        when (state) {
            LightState.RED -> {
                speak("Semafor roșu. Așteaptă.")
            }
            LightState.GREEN -> {
                speak("Semafor verde. Poți începe să traversezi cu atenție.")
                // După ce dăm verde, putem marca state-ul ca DONE (sau lăsăm să continue)
                appState = AppState.DONE
                updateStatusText()
            }
            else -> Unit
        }
    }

    // ====== Permissions callback ======

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == Permissions.REQUEST_CODE_PERMISSIONS) {
            if (Permissions.allRequiredPermissionsGranted(requireContext())) {
                Toast.makeText(requireContext(), "Permisiuni acordate", Toast.LENGTH_SHORT).show()
                onStartAssistantClicked()
            } else {
                Toast.makeText(requireContext(), "Permisiuni necesare neacordate", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tts?.shutdown()
        tts = null
        _binding = null
    }
}
