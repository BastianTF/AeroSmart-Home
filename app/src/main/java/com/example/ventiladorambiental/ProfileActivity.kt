package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.ventiladorambiental.data.UserDatabaseHelper
import com.example.ventiladorambiental.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var dbHelper: UserDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = UserDatabaseHelper(this)

        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        binding.switchDarkMode.isChecked = isDarkMode

        val isNotificationsEnabled = sharedPrefs.getBoolean("notifications_enabled", true)
        binding.switchNotifications.isChecked = isNotificationsEnabled

        loadUserProfile()

        binding.btnEditProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("notifications_enabled", isChecked).apply()
            val status = if (isChecked) "activadas" else "desactivadas"
            Toast.makeText(this, "Notificaciones $status", Toast.LENGTH_SHORT).show()
        }

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("dark_mode", isChecked).apply()
            val targetMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode)
            }
            val modeText = if (isChecked) "activado" else "desactivado"
            Toast.makeText(this, "Modo oscuro $modeText", Toast.LENGTH_SHORT).show()
        }

        binding.txtTerms.setOnClickListener {
            showTermsDialog()
        }

        binding.btnLogout.setOnClickListener {
            dbHelper.clearActiveSession()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = dbHelper.getActiveUser()
        if (user != null) {
            val fullName = "${user.firstName} ${user.lastName}".trim()
            binding.txtProfileEmail.text = user.email
            binding.txtProfileEmailDetail.text = user.email
            binding.txtProfileFullName.text = if (fullName.isNotEmpty()) fullName else "Usuario AeroSmart"
            binding.txtProfilePhone.text = if (user.phone.isNotEmpty()) user.phone else "+56 9 1234 5678"
        }
    }

    private fun showTermsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Términos de Servicio y Privacidad")
        builder.setMessage("AeroSmart Home recopila datos de sensores ambientales y números de celular exclusivamente para el envío de alertas críticas de temperatura o intrusión (PIR) y el control inteligente de ventilación. Sus datos están protegidos bajo estrictos estándares de privacidad y cifrado.")
        builder.setPositiveButton("Aceptar", null)
        builder.show()
    }
}
