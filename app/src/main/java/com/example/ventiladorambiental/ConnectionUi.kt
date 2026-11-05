package com.example.ventiladorambiental

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Tarjeta de conexión compartida por Inicio y Monitoreo. */
object ConnectionUi {

    /** Conecta o desconecta según el estado actual. */
    fun onConnectClick(context: Context, viewModel: MainViewModel) {
        val state = viewModel.uiState.value
        if (state is MainUiState.Success && (state.isConnected || state.linkStatus != null)) {
            viewModel.disconnectDevice()
            Toast.makeText(context, "Dispositivo desconectado", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf(
            "Placa AeroSmart (Wi-Fi ${BuildConfig.DEVICE_HOST})",
            "Modo demostración (sin placa)"
        )
        MaterialAlertDialogBuilder(context)
            .setTitle("Elige tu dispositivo")
            .setItems(options) { _, which ->
                if (which == 1) {
                    viewModel.connectDemo()
                    return@setItems
                }
                when {
                    !viewModel.isLinkConfigured -> MaterialAlertDialogBuilder(context)
                        .setTitle("Falta la clave del enlace")
                        .setMessage("Agrega aerosmart.linkKey en local.properties (la misma clave del archivo secrets.h del Arduino) y vuelve a compilar.")
                        .setPositiveButton("Entendido", null)
                        .show()
                    !isOnWifi(context) -> MaterialAlertDialogBuilder(context)
                        .setTitle("Conéctate a la placa")
                        .setMessage("En Ajustes > Wi-Fi elige la red AeroSmart-Home y vuelve. Si Android avisa que la red no tiene internet, elige \"Mantener conexión\".")
                        .setPositiveButton("Abrir Wi-Fi") { _, _ -> context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                    else -> viewModel.connectDevice()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    fun render(state: MainUiState, dot: View, status: TextView, hint: TextView, button: MaterialButton) {
        val context = status.context
        when (state) {
            is MainUiState.Loading -> {
                button.isEnabled = false
                dot.setBackgroundResource(R.drawable.circle_gray)
                status.text = "Conectando…"
                hint.text = "Saludo seguro con la placa"
            }
            is MainUiState.Success -> {
                button.isEnabled = true
                when {
                    state.isConnected -> {
                        dot.setBackgroundResource(R.drawable.circle_green)
                        status.text = if (state.isDemo) "Demostración" else context.getString(R.string.status_connected)
                        hint.text = state.lastUpdate?.let { "Última lectura $it" } ?: "Recibiendo datos"
                        button.text = context.getString(R.string.disconnect_bluetooth)
                    }
                    state.linkStatus != null -> {
                        dot.setBackgroundResource(R.drawable.circle_gray)
                        status.text = "Sin señal"
                        hint.text = state.linkStatus
                        button.text = context.getString(R.string.disconnect_bluetooth)
                    }
                    else -> {
                        dot.setBackgroundResource(R.drawable.circle_red)
                        status.text = context.getString(R.string.status_disconnected)
                        hint.text = "Conecta tu placa para ver datos en vivo"
                        button.text = context.getString(R.string.connect)
                    }
                }
            }
            is MainUiState.Error -> {
                button.isEnabled = true
                button.text = context.getString(R.string.connect)
                dot.setBackgroundResource(R.drawable.circle_red)
                status.text = "Error de conexión"
                hint.text = state.message
            }
        }
    }
}
