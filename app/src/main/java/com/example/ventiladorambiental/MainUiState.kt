package com.example.ventiladorambiental

sealed class MainUiState {
    object Loading : MainUiState()
    
    data class Success(
        val temperature: String,
        val humidity: String,
        val isMovementDetected: Boolean,
        val isVentOpen: Boolean,
        val isConnected: Boolean,
        val isAutoMode: Boolean
    ) : MainUiState()

    data class Error(val message: String) : MainUiState()
}
