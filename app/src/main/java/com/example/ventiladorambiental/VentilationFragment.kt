package com.example.ventiladorambiental

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.databinding.FragmentVentilationBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VentilationFragment : Fragment() {

    private var _binding: FragmentVentilationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private var fanAnimator: ObjectAnimator? = null
    private var fanAnimSpeed = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVentilationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUiState()
        UiFx.animateChildrenIn(binding.contentLayout)
    }

    private fun setupListeners() {
        binding.btnOpenVent.setOnClickListener {
            viewModel.toggleVentilation(true)
            Toast.makeText(requireContext(), "Compuerta abierta (modo manual)", Toast.LENGTH_SHORT).show()
        }
        binding.btnCloseVent.setOnClickListener {
            viewModel.toggleVentilation(false)
            Toast.makeText(requireContext(), "Compuerta cerrada (modo manual)", Toast.LENGTH_SHORT).show()
        }
        binding.btnModeAuto.setOnClickListener { viewModel.setAutoMode(true) }
        binding.btnModeManual.setOnClickListener { viewModel.setAutoMode(false) }

        binding.seekFanSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setFanSpeed(value.toInt())
        }

        binding.profileAvatarCard.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state !is MainUiState.Success) return@collectLatest

                binding.txtServoStatus.text = if (state.isVentOpen) "Compuerta abierta" else "Compuerta cerrada"
                binding.txtVentSub.text = if (state.isVentOpen) "Ventilando al ${state.fanSpeed} %" else "La turbina está detenida"
                binding.toggleVent.check(if (state.isVentOpen) R.id.btnOpenVent else R.id.btnCloseVent)
                binding.toggleOpMode.check(if (state.isAutoMode) R.id.btnModeAuto else R.id.btnModeManual)

                binding.txtFanSpeed.text = "${state.fanSpeed} %"
                if (binding.seekFanSpeed.value.toInt() != state.fanSpeed) binding.seekFanSpeed.value = state.fanSpeed.toFloat()
                val bars = listOf(binding.bar1, binding.bar2, binding.bar3, binding.bar4, binding.bar5)
                val lit = state.fanSpeed / 20
                bars.forEachIndexed { i, bar ->
                    bar.setBackgroundResource(if (i < lit) R.drawable.bg_bar_on else R.drawable.bg_bar_off)
                }

                updateFan(state.isVentOpen, state.fanSpeed)
            }
        }
    }

    /** La turbina gira cuando la compuerta está abierta; más rápido a mayor velocidad. */
    private fun updateFan(open: Boolean, speed: Int) {
        val wanted = if (open) speed else 0
        if (wanted == fanAnimSpeed) return
        fanAnimSpeed = wanted
        fanAnimator?.cancel()
        fanAnimator = if (open) UiFx.spin(binding.imgFan, (2400L * 20 / speed).coerceAtLeast(350L)) else null
    }

    override fun onDestroyView() {
        fanAnimator?.cancel()
        fanAnimator = null
        fanAnimSpeed = -1
        super.onDestroyView()
        _binding = null
    }
}
