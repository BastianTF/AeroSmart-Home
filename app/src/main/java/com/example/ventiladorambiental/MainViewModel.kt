package com.example.ventiladorambiental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ventiladorambiental.data.SensorReading
import com.example.ventiladorambiental.data.SensorRepository
import com.example.ventiladorambiental.data.StorageSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorRepository = SensorRepository(application)

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _sensorHistory = MutableStateFlow<List<SensorReading>>(emptyList())
    val sensorHistory: StateFlow<List<SensorReading>> = _sensorHistory.asStateFlow()

    private var isConnected = false
    private var isVentOpen = false
    private var isAutoMode = true
    private var currentTempNum = 27.0
    private var currentHumidityNum = 55.0
    private var isMovement = true

    init {
        loadInitialData()
        observeSensorStreams()
    }

    fun connectDevice() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            delay(1500)
            isConnected = true

            // Registra y guarda la medición capturada por los sensores en todas las fuentes (SQLite, Firebase, MySQL)
            recordSensorData("DHT11_PIR_MAIN", currentTempNum, currentHumidityNum, isMovement)

            evaluateAutomation()
            updateSuccessState()
        }
    }

    fun recordSensorData(sensorId: String, temp: Double, hum: Double, movement: Boolean) {
        viewModelScope.launch {
            sensorRepository.saveReadingToAllSources(sensorId, temp, hum, movement)
            refreshSensorHistory(StorageSource.ALL_SOURCES)
        }
    }

    fun refreshSensorHistory(source: StorageSource = StorageSource.ALL_SOURCES) {
        viewModelScope.launch {
            val list = sensorRepository.fetchReadingsFrom(source)
            _sensorHistory.value = list
        }
    }

    private fun observeSensorStreams() {
        viewModelScope.launch {
            sensorRepository.observeRealTimeReadings().collect { readings ->
                _sensorHistory.value = readings
            }
        }
    }

    fun setAutoMode(enabled: Boolean) {
        isAutoMode = enabled
        if (isAutoMode && isConnected) {
            evaluateAutomation()
        }
        updateSuccessState()
    }

    fun toggleVentilation(isOpen: Boolean) {
        if (!isAutoMode) {
            isVentOpen = isOpen
            updateSuccessState()
        }
    }

    private fun evaluateAutomation() {
        if (!isAutoMode || !isConnected) return

        if (isMovement && currentTempNum > 26.0) {
            isVentOpen = true
        } else if (!isMovement || currentTempNum < 22.0) {
            isVentOpen = false
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                updateSuccessState()
                refreshSensorHistory(StorageSource.ALL_SOURCES)
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error("No se pudo conectar con el módulo de ventilación. Revisa la conexión.")
            }
        }
    }

    private fun updateSuccessState() {
        _uiState.value = MainUiState.Success(
            temperature = if (isConnected) "${currentTempNum.toInt()}°C" else "-- °C",
            humidity = if (isConnected) "${currentHumidityNum.toInt()}%" else "-- %",
            isMovementDetected = if (isConnected) isMovement else false,
            isVentOpen = isVentOpen,
            isConnected = isConnected,
            isAutoMode = isAutoMode
        )
    }
}
