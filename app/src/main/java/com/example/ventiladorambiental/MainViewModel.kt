package com.example.ventiladorambiental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ventiladorambiental.data.AutomationRules
import com.example.ventiladorambiental.data.ReadingRepository
import com.example.ventiladorambiental.data.Schedule
import com.example.ventiladorambiental.link.Commands
import com.example.ventiladorambiental.link.DeviceLink
import com.example.ventiladorambiental.link.LinkQuality
import com.example.ventiladorambiental.link.SecureChannel
import com.example.ventiladorambiental.link.Telemetry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado compartido por las 4 pestañas.
 *
 * Dos formas de conectarse:
 *  - Placa real (ESP8266 Wemos D1): [connectDevice] abre el enlace seguro [DeviceLink] por Wi-Fi.
 *    Las lecturas llegan desde el Arduino y las órdenes (compuerta, turbina, modo) se le envían firmadas.
 *  - Modo demostración: [connectDemo] simula lecturas para mostrar la app sin hardware.
 *
 * Cada lectura se guarda en SQLite (ReadingRepository), así el historial no se pierde al cerrar la app.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var isConnected = false
    private var isDemo = false
    private var isVentOpen = false
    private var isAutoMode = true
    private var temperature = 25
    private var humidity = 55
    private var isMovement = false
    private var fanSpeed = 60
    private var rules = AutomationRules()
    private var schedule: Schedule = Schedule.DEFAULT
    private val history = ArrayDeque<Reading>()
    private var lastUpdate: String? = null
    private var linkStatus: String? = null
    private var linkQuality: LinkQuality? = null
    private var demoJob: Job? = null
    private val linkJobs = mutableListOf<Job>()
    private var link: DeviceLink? = null
    private val readings = ReadingRepository(application)

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        updateSuccessState()
        viewModelScope.launch {
            readings.purgeOlderThan(HISTORY_DAYS)
            val saved = readings.latest(MAX_HISTORY)
            history.addAll(saved)
            saved.lastOrNull()?.let { temperature = it.temperature; humidity = it.humidity }
            updateSuccessState()
        }
    }

    // ---------- Conexión con la placa real ----------

    /** true si la clave del enlace está configurada en local.properties (ver README). */
    val isLinkConfigured: Boolean get() = SecureChannel.keyFromHex(BuildConfig.LINK_KEY_HEX) != null

    fun connectDevice() {
        if (isConnected || link != null) return
        val key = SecureChannel.keyFromHex(BuildConfig.LINK_KEY_HEX)
        if (key == null) {
            _uiState.value = MainUiState.Error("Falta la clave del enlace (aerosmart.linkKey en local.properties).")
            return
        }
        stopDemo()
        isDemo = false
        val l = DeviceLink(getApplication(), key, BuildConfig.DEVICE_HOST, BuildConfig.DEVICE_PORT, viewModelScope)
        link = l
        linkJobs += viewModelScope.launch { l.state.collect { onLinkState(it) } }
        linkJobs += viewModelScope.launch { l.messages.collect { onDeviceMessage(it) } }
        linkJobs += viewModelScope.launch { l.quality.collect { linkQuality = it; if (isConnected) updateSuccessState() } }
        _uiState.value = MainUiState.Loading
        l.connect()
    }

    private fun onLinkState(state: DeviceLink.State) {
        when (state) {
            DeviceLink.State.Connecting -> { linkStatus = "Conectando con la placa…"; _uiState.value = MainUiState.Loading; return }
            DeviceLink.State.Connected -> {
                isConnected = true
                linkStatus = null
                // Al conectar se envía el estado deseado para que la placa quede igual que la app
                link?.send(Commands.mode(isAutoMode))
                link?.send(Commands.fan(fanSpeed))
            }
            is DeviceLink.State.Reconnecting -> { isConnected = false; linkStatus = "Reconectando (${state.attempt}/6)…" }
            is DeviceLink.State.Failed -> {
                isConnected = false
                linkStatus = null
                releaseLink()
                _uiState.value = MainUiState.Error(state.message)
                return
            }
            DeviceLink.State.Idle -> { isConnected = false; linkStatus = null }
        }
        updateSuccessState()
    }

    private fun onDeviceMessage(body: String) {
        val t = Telemetry.parse(body) ?: return
        temperature = t.temperature
        humidity = t.humidity
        isMovement = t.movement
        isVentOpen = t.ventOpen
        // La turbina reporta 0 cuando la compuerta está cerrada; se conserva la velocidad elegida
        if (t.ventOpen && t.fanPercent > 0) fanSpeed = t.fanPercent
        recordReading()
        evaluateAutomation()
        updateSuccessState()
    }

    fun disconnectDevice() {
        stopDemo()
        releaseLink()
        isConnected = false
        isDemo = false
        isVentOpen = false
        isMovement = false
        lastUpdate = null
        linkStatus = null
        updateSuccessState()
    }

    private fun releaseLink() {
        link?.disconnect()
        link = null
        linkJobs.forEach { it.cancel() }
        linkJobs.clear()
        linkQuality = null
    }

    // ---------- Modo demostración (sin placa) ----------

    fun connectDemo() {
        if (isConnected) return
        releaseLink()
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            delay(800)
            isConnected = true
            isDemo = true
            isMovement = true
            simulateReading()
            evaluateAutomation()
            updateSuccessState()
            demoJob = viewModelScope.launch {
                while (isActive && isConnected && isDemo) {
                    delay(5000)
                    simulateReading()
                    evaluateAutomation()
                    updateSuccessState()
                }
            }
        }
    }

    private fun stopDemo() {
        demoJob?.cancel()
        demoJob = null
    }

    private fun simulateReading() {
        temperature = (temperature + (-1..1).random()).coerceIn(18, 34)
        humidity = (humidity + (-3..3).random()).coerceIn(30, 90)
        recordReading()
    }

    // ---------- Sensores ----------

    fun refreshSensors() {
        if (!isConnected) return
        if (isDemo) {
            simulateReading(); evaluateAutomation(); updateSuccessState()
        } else {
            link?.send(Commands.ping(System.currentTimeMillis()))
        }
    }

    /** Solo en modo demostración: en la placa real el PIR manda su propio estado. */
    fun togglePIRMovement(): Boolean {
        if (!isConnected || !isDemo) return false
        isMovement = !isMovement
        evaluateAutomation()
        updateSuccessState()
        return true
    }

    private fun recordReading() {
        lastUpdate = timeFormat.format(Date())
        history.addLast(Reading(temperature, humidity, lastUpdate!!.substring(0, 5)))
        while (history.size > MAX_HISTORY) history.removeFirst()
        val t = temperature; val h = humidity; val m = isMovement; val v = isVentOpen
        viewModelScope.launch { readings.insert(t, h, m, v) }
    }

    // ---------- Actuadores ----------

    fun setAutoMode(enabled: Boolean) {
        isAutoMode = enabled
        link?.send(Commands.mode(enabled))
        evaluateAutomation()
        updateSuccessState()
    }

    fun toggleVentilation(isOpen: Boolean) {
        isAutoMode = false
        link?.send(Commands.mode(false))
        applyVent(isOpen)
        updateSuccessState()
    }

    /** Velocidad de la turbina en %, de 20 a 100 en pasos de 20. */
    fun setFanSpeed(percent: Int) {
        fanSpeed = (percent / 20 * 20).coerceIn(20, 100)
        link?.send(Commands.fan(fanSpeed))
        updateSuccessState()
    }

    /** Cambia la compuerta y, si hay placa real, le envía la orden. */
    private fun applyVent(open: Boolean) {
        isVentOpen = open
        if (!isDemo && isConnected) link?.send(Commands.vent(open))
    }

    // ---------- Reglas y horario (vienen de UserPreferences) ----------

    fun setRules(newRules: AutomationRules) {
        rules = newRules
        evaluateAutomation()
        updateSuccessState()
    }

    fun setSchedule(newSchedule: Schedule) {
        schedule = newSchedule
        evaluateAutomation()
        updateSuccessState()
    }

    private fun isInsideSchedule(now: Calendar = Calendar.getInstance()): Boolean {
        // Calendar: domingo = 1 … sábado = 7  ->  lunes = 1 … domingo = 7
        val day = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val s = schedule
        return if (s.startMinutes <= s.endMinutes) {
            day in s.days && minutes in s.startMinutes until s.endMinutes
        } else {
            // Cruza la medianoche (ej. 22:00 – 06:00): la madrugada cuenta para el día anterior
            val previousDay = if (day == 1) 7 else day - 1
            (day in s.days && minutes >= s.startMinutes) || (previousDay in s.days && minutes < s.endMinutes)
        }
    }

    /** Modo automático: abre si hace calor o hay humedad alta (y hay presencia, si la regla lo pide). */
    private fun evaluateAutomation() {
        if (!isAutoMode || !isConnected) return
        val wanted = if (!isInsideSchedule()) {
            false
        } else {
            val climateNeedsAir = temperature >= rules.tempThreshold || humidity >= rules.humidityThreshold
            climateNeedsAir && (!rules.requirePresence || isMovement)
        }
        if (wanted != isVentOpen) applyVent(wanted)
    }

    private fun updateSuccessState() {
        _uiState.value = MainUiState.Success(
            isConnected = isConnected,
            temperatureC = if (isConnected) temperature else null,
            humidityPct = if (isConnected) humidity else null,
            isMovementDetected = isConnected && isMovement,
            isVentOpen = isVentOpen,
            isAutoMode = isAutoMode,
            fanSpeed = fanSpeed,
            history = history.toList(),
            rules = rules,
            insideSchedule = isInsideSchedule(),
            lastUpdate = lastUpdate,
            isDemo = isDemo,
            linkStatus = linkStatus,
            linkQuality = if (isConnected && !isDemo) linkQuality else null
        )
    }

    override fun onCleared() {
        releaseLink()
        super.onCleared()
    }

    companion object {
        private const val MAX_HISTORY = 20
        private const val HISTORY_DAYS = 7L
    }
}
