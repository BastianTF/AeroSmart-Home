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
        val targetDot: Int
        val targetStatus: String
        val targetHint: String
        val targetBtnText: String
        val targetBtnEnabled: Boolean

        when (state) {
            is MainUiState.Loading -> {
                targetDot = R.drawable.circle_gray
                targetStatus = "Conectando…"
                targetHint = "Saludo seguro con la placa"
                targetBtnText = context.getString(R.string.connect)
                targetBtnEnabled = false
            }
            is MainUiState.Success -> {
                targetBtnEnabled = true
                when {
                    state.isConnected -> {
                        targetDot = R.drawable.circle_green
                        targetStatus = if (state.isDemo) "Demostración" else context.getString(R.string.status_connected)
                        targetHint = state.lastUpdate?.let { "Última lectura $it" } ?: "Recibiendo datos"
                        targetBtnText = context.getString(R.string.disconnect_bluetooth)
                    }
                    state.linkStatus != null -> {
                        targetDot = R.drawable.circle_gray
                        targetStatus = "Sin señal"
                        targetHint = state.linkStatus
                        targetBtnText = context.getString(R.string.disconnect_bluetooth)
                    }
                    else -> {
                        targetDot = R.drawable.circle_red
                        targetStatus = context.getString(R.string.status_disconnected)
                        targetHint = "Conecta tu placa para ver datos en vivo"
                        targetBtnText = context.getString(R.string.connect)
                    }
                }
            }
            is MainUiState.Error -> {
                targetDot = R.drawable.circle_red
                targetStatus = "Error de conexión"
                targetHint = state.message
                targetBtnText = context.getString(R.string.connect)
                targetBtnEnabled = true
            }
        }

        if (status.text != targetStatus) status.text = targetStatus
        if (hint.text != targetHint) hint.text = targetHint
        // Conectando: ruedita de carga dentro del botón
        val loading = state is MainUiState.Loading
        if (loading) {
            UiFx.setButtonLoading(button, true, "Conectando…")
        } else {
            UiFx.setButtonLoading(button, false)
            if (button.text != targetBtnText) button.text = targetBtnText
            if (button.isEnabled != targetBtnEnabled) button.isEnabled = targetBtnEnabled
        }
        if ((dot.tag as? Int) != targetDot) {
            dot.setBackgroundResource(targetDot)
            dot.tag = targetDot
        }
    }
}
