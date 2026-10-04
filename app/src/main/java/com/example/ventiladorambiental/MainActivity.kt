package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeUiState()
    }

    private fun setupListeners() {
        binding.btnConnect.setOnClickListener {
            // Si ya está conectado, desconectar; si no, buscar dispositivos Arduino Bluetooth/Wi-Fi
            showBluetoothDevicesDialog()
        }

        binding.profileAvatarCard.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        binding.switchAutoMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoMode(isChecked)
        }

        binding.switchVentilation.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleVentilation(isChecked)
            val statusText = if (isChecked) getString(R.string.status_open) else getString(R.string.status_closed)
            binding.txtServoStatus.text = statusText
        }
    }

    private fun showBluetoothDevicesDialog() {
        val devices = arrayOf(
            "AeroSmart-ESP32 (Bluetooth BLE)",
            "HC-05 (Bluetooth Classic)",
            "Arduino Wi-Fi / MQTT Broker"
        )
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Vincular con Hardware Arduino")
        builder.setItems(devices) { _, which ->
            val selectedDevice = devices[which]
            Toast.makeText(this, "Vinculando con $selectedDevice...", Toast.LENGTH_SHORT).show()
            viewModel.connectDevice()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is MainUiState.Loading -> {
                        binding.btnConnect.isEnabled = false
                        binding.txtStatus.text = "Conectando con Arduino..."
                        binding.viewStatusDot.setBackgroundResource(R.drawable.circle_gray)
                    }
                    is MainUiState.Success -> {
                        binding.btnConnect.isEnabled = true

                        if (state.isConnected) {
                            binding.txtStatus.text = getString(R.string.status_connected)
                            binding.btnConnect.text = getString(R.string.disconnect_bluetooth)
                            binding.viewStatusDot.setBackgroundResource(R.drawable.circle_green)
                        } else {
                            binding.txtStatus.text = getString(R.string.status_disconnected)
                            binding.btnConnect.text = getString(R.string.connect_bluetooth)
                            binding.viewStatusDot.setBackgroundResource(R.drawable.circle_red)
                        }

                        binding.txtTemperature.text = state.temperature
                        binding.txtHumidity.text = state.humidity

                        if (state.isMovementDetected && state.isConnected) {
                            binding.txtPIRStatus.text = "Movimiento detectado"
                            binding.viewLedPIR.setBackgroundResource(R.drawable.circle_green)
                        } else {
                            binding.txtPIRStatus.text = getString(R.string.status_no_movement)
                            binding.viewLedPIR.setBackgroundResource(R.drawable.circle_gray)
                        }

                        // Auto Mode UI
                        binding.switchAutoMode.isChecked = state.isAutoMode
                        binding.txtAutoStatus.text = if (state.isAutoMode) {
                            "El sistema decide cuándo encenderse"
                        } else {
                            "Modo manual activado"
                        }

                        binding.switchVentilation.isChecked = state.isVentOpen
                        binding.txtServoStatus.text = if (state.isVentOpen) "Abierto" else "Cerrado"
                    }
                    is MainUiState.Error -> {
                        binding.btnConnect.isEnabled = true
                        binding.viewStatusDot.setBackgroundResource(R.drawable.circle_red)
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
