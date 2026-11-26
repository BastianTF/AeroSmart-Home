package com.example.ventiladorambiental

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.animation.ObjectAnimator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
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

    private val initialPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        } else {
            true
        }
        if (!notifGranted) {
            lifecycleScope.launch { userPreferences.setNotificationsEnabled(false) }
        }
    }

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
        requestInitialPermissions()
    }

    private fun requestInitialPermissions() {
        // Solo se pide lo que la app usa: notificaciones (Android 13+). Wi-Fi e Internet no requieren diálogo.
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            initialPermissionsLauncher.launch(missing.toTypedArray())
        }
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
                moveNavPill(tabs[position], animate = true)
            }
        })
        binding.bottomNav.setOnItemSelectedListener { item ->
            val index = tabs.indexOf(item.itemId)
            if (index >= 0) {
                binding.bottomNav.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                binding.viewPagerMain.setCurrentItem(index, true)
            }
            index >= 0
        }
        // La píldora reemplaza el indicador de Material; se ubica al medir la barra (y al rotar)
        binding.bottomNav.isItemActiveIndicatorEnabled = false
        binding.bottomNav.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            // Solo cuando cambia el tamaño (primer dibujo o rotación); así no corta la animación
            if (r - l != oldR - oldL || b - t != oldB - oldT || binding.navPill.alpha == 0f) {
                binding.bottomNav.post { moveNavPill(tabs[binding.viewPagerMain.currentItem], animate = false) }
            }
        }
    }

    /** Desliza la píldora negra hasta el ícono de la pestaña [itemId], estirándose en el camino. */
    private fun moveNavPill(itemId: Int, animate: Boolean) {
        val item = binding.bottomNav.findViewById<View>(itemId) ?: return
        val icon = item.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_view) ?: item
        if (icon.width == 0) return
        val pill = binding.navPill
        val iconLoc = IntArray(2).also { icon.getLocationInWindow(it) }
        val baseLoc = IntArray(2).also { binding.navContainer.getLocationInWindow(it) }
        val x = iconLoc[0] - baseLoc[0] + icon.width / 2f - pill.width / 2f
        val y = iconLoc[1] - baseLoc[1] + icon.height / 2f - pill.height / 2f
        if (!animate || pill.alpha == 0f) {
            pill.animate().cancel()
            pill.translationX = x
            pill.translationY = y
            pill.animate().alpha(1f).setDuration(200).start()
            return
        }
        pill.animate().translationX(x).translationY(y).setDuration(420)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
        ObjectAnimator.ofFloat(pill, View.SCALE_X, 1f, 1.45f, 1f).setDuration(420).start()
        ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.2f, 1f).setDuration(320).start()
        ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.2f, 1f).setDuration(320).start()
    }

    /** Horario, reglas, modo oscuro y "pantalla encendida" guardados por el usuario llegan al ViewModel. */
    private fun connectPreferencesToViewModel() {
        lifecycleScope.launch { userPreferences.schedule.collectLatest { viewModel.setSchedule(it) } }
        lifecycleScope.launch { userPreferences.rules.collectLatest { viewModel.setRules(it) } }
        lifecycleScope.launch {
            userPreferences.settings.collectLatest { settings ->
                if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                AppCompatDelegate.setDefaultNightMode(
                    if (settings.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
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
