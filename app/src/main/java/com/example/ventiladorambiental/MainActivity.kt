package com.example.ventiladorambiental

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.ventiladorambiental.databinding.ActivityMainBinding

/**
 * Pantalla principal con 4 pestañas: Inicio, Monitoreo, Automatización y Ventilación.
 * Cada pestaña es un Fragment (ver MainPagerAdapter) y comparte el mismo MainViewModel.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Se crea aquí para que los fragments lo compartan con activityViewModels()
    private val viewModel: MainViewModel by viewModels()

    private val tabs = listOf(R.id.nav_home, R.id.nav_monitoring, R.id.nav_automation, R.id.nav_ventilation)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAeroEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // La barra inferior ya maneja su propio espacio abajo
        binding.mainRoot.applySystemBarInsets(bottom = false)
        viewModel // fuerza la creación antes que los fragments

        binding.viewPagerMain.adapter = MainPagerAdapter(this)
        binding.viewPagerMain.offscreenPageLimit = 1

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
}
