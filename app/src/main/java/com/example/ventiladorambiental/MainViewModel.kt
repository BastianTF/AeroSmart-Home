package com.example.ventiladorambiental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ventiladorambiental.data.AutomationRules
import com.example.ventiladorambiental.data.Schedule
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
 * Por ahora los datos de los sensores son SIMULADOS: cuando exista el enlace real
 * (HC-05 / ESP32), reemplazar [simulateReading] por la lectura del Arduino.
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var isConnected = false
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
    private var streamJob: Job? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        updateSuccessState()
    }

    // ---------- Conexión ----------

    fun connectDevice() {
        if (isConnected) return
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            delay(1000)
            isConnected = true
            isMovement = true
            history.clear()
            simulateReading()
            startStream()
            evaluateAutomation()
            updateSuccessState()
        }
    }

    fun disconnectDevice() {
        streamJob?.cancel()
        isConnected = false
        isVentOpen = false
        isMovement = false
        lastUpdate = null
        updateSuccessState()
    }

    /** Mientras está conectado, llega una lectura nueva cada 5 segundos. */
    private fun startStream() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            while (isActive && isConnected) {
                delay(5000)
                simulateReading()
                evaluateAutomation()
                updateSuccessState()
            }
        }
    }

    // ---------- Sensores ----------

    fun refreshSensors() {
        if (!isConnected) return
        simulateReading()
        evaluateAutomation()
        updateSuccessState()
    }

    fun togglePIRMovement() {
        if (!isConnected) return
        isMovement = !isMovement
        evaluateAutomation()
        updateSuccessState()
    }

    private fun simulateReading() {
        // Variación pequeña y realista entre lecturas
        temperature = (temperature + (-1..1).random()).coerceIn(18, 34)
        humidity = (humidity + (-3..3).random()).coerceIn(30, 90)
        lastUpdate = timeFormat.format(Date())
        history.addLast(Reading(temperature, humidity, lastUpdate!!.substring(0, 5)))
        while (history.size > MAX_HISTORY) history.removeFirst()
    }

    // ---------- Actuadores ----------

    fun setAutoMode(enabled: Boolean) {
        isAutoMode = enabled
        evaluateAutomation()
        updateSuccessState()
    }

    fun toggleVentilation(isOpen: Boolean) {
        isAutoMode = false
        isVentOpen = isOpen
        updateSuccessState()
    }

    /** Velocidad de la turbina en %, de 20 a 100 en pasos de 20. */
    fun setFanSpeed(percent: Int) {
        fanSpeed = (percent / 20 * 20).coerceIn(20, 100)
        updateSuccessState()
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
        if (!isInsideSchedule()) {
            isVentOpen = false
            return
        }
        val climateNeedsAir = temperature >= rules.tempThreshold || humidity >= rules.humidityThreshold
        val presenceOk = !rules.requirePresence || isMovement
        isVentOpen = climateNeedsAir && presenceOk
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
            lastUpdate = lastUpdate
        )
    }

    companion object {
        private const val MAX_HISTORY = 20
    }
}
