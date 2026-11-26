package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.data.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val userPreferences = UserPreferences(this)

        lifecycleScope.launch {
            val settings = userPreferences.settings.first()
            AppCompatDelegate.setDefaultNightMode(
                if (settings.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )

            val onboardingCompleted = userPreferences.isOnboardingCompleted.first()
            val profile = userPreferences.profile.first()

            val nextDestination = when {
                !onboardingCompleted -> Intent(this@SplashActivity, OnboardingActivity::class.java)
                profile.email.isNotBlank() -> Intent(this@SplashActivity, MainActivity::class.java)
                else -> Intent(this@SplashActivity, LoginActivity::class.java)
            }

            startActivity(nextDestination)
            finish()
        }
    }
}
