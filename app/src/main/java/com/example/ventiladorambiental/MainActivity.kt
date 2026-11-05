package com.example.ventiladorambiental

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.ActivityMainBinding
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pantalla principal con 4 pestañas: Inicio, Monitoreo, Automatización y Ventilación.
 * Cada pestaña es un Fragment (ver MainPagerAdapter) y comparte el mismo MainViewModel.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var userPreferences: UserPreferences
    private var lastMovement = false

    private val tabs = listOf(R.id.nav_home, R.id.nav_monitoring, R.id.nav_automation, R.id.nav_ventilation)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAeroEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mainRoot.applySystemBarInsets(bottom = false)
        userPreferences = UserPreferences(this)

        setupTabs()
        connectPreferencesToViewModel()
        watchMovementForAlerts()
    }

    private fun setupTabs() {
        binding.viewPagerMain.adapter = MainPagerAdapter(this)
        binding.viewPagerMain.offscreenPageLimit = 1
        // Al cambiar de pestaña la página entra con un leve fundido y escala
        binding.viewPagerMain.setPageTransformer { page, position ->
            val p = abs(position)
            page.alpha = 1f - p * 0.4f
            page.scaleY = 1f - p * 0.04f
        }
        binding.viewPagerMain.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNav.menu.findItem(tabs[position]).isChecked = true
            }
        })
        binding.bottomNav.setOnItemSelectedListener { item ->
            val index = tabs.indexOf(item.itemId)
            if (index >= 0) binding.viewPagerMain.setCurrentItem(index, true)
            index >= 0
        }
    }

    /** Horario, reglas y "pantalla encendida" guardados por el usuario llegan al ViewModel. */
    private fun connectPreferencesToViewModel() {
        lifecycleScope.launch { userPreferences.schedule.collectLatest { viewModel.setSchedule(it) } }
        lifecycleScope.launch { userPreferences.rules.collectLatest { viewModel.setRules(it) } }
        lifecycleScope.launch {
            userPreferences.settings.collectLatest { settings ->
                if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    /** Notificación cuando el PIR pasa de "sin movimiento" a "movimiento" (si el usuario la activó). */
    private fun watchMovementForAlerts() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state is MainUiState.Success) {
                    val moving = state.isMovementDetected
                    if (moving && !lastMovement && userPreferences.settings.first().notificationsEnabled) {
                        NotificationHelper.showMovementAlert(this@MainActivity)
                    }
                    lastMovement = moving
                }
            }
        }
    }
}
