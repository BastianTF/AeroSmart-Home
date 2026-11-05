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
import com.example.ventiladorambiental.data.Schedule
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.DialogScheduleBinding
import com.example.ventiladorambiental.databinding.FragmentAutomationBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import android.widget.Toast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutomationFragment : Fragment() {

    private var _binding: FragmentAutomationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var userPreferences: UserPreferences
    private var currentSchedule = Schedule.DEFAULT

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAutomationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userPreferences = UserPreferences(requireContext())
        setupListeners()
        observeUiState()
        observeSchedule()
    }

    // ---------- Programación horaria elegida por el usuario ----------

    private fun observeSchedule() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPreferences.schedule.collectLatest { schedule ->
                currentSchedule = schedule
                binding.txtScheduleDesc.text = describe(schedule)
            }
        }
    }

    private fun showScheduleDialog() {
        val dialogBinding = DialogScheduleBinding.inflate(layoutInflater)
        var start = currentSchedule.startMinutes
        var end = currentSchedule.endMinutes
        val chips = listOf(
            dialogBinding.chipDay1, dialogBinding.chipDay2, dialogBinding.chipDay3, dialogBinding.chipDay4,
            dialogBinding.chipDay5, dialogBinding.chipDay6, dialogBinding.chipDay7
        )
        chips.forEachIndexed { i, chip -> chip.isChecked = (i + 1) in currentSchedule.days }

        fun refreshButtons() {
            dialogBinding.btnStartTime.text = "Desde ${format(start)}"
            dialogBinding.btnEndTime.text = "Hasta ${format(end)}"
        }
        refreshButtons()

        dialogBinding.btnStartTime.setOnClickListener {
            pickTime("Hora de inicio", start) { start = it; refreshButtons() }
        }
        dialogBinding.btnEndTime.setOnClickListener {
            pickTime("Hora de término", end) { end = it; refreshButtons() }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Programación Horaria")
            .setView(dialogBinding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null) // se valida antes de cerrar
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val days = chips.withIndex().filter { it.value.isChecked }.map { it.index + 1 }.toSet()
            when {
                days.isEmpty() -> {
                    dialogBinding.txtScheduleError.text = "Elige al menos un día."
                    dialogBinding.txtScheduleError.visibility = View.VISIBLE
                }
                start == end -> {
                    dialogBinding.txtScheduleError.text = "La hora de inicio y de término no pueden ser iguales."
                    dialogBinding.txtScheduleError.visibility = View.VISIBLE
                }
                else -> {
                    val schedule = Schedule(start, end, days)
                    viewLifecycleOwner.lifecycleScope.launch { userPreferences.saveSchedule(schedule) }
                    Toast.makeText(requireContext(), "Horario guardado: ${describe(schedule)}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
    }

    private fun pickTime(title: String, minutes: Int, onPicked: (Int) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(minutes / 60)
            .setMinute(minutes % 60)
            .setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener { onPicked(picker.hour * 60 + picker.minute) }
        picker.show(childFragmentManager, "time_picker")
    }

    private fun format(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** Ej: "08:00 – 22:00 · Lun a Vie". Si termina antes de empezar, cruza la medianoche. */
    private fun describe(s: Schedule): String {
        val names = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        val days = when {
            s.days.size == 7 -> "Todos los días"
            s.days == (1..5).toSet() -> "Lun a Vie"
            s.days == setOf(6, 7) -> "Fines de semana"
            else -> s.days.sorted().joinToString(", ") { names[it - 1] }
        }
        val overnight = if (s.endMinutes < s.startMinutes) " (al día siguiente)" else ""
        return "${format(s.startMinutes)} – ${format(s.endMinutes)}$overnight · $days"
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
            showScheduleDialog()
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
