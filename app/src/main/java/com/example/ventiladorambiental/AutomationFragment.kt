package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.R
import com.example.ventiladorambiental.databinding.FragmentAutomationBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutomationFragment : Fragment() {

    private var _binding: FragmentAutomationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAutomationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUiState()
    }

    private fun setupListeners() {
        binding.switchAutoMain.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoMode(isChecked)
        }

        binding.btnSegAuto.setOnClickListener {
            viewModel.setAutoMode(true)
        }

        binding.btnSegManual.setOnClickListener {
            viewModel.setAutoMode(false)
        }

        binding.ruleTemp.setOnClickListener {
            showRuleDialog("Regla de Temperatura", "Rango de activación: > 26°C para encender ventilación, < 22°C para apagar.")
        }

        binding.ruleHumidity.setOnClickListener {
            showRuleDialog("Regla de Humedad", "Nivel de humedad de disparo: > 70% activación automática de extracción.")
        }

        binding.rulePIR.setOnClickListener {
            showRuleDialog("Regla de Presencia (PIR)", "Sensor PIR activo: Se enciende la ventilación cuando detecta usuarios en la habitación.")
        }

        binding.itemSchedule.setOnClickListener {
            showRuleDialog("Programación Horaria", "Horario activo: 08:00 AM - 10:00 PM todos los días.")
        }

        binding.profileAvatarCard.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
    }

    private fun showRuleDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state is MainUiState.Success) {
                    binding.switchAutoMain.isChecked = state.isAutoMode
                    if (state.isAutoMode) {
                        binding.txtAutoStatusHeader.text = "Estado: Activado"
                        binding.dotAuto.setBackgroundResource(R.drawable.circle_green)
                        binding.btnSegAuto.setBackgroundResource(R.drawable.bg_segmented_selected)
                        binding.btnSegManual.setBackgroundResource(R.drawable.bg_segmented_unselected)
                    } else {
                        binding.txtAutoStatusHeader.text = "Estado: Modo Manual"
                        binding.dotAuto.setBackgroundResource(R.drawable.circle_gray)
                        binding.btnSegAuto.setBackgroundResource(R.drawable.bg_segmented_unselected)
                        binding.btnSegManual.setBackgroundResource(R.drawable.bg_segmented_selected)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
