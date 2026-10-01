package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
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
            viewModel.connectDevice()
        }

        binding.btnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        binding.switchVentilation.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleVentilation(isChecked)
            val statusText = if (isChecked) getString(R.string.status_open) else getString(R.string.status_closed)
            binding.txtServoStatus.text = statusText
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is MainUiState.Loading -> {
                        binding.btnConnect.isEnabled = false
                        binding.txtStatus.text = "Conectando..."
                    }
                    is MainUiState.Success -> {
                        binding.btnConnect.isEnabled = true

                        if (state.isConnected) {
                            binding.txtStatus.text = getString(R.string.status_connected)
                            binding.btnConnect.text = "Desconectar"
                        } else {
                            binding.txtStatus.text = getString(R.string.status_disconnected)
                            binding.btnConnect.text = getString(R.string.connect)
                        }

                        binding.txtTemperature.text = state.temperature
                        binding.txtHumidity.text = state.humidity

                        if (state.isMovementDetected) {
                            binding.txtPIRStatus.text = "Movimiento detectado"
                            binding.viewLedPIR.setBackgroundResource(R.drawable.circle_indicator)
                        } else {
                            binding.txtPIRStatus.text = getString(R.string.status_no_movement)
                            binding.viewLedPIR.setBackgroundResource(R.drawable.circle_indicator)
                        }

                        binding.switchVentilation.isChecked = state.isVentOpen
                        binding.txtServoStatus.text = if (state.isVentOpen) "Abierto" else "Cerrado"
                    }
                    is MainUiState.Error -> {
                        binding.btnConnect.isEnabled = true
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
