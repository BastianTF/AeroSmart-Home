package com.example.ventiladorambiental

import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.ventiladorambiental.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStatusBar()
        setupViewPager()
        setupBottomNavigation()
    }

    private fun setupStatusBar() {
        // Barra de estado/notch en negro absoluto
        window.statusBarColor = Color.BLACK
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
    }

    private fun setupViewPager() {
        val adapter = MainPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    0 -> binding.bottomNavigation.selectedItemId = R.id.nav_home
                    1 -> binding.bottomNavigation.selectedItemId = R.id.nav_monitoring
                    2 -> binding.bottomNavigation.selectedItemId = R.id.nav_automation
                    3 -> binding.bottomNavigation.selectedItemId = R.id.nav_ventilation
                }
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.viewPager.setCurrentItem(0, true)
                    true
                }
                R.id.nav_monitoring -> {
                    binding.viewPager.setCurrentItem(1, true)
                    true
                }
                R.id.nav_automation -> {
                    binding.viewPager.setCurrentItem(2, true)
                    true
                }
                R.id.nav_ventilation -> {
                    binding.viewPager.setCurrentItem(3, true)
                    true
                }
                else -> false
            }
        }
    }
}
