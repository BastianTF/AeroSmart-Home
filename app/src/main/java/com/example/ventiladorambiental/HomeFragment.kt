package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.FragmentHomeBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUiState()
        observeName()
        UiFx.animateChildrenIn(binding.contentLayout)
    }

    private fun setupListeners() {
        binding.btnConnect.setOnClickListener { ConnectionUi.onConnectClick(requireContext(), viewModel) }

        binding.btnRefresh.setOnClickListener {
            it.animate().rotationBy(360f).setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
            if (isConnected()) {
                viewModel.refreshSensors()
                Toast.makeText(requireContext(), "Datos de sensores actualizados", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Conecta el dispositivo primero", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardPIR.setOnClickListener {
            if (isConnected()) {
                viewModel.togglePIRMovement()
            } else {
                Toast.makeText(requireContext(), "Conecta el dispositivo para simular presencia PIR", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnVentToggle.setOnClickListener {
            val state = viewModel.uiState.value as? MainUiState.Success ?: return@setOnClickListener
            viewModel.toggleVentilation(!state.isVentOpen)
            Toast.makeText(requireContext(), if (!state.isVentOpen) "Compuerta abierta" else "Compuerta cerrada", Toast.LENGTH_SHORT).show()
        }

        binding.profileAvatarCard.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        binding.switchAutoMode.setOnCheckedChangeListener { button, isChecked ->
            if (button.isPressed) viewModel.setAutoMode(isChecked)
        }
    }

    private fun isConnected() = (viewModel.uiState.value as? MainUiState.Success)?.isConnected == true

    private fun observeName() {
        viewLifecycleOwner.lifecycleScope.launch {
            UserPreferences(requireContext()).profile.collectLatest { profile ->
                binding.txtGreeting.text = UiFx.greeting(profile.name)
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                ConnectionUi.render(state, binding.viewStatusDot, binding.txtStatus, binding.txtStatusHint, binding.btnConnect)
                if (state !is MainUiState.Success) return@collectLatest

                binding.txtTemperature.text = state.temperature
                binding.txtHumidity.text = state.humidity
                binding.txtComfort.text = state.comfortLabel
                binding.gaugeHumidity.setProgressCompat(state.humidityPct ?: 0, true)
                binding.txtLastUpdate.text = state.lastUpdate?.let { "Actualizado a las $it" } ?: "Sin lecturas todavía"

                binding.txtPIRStatus.text = if (state.isMovementDetected) "Movimiento detectado" else "Sin movimiento"
                binding.viewLedPIR.setBackgroundResource(
                    if (state.isMovementDetected) R.drawable.circle_green else R.drawable.circle_gray
                )

                if (binding.switchAutoMode.isChecked != state.isAutoMode) binding.switchAutoMode.isChecked = state.isAutoMode
                binding.txtAutoStatus.text = if (state.isAutoMode) getString(R.string.auto_mode_desc) else "Modo manual activado"

                binding.txtServoStatus.text = if (state.isVentOpen) "Abierta" else "Cerrada"
                binding.btnVentToggle.setCardBackgroundColor(
                    ContextCompat.getColor(requireContext(), if (state.isVentOpen) R.color.primary_container else R.color.card_bg)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
