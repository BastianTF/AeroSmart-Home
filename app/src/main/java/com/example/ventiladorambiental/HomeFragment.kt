package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.R
import com.example.ventiladorambiental.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUiState()
    }

    private fun setupListeners() {
        binding.btnConnect.setOnClickListener {
            val currentState = viewModel.uiState.value
            if (currentState is MainUiState.Success && currentState.isConnected) {
                viewModel.disconnectDevice()
            } else {
                showBluetoothDevicesDialog()
            }
        }

        binding.btnRefresh.setOnClickListener {
            viewModel.refreshSensors()
            Toast.makeText(requireContext(), "Datos de sensores actualizados", Toast.LENGTH_SHORT).show()
        }

        binding.cardPIR.setOnClickListener {
            val currentState = viewModel.uiState.value
            if (currentState is MainUiState.Success && currentState.isConnected) {
                viewModel.togglePIRMovement()
                val msg = if (currentState.isMovementDetected) "Simulación PIR: Sin movimiento" else "Simulación PIR: Movimiento detectado"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Conecta el dispositivo para simular presencia PIR", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnVentToggle.setOnClickListener {
            val currentState = viewModel.uiState.value
            if (currentState is MainUiState.Success) {
                viewModel.toggleVentilation(!currentState.isVentOpen)
                val status = if (!currentState.isVentOpen) "Abierta" else "Cerrada"
                Toast.makeText(requireContext(), "Compuerta " + status, Toast.LENGTH_SHORT).show()
            }
        }

        binding.profileAvatarCard.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        binding.switchAutoMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoMode(isChecked)
        }
    }

    private fun showBluetoothDevicesDialog() {
        val devices = arrayOf(
            "AeroSmart-ESP32 (Bluetooth BLE)",
            "HC-05 (Bluetooth Classic)",
            "Arduino Wi-Fi / MQTT Broker"
        )
        AlertDialog.Builder(requireContext())
            .setTitle("Vincular con Hardware Arduino")
            .setItems(devices) { _, which ->
                Toast.makeText(requireContext(), "Vinculando con " + devices[which] + "...", Toast.LENGTH_SHORT).show()
                viewModel.connectDevice()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is MainUiState.Loading -> {
                        binding.btnConnect.isEnabled = false
                        setStatusText("Conectando con Arduino...", "#757575")
                        binding.viewStatusDot.setBackgroundResource(R.drawable.circle_gray)
                    }
                    is MainUiState.Success -> {
                        binding.btnConnect.isEnabled = true
                        if (state.isConnected) {
                            setStatusText("Conectado", "#4CAF50")
                            binding.btnConnect.text = getString(R.string.disconnect_bluetooth)
                            binding.viewStatusDot.setBackgroundResource(R.drawable.circle_green)
                        } else {
                            setStatusText("Desconectado", "#E53935")
                            binding.btnConnect.text = getString(R.string.connect)
                            binding.viewStatusDot.setBackgroundResource(R.drawable.circle_red)
                        }

                        binding.txtTemperature.text = state.temperature
                        binding.txtHumidity.text = state.humidity

                        if (state.isMovementDetected && state.isConnected) {
                            binding.txtPIRStatus.text = "Estado: Movimiento detectado"
                            binding.viewLedPIR.setBackgroundResource(R.drawable.circle_green)
                        } else {
                            binding.txtPIRStatus.text = getString(R.string.status_no_movement)
                            binding.viewLedPIR.setBackgroundResource(R.drawable.circle_gray)
                        }

                        binding.switchAutoMode.isChecked = state.isAutoMode
                        binding.txtAutoStatus.text = if (state.isAutoMode) {
                            getString(R.string.auto_mode_desc)
                        } else {
                            "Modo manual activado"
                        }

                        binding.txtServoStatus.text = if (state.isVentOpen) {
                            getString(R.string.status_open)
                        } else {
                            getString(R.string.status_closed)
                        }

                        if (state.isVentOpen) {
                            binding.btnVentToggle.setCardBackgroundColor(android.graphics.Color.parseColor("#E8F1FF"))
                        } else {
                            binding.btnVentToggle.setCardBackgroundColor(android.graphics.Color.parseColor("#F5F7FA"))
                        }
                    }
                    is MainUiState.Error -> {
                        binding.btnConnect.isEnabled = true
                        binding.viewStatusDot.setBackgroundResource(R.drawable.circle_red)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setStatusText(statusText: String, colorHex: String) {
        val htmlText = "Estado: <font color='" + colorHex + "'><b>" + statusText + "</b></font>"
        binding.txtStatus.text = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
