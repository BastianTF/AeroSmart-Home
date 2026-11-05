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
import com.example.ventiladorambiental.data.AutomationRules
import com.example.ventiladorambiental.data.Schedule
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.FragmentAutomationBinding
import com.example.ventiladorambiental.databinding.SheetRuleBinding
import com.example.ventiladorambiental.databinding.SheetScheduleBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutomationFragment : Fragment() {

    private var _binding: FragmentAutomationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var userPreferences: UserPreferences
    private var currentSchedule = Schedule.DEFAULT
    private var currentRules = AutomationRules()

    private enum class Rule { TEMPERATURE, HUMIDITY, PRESENCE }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAutomationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userPreferences = UserPreferences(requireContext())
        setupListeners()
        observeUiState()
        observePreferences()
        UiFx.animateChildrenIn(binding.contentLayout)
    }

    private fun setupListeners() {
        binding.switchAutoMain.setOnCheckedChangeListener { button, isChecked ->
            if (button.isPressed) viewModel.setAutoMode(isChecked)
        }
        binding.btnSegAuto.setOnClickListener { viewModel.setAutoMode(true) }
        binding.btnSegManual.setOnClickListener { viewModel.setAutoMode(false) }

        binding.ruleTemp.setOnClickListener { showRuleSheet(Rule.TEMPERATURE) }
        binding.ruleHumidity.setOnClickListener { showRuleSheet(Rule.HUMIDITY) }
        binding.rulePIR.setOnClickListener { showRuleSheet(Rule.PRESENCE) }
        binding.itemSchedule.setOnClickListener { showScheduleSheet() }

        binding.profileAvatarCard.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state !is MainUiState.Success) return@collectLatest
                if (binding.switchAutoMain.isChecked != state.isAutoMode) binding.switchAutoMain.isChecked = state.isAutoMode
                binding.toggleAutoMode.check(if (state.isAutoMode) R.id.btnSegAuto else R.id.btnSegManual)
                if (state.isAutoMode) {
                    binding.txtAutoStatusHeader.text = "Automático activado"
                    binding.txtAutoDesc.text = "La compuerta se abre sola según tus reglas y horario."
                    binding.dotAuto.setBackgroundResource(R.drawable.circle_green)
                } else {
                    binding.txtAutoStatusHeader.text = "Modo manual"
                    binding.txtAutoDesc.text = "Tú decides cuándo abrir o cerrar la compuerta."
                    binding.dotAuto.setBackgroundResource(R.drawable.circle_gray)
                }
                binding.txtScheduleState.text = if (state.insideSchedule) "Dentro del horario ahora" else "Fuera del horario ahora"
            }
        }
    }

    private fun observePreferences() {
        viewLifecycleOwner.lifecycleScope.launch {
            userPreferences.schedule.collectLatest { schedule ->
                currentSchedule = schedule
                binding.txtScheduleDesc.text = describe(schedule)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            userPreferences.rules.collectLatest { rules ->
                currentRules = rules
                binding.txtRuleTemp.text = "Abre desde ${rules.tempThreshold} °C"
                binding.txtRuleHumidity.text = "Abre desde ${rules.humidityThreshold} %"
                binding.txtRulePIR.text = if (rules.requirePresence) "Solo si hay personas" else "Con o sin personas"
            }
        }
    }

    // ---------- Reglas ----------

    private fun showRuleSheet(rule: Rule) {
        val sheet = BottomSheetDialog(requireContext())
        val b = SheetRuleBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        when (rule) {
            Rule.TEMPERATURE -> setupSlider(b, "Regla de temperatura",
                "La compuerta se abre cuando la temperatura llega a este valor.",
                UserPreferences.TEMP_MIN, UserPreferences.TEMP_MAX, currentRules.tempThreshold, "°C")
            Rule.HUMIDITY -> setupSlider(b, "Regla de humedad",
                "La compuerta se abre cuando la humedad llega a este valor.",
                UserPreferences.HUMIDITY_MIN, UserPreferences.HUMIDITY_MAX, currentRules.humidityThreshold, "%")
            Rule.PRESENCE -> {
                b.txtRuleTitle.text = "Regla de presencia"
                b.txtRuleDesc.text = "Usa el sensor PIR para ventilar solo cuando hay alguien en la habitación. Ahorra energía."
                b.layoutRuleSlider.visibility = View.GONE
                b.layoutRuleSwitch.visibility = View.VISIBLE
                b.switchRule.isChecked = currentRules.requirePresence
            }
        }

        b.btnSaveRule.setOnClickListener {
            val value = b.sliderRule.value.toInt()
            val newRules = when (rule) {
                Rule.TEMPERATURE -> currentRules.copy(tempThreshold = value)
                Rule.HUMIDITY -> currentRules.copy(humidityThreshold = value)
                Rule.PRESENCE -> currentRules.copy(requirePresence = b.switchRule.isChecked)
            }
            viewLifecycleOwner.lifecycleScope.launch { userPreferences.saveRules(newRules) }
            Toast.makeText(requireContext(), "Regla guardada", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun setupSlider(b: SheetRuleBinding, title: String, desc: String, min: Int, max: Int, value: Int, unit: String) {
        b.txtRuleTitle.text = title
        b.txtRuleDesc.text = desc
        b.sliderRule.valueFrom = min.toFloat()
        b.sliderRule.valueTo = max.toFloat()
        b.sliderRule.value = value.coerceIn(min, max).toFloat()
        b.txtRuleMin.text = "$min $unit"
        b.txtRuleMax.text = "$max $unit"
        b.txtRuleValue.text = "$value $unit"
        b.sliderRule.addOnChangeListener { _, v, _ -> b.txtRuleValue.text = "${v.toInt()} $unit" }
    }

    // ---------- Horario ----------

    private fun showScheduleSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val b = SheetScheduleBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        var start = currentSchedule.startMinutes
        var end = currentSchedule.endMinutes
        val chips = listOf(b.chipDay1, b.chipDay2, b.chipDay3, b.chipDay4, b.chipDay5, b.chipDay6, b.chipDay7)
        chips.forEachIndexed { i, chip -> chip.isChecked = (i + 1) in currentSchedule.days }

        fun refresh() {
            b.txtStartTime.text = UiFx.formatMinutes(start)
            b.txtEndTime.text = UiFx.formatMinutes(end)
        }
        refresh()

        b.cardStartTime.setOnClickListener { pickTime("Hora de inicio", start) { start = it; refresh() } }
        b.cardEndTime.setOnClickListener { pickTime("Hora de término", end) { end = it; refresh() } }
        b.chipPresetOffice.setOnClickListener {
            start = 8 * 60; end = 18 * 60
            chips.forEachIndexed { i, c -> c.isChecked = i < 5 }
            refresh()
        }
        b.chipPresetNight.setOnClickListener { start = 22 * 60; end = 6 * 60; refresh() }
        b.chipPresetAllDay.setOnClickListener { start = 0; end = 23 * 60 + 59; refresh() }

        b.btnSaveSchedule.setOnClickListener {
            val days = chips.withIndex().filter { it.value.isChecked }.map { it.index + 1 }.toSet()
            when {
                days.isEmpty() -> showError(b, "Elige al menos un día.")
                start == end -> showError(b, "La hora de inicio y de término no pueden ser iguales.")
                else -> {
                    val schedule = Schedule(start, end, days)
                    viewLifecycleOwner.lifecycleScope.launch { userPreferences.saveSchedule(schedule) }
                    Toast.makeText(requireContext(), "Horario guardado: ${describe(schedule)}", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                }
            }
        }
        sheet.show()
    }

    private fun showError(b: SheetScheduleBinding, text: String) {
        b.txtScheduleError.text = text
        b.txtScheduleError.visibility = View.VISIBLE
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

    /** Ej: "08:00 – 22:00 · Lun a Vie". Si termina antes de empezar, cruza la medianoche. */
    private fun describe(s: Schedule): String {
        val names = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        val days = when {
            s.days.size == 7 -> "Todos los días"
            s.days == (1..5).toSet() -> "Lun a Vie"
            s.days == setOf(6, 7) -> "Fines de semana"
            else -> s.days.sorted().joinToString(", ") { names[it - 1] }
        }
        val overnight = if (s.endMinutes < s.startMinutes) " (+1 día)" else ""
        return "${UiFx.formatMinutes(s.startMinutes)} – ${UiFx.formatMinutes(s.endMinutes)}$overnight · $days"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
