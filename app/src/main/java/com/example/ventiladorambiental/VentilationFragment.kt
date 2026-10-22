package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.R
import com.example.ventiladorambiental.databinding.FragmentVentilationBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VentilationFragment : Fragment() {

    private var _binding: FragmentVentilationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVentilationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUiState()
    }

    private fun setupListeners() {
        binding.btnOpenVent.setOnClickListener {
            viewModel.toggleVentilation(true)
            Toast.makeText(requireContext(), "Compuerta Abierta", Toast.LENGTH_SHORT).show()
        }

        binding.btnCloseVent.setOnClickListener {
            viewModel.toggleVentilation(false)
            Toast.makeText(requireContext(), "Compuerta Cerrada", Toast.LENGTH_SHORT).show()
        }

        binding.btnModeAuto.setOnClickListener {
            viewModel.setAutoMode(true)
            Toast.makeText(requireContext(), "Modo de Operación: Automático", Toast.LENGTH_SHORT).show()
        }

        binding.btnModeManual.setOnClickListener {
            viewModel.setAutoMode(false)
            Toast.makeText(requireContext(), "Modo de Operación: Manual", Toast.LENGTH_SHORT).show()
        }

        binding.seekFanSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speedLevel = progress + 1
                    Toast.makeText(requireContext(), "Velocidad del Ventilador: Nivel " + speedLevel, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.profileAvatarCard.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state is MainUiState.Success) {
                    if (state.isVentOpen) {
                        binding.txtServoStatus.text = "Estado: Abierto"
                        binding.txtServoStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                        binding.btnOpenVent.setBackgroundResource(R.drawable.bg_segmented_selected)
                        binding.btnCloseVent.setBackgroundResource(R.drawable.bg_segmented_unselected)
                    } else {
                        binding.txtServoStatus.text = "Estado: Cerrado"
                        binding.txtServoStatus.setTextColor(android.graphics.Color.parseColor("#1A73E8"))
                        binding.btnOpenVent.setBackgroundResource(R.drawable.bg_segmented_unselected)
                        binding.btnCloseVent.setBackgroundResource(R.drawable.bg_segmented_selected)
                    }

                    if (state.isAutoMode) {
                        binding.btnModeAuto.setBackgroundResource(R.drawable.bg_segmented_selected)
                        binding.btnModeManual.setBackgroundResource(R.drawable.bg_segmented_unselected)
                    } else {
                        binding.btnModeAuto.setBackgroundResource(R.drawable.bg_segmented_unselected)
                        binding.btnModeManual.setBackgroundResource(R.drawable.bg_segmented_selected)
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
