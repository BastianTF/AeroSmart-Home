package com.example.ventiladorambiental

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var userPreferences: UserPreferences

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permisos de Bluetooth, Ubicación y Notificaciones concedidos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPreferences = UserPreferences(this)

        val viewPager = binding.viewPagerOnboarding
        val btnSiguiente = binding.btnSiguiente

        val onboardingPages = listOf(
            OnboardingPage(
                "Bienvenido a AeroSmart Home",
                "Monitorea temperatura, humedad, presencia y controla la ventilación de tu hogar de forma sencilla.",
                R.drawable.logo_ventilador
            ),
            OnboardingPage(
                "Climatización Inteligente",
                "El sistema activa el ventilador automáticamente al detectar altas temperaturas o movimiento.",
                R.drawable.ic_temperature
            ),
            OnboardingPage(
                "Tu Hogar Seguro",
                "Recibe alertas inmediatas en tu correo o teléfono ante cualquier actividad inusual en el ambiente.",
                R.drawable.ic_profile_placeholder
            )
        )

        val adapter = OnboardingAdapter(onboardingPages)
        viewPager.adapter = adapter

        setupDots(0, onboardingPages.size)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                setupDots(position, onboardingPages.size)
                if (position == onboardingPages.size - 1) {
                    btnSiguiente.text = "Comenzar"
                    binding.btnSkip.visibility = View.INVISIBLE
                } else {
                    btnSiguiente.text = "Siguiente"
                    binding.btnSkip.visibility = View.VISIBLE
                }
            }
        })

        btnSiguiente.setOnClickListener {
            if (viewPager.currentItem + 1 < adapter.itemCount) {
                viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun setupDots(currentPosition: Int, totalPages: Int) {
        binding.layoutDots.removeAllViews()
        val dots = arrayOfNulls<ImageView>(totalPages)
        for (i in dots.indices) {
            dots[i] = ImageView(this).apply {
                val size = if (i == currentPosition) 24 else 8
                val height = 8
                val params = LinearLayout.LayoutParams(size, height).apply {
                    setMargins(6, 0, 6, 0)
                }
                layoutParams = params
                setImageResource(
                    if (i == currentPosition) R.drawable.circle_dot_active
                    else R.drawable.circle_dot_inactive
                )
            }
            binding.layoutDots.addView(dots[i])
        }
    }

    private fun finishOnboarding() {
        lifecycleScope.launch {
            userPreferences.setOnboardingCompleted(true)
            val intent = Intent(this@OnboardingActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
