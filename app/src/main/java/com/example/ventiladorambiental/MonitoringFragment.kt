package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.databinding.FragmentMonitoringBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MonitoringFragment : Fragment() {

    private var _binding: FragmentMonitoringBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMonitoringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnConnect.setOnClickListener { ConnectionUi.onConnectClick(requireContext(), viewModel) }
        binding.cardPIR.setOnClickListener {
            val state = viewModel.uiState.value as? MainUiState.Success
            if (state?.isConnected == true) viewModel.togglePIRMovement()
            else Toast.makeText(requireContext(), "Conecta el dispositivo para simular presencia PIR", Toast.LENGTH_SHORT).show()
        }
        binding.profileAvatarCard.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
        observeUiState()
        UiFx.animateChildrenIn(binding.contentLayout)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                ConnectionUi.render(state, binding.viewStatusDot, binding.txtStatus, binding.txtStatusHint, binding.btnConnect)
                if (state !is MainUiState.Success) return@collectLatest

                binding.txtTemperature.text = state.temperature
                binding.txtHumidity.text = state.humidity

                val h = state.history
                binding.txtTempRange.text = if (h.isEmpty()) "Mín -- · Máx --"
                else "Mín ${h.minOf { it.temperature }} · Máx ${h.maxOf { it.temperature }}"
                binding.txtHumRange.text = if (h.isEmpty()) "Mín -- · Máx --"
                else "Mín ${h.minOf { it.humidity }} · Máx ${h.maxOf { it.humidity }}"

                binding.chartHistory.setReadings(h)
                binding.txtChartEmpty.visibility = if (h.size < 2) View.VISIBLE else View.GONE
                binding.txtChartEmpty.text = if (state.isConnected) "Esperando más lecturas…"
                else "Conecta el dispositivo para ver las lecturas en vivo"
                binding.txtReadingsCount.text = if (h.size == 1) "1 lectura" else "${h.size} lecturas"

                binding.txtPIRStatus.text = when {
                    !state.isConnected -> "Sin conexión"
                    state.isMovementDetected -> "Movimiento detectado · toca para simular"
                    else -> "Sin movimiento · toca para simular"
                }
                binding.viewLedPIR.setBackgroundResource(
                    if (state.isMovementDetected) R.drawable.circle_green else R.drawable.circle_gray
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
