package com.example.ventiladorambiental

import android.content.Context
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Tarjeta de conexión compartida por Inicio y Monitoreo. */
object ConnectionUi {

    private val devices = arrayOf(
        "AeroSmart-ESP32 (Bluetooth BLE)",
        "HC-05 (Bluetooth Classic)",
        "Arduino Wi-Fi / MQTT Broker"
    )

    /** Conecta o desconecta según el estado actual. */
    fun onConnectClick(context: Context, viewModel: MainViewModel) {
        val state = viewModel.uiState.value
        if (state is MainUiState.Success && state.isConnected) {
            viewModel.disconnectDevice()
            Toast.makeText(context, "Dispositivo desconectado", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("Vincular con Hardware Arduino")
            .setItems(devices) { _, which ->
                Toast.makeText(context, "Vinculando con ${devices[which]}...", Toast.LENGTH_SHORT).show()
                viewModel.connectDevice()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun render(state: MainUiState, dot: View, status: TextView, hint: TextView, button: MaterialButton) {
        val context = status.context
        when (state) {
            is MainUiState.Loading -> {
                button.isEnabled = false
                dot.setBackgroundResource(R.drawable.circle_gray)
                status.text = "Conectando…"
                hint.text = "Buscando el módulo Arduino"
            }
            is MainUiState.Success -> {
                button.isEnabled = true
                if (state.isConnected) {
                    dot.setBackgroundResource(R.drawable.circle_green)
                    status.text = context.getString(R.string.status_connected)
                    hint.text = state.lastUpdate?.let { "Última lectura $it" } ?: "Recibiendo datos"
                    button.text = context.getString(R.string.disconnect_bluetooth)
                } else {
                    dot.setBackgroundResource(R.drawable.circle_red)
                    status.text = context.getString(R.string.status_disconnected)
                    hint.text = "Vincula tu Arduino para ver datos en vivo"
                    button.text = context.getString(R.string.connect)
                }
            }
            is MainUiState.Error -> {
                button.isEnabled = true
                dot.setBackgroundResource(R.drawable.circle_red)
                status.text = "Error de conexión"
                hint.text = state.message
            }
        }
    }
}
