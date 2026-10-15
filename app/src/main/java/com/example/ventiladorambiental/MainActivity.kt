package com.example.ventiladorambiental

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permisos de Bluetooth, Ubicación y Notificaciones concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Se requieren permisos de Bluetooth, Ubicación y Notificaciones para el funcionamiento completo de AeroSmart Home",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeUiState()
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Permisos de Ubicación
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        // Permisos de Bluetooth en Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Permisos de Notificaciones en Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
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

        binding.switchAutoMode.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                viewModel.setAutoMode(isChecked)
            }
        }

        binding.switchVentilation.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                viewModel.toggleVentilation(isChecked)
                val statusText = if (isChecked) getString(R.string.status_open) else getString(R.string.status_closed)
                binding.txtServoStatus.text = statusText
            }
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
                        if (binding.switchAutoMode.isChecked != state.isAutoMode) {
                            binding.switchAutoMode.isChecked = state.isAutoMode
                        }
                        binding.txtAutoStatus.text = if (state.isAutoMode) {
                            "El sistema decide cuándo encenderse"
                        } else {
                            "Modo manual activado"
                        }

                        // Desactivar interruptor de ventilación cuando el control automático está activado
                        binding.switchVentilation.isEnabled = !state.isAutoMode
                        if (binding.switchVentilation.isChecked != state.isVentOpen) {
                            binding.switchVentilation.isChecked = state.isVentOpen
                        }
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
