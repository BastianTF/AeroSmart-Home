package com.example.ventiladorambiental

import com.example.ventiladorambiental.data.AutomationRules
import com.example.ventiladorambiental.link.LinkQuality

/** Una lectura de los sensores DHT11 (temperatura y humedad) con su hora. */
data class Reading(val temperature: Int, val humidity: Int, val timeLabel: String)

sealed class MainUiState {
    object Loading : MainUiState()

    data class Success(
        val isConnected: Boolean,
        val temperatureC: Int?,
        val humidityPct: Int?,
        val isMovementDetected: Boolean,
        val isVentOpen: Boolean,
        val isAutoMode: Boolean,
        val fanSpeed: Int,
        val history: List<Reading>,
        val rules: AutomationRules,
        val insideSchedule: Boolean,
        val lastUpdate: String?,
        /** true = datos simulados (sin placa). */
        val isDemo: Boolean = false,
        /** Texto del enlace cuando no está conectado: "Reconectando (2/6)…", error, etc. */
        val linkStatus: String? = null,
        /** Estabilidad del enlace con la placa (solo conexión real). */
        val linkQuality: LinkQuality? = null
    ) : MainUiState() {
        val temperature: String get() = temperatureC?.let { "$it °C" } ?: "-- °C"
        val humidity: String get() = humidityPct?.let { "$it %" } ?: "-- %"

        /** Texto corto de confort térmico según la temperatura. */
        val comfortLabel: String
            get() = when {
                temperatureC == null -> "Sin conexión"
                temperatureC >= rules.tempThreshold -> "Caluroso"
                temperatureC <= 18 -> "Frío"
                else -> "Confort ideal"
            }
    }

    data class Error(val message: String) : MainUiState()
}
