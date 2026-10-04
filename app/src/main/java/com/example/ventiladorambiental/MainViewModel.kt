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
    private var currentTempNum = 27 // Temperatura simulada inicial (>26°C para activar con movimiento)
    private var isMovement = true

    init {
        loadInitialData()
    }

    fun connectDevice() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            delay(1500)
            isConnected = true
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
        // Al tocar manualmente, desactivamos el modo automático para respetar la decisión del usuario
        isAutoMode = false
        isVentOpen = isOpen
        updateSuccessState()
    }

    private fun evaluateAutomation() {
        if (!isAutoMode || !isConnected) return

        // Regla: SI (Presencia == Con movimiento Y Temperatura > 26°C) -> Encender
        // O SI (Presencia == Sin movimiento O Temperatura < 22°C) -> Apagar
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
                _uiState.value = MainUiState.Error("No se pudo conectar con el módulo de ventilación. Revisa la conexión.")
            }
        }
    }

    private fun updateSuccessState() {
        _uiState.value = MainUiState.Success(
            temperature = if (isConnected) "${currentTempNum}°C" else "-- °C",
            humidity = if (isConnected) "55%" else "-- %",
            isMovementDetected = if (isConnected) isMovement else false,
            isVentOpen = isVentOpen,
            isConnected = isConnected,
            isAutoMode = isAutoMode
        )
    }
}
