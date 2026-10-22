package com.example.ventiladorambiental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var isConnected = false
    private var isVentOpen = false
    private var isAutoMode = true
    private var currentTempNum = 27
    private var currentHumidityNum = 55
    private var isMovement = false

    init {
        loadInitialData()
    }

    fun connectDevice() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            delay(1000)
            isConnected = true
            isMovement = true
            evaluateAutomation()
            updateSuccessState()
        }
    }

    fun disconnectDevice() {
        isConnected = false
        isVentOpen = false
        isMovement = false
        updateSuccessState()
    }

    fun togglePIRMovement() {
        if (isConnected) {
            isMovement = !isMovement
            evaluateAutomation()
            updateSuccessState()
        }
    }

    fun refreshSensors() {
        if (isConnected) {
            currentTempNum = (24..28).random()
            currentHumidityNum = (50..60).random()
            evaluateAutomation()
            updateSuccessState()
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
        isAutoMode = false
        isVentOpen = isOpen
        updateSuccessState()
    }

    private fun evaluateAutomation() {
        if (!isAutoMode || !isConnected) return

        if (isMovement && currentTempNum > 26) {
            isVentOpen = true
        } else if (!isMovement || currentTempNum < 22) {
            isVentOpen = false
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                updateSuccessState()
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error("No se pudo conectar con el módulo de ventilación.")
            }
        }
    }

    private fun updateSuccessState() {
        _uiState.value = MainUiState.Success(
            temperature = if (isConnected) "${currentTempNum} °C" else "-- °C",
            humidity = if (isConnected) "${currentHumidityNum} %" else "-- %",
            isMovementDetected = if (isConnected) isMovement else false,
            isVentOpen = isVentOpen,
            isConnected = isConnected,
            isAutoMode = isAutoMode
        )
    }
}
