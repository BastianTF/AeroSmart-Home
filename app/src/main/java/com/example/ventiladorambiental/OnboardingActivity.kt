package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userPreferences = UserPreferences(this)

        binding.btnStart.setOnClickListener {
            lifecycleScope.launch {
                userPreferences.setOnboardingCompleted(true)

                val intent = Intent(this@OnboardingActivity, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }
}
