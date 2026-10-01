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

    init {
        loadInitialData()
    }

    fun connectDevice() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            delay(1500)
            isConnected = true
            updateSuccessState()
        }
    }

    fun toggleVentilation(isOpen: Boolean) {
        isVentOpen = isOpen
        updateSuccessState()
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
            temperature = if (isConnected) "24°C" else "-- °C",
            humidity = if (isConnected) "55%" else "-- %",
            isMovementDetected = isConnected,
            isVentOpen = isVentOpen,
            isConnected = isConnected
        )
    }
}
