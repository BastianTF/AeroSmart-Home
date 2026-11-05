package com.example.ventiladorambiental

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.ActivityProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var userPreferences: UserPreferences

    // Android 13+ pide permiso explícito para mostrar notificaciones.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        lifecycleScope.launch { userPreferences.setNotificationsEnabled(granted) }
        if (!granted) {
            binding.switchNotifications.isChecked = false
            Toast.makeText(this, "Sin permiso no podemos avisarte. Puedes activarlo en Ajustes del teléfono.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAeroEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        userPreferences = UserPreferences(this)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        loadData()
        UiFx.animateChildrenIn(binding.contentLayout, 40)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val profile = userPreferences.profile.first()
            val settings = userPreferences.settings.first()
            binding.etProfileName.setText(profile.name)
            binding.etProfilePhone.setText(profile.phone)
            showHeader(profile.name, profile.email)
            binding.switchNotifications.isChecked = settings.notificationsEnabled && NotificationHelper.canNotify(this@ProfileActivity)
            binding.switchKeepScreen.isChecked = settings.keepScreenOn
            // Los listeners se conectan después de cargar, así no reaccionan a los valores iniciales.
            setupListeners()
        }
    }

    private fun showHeader(name: String, email: String) {
        binding.txtAvatarInitials.text = Validators.initials(name)
        binding.txtProfileDisplayName.text = name.ifBlank { "Usuario AeroSmart" }
        binding.txtProfileEmail.text = email.ifBlank { "Sin correo registrado" }
    }

    private fun setupListeners() {
        binding.btnSaveProfile.setOnClickListener { saveProfile() }

        binding.rowNotifications.setOnClickListener { binding.switchNotifications.toggle() }
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                lifecycleScope.launch { userPreferences.setNotificationsEnabled(isChecked) }
            }
        }

        binding.rowKeepScreen.setOnClickListener { binding.switchKeepScreen.toggle() }
        binding.switchKeepScreen.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { userPreferences.setKeepScreenOn(isChecked) }
        }

        binding.txtTerms.setOnClickListener { showTermsDialog() }
        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    private fun saveProfile() {
        val name = binding.etProfileName.text.toString().trim()
        val phone = Validators.normalizeChileanMobile(binding.etProfilePhone.text.toString())

        binding.tilProfileName.error = if (Validators.isValidName(name)) null else "Escribe tu nombre (solo letras)"
        binding.tilProfilePhone.error = if (phone != null) null else "Formato: +56 9 1234 5678"
        if (binding.tilProfileName.error != null || phone == null) return

        binding.etProfilePhone.setText(phone)
        lifecycleScope.launch {
            userPreferences.saveProfile(name, phone)
            showHeader(name, userPreferences.profile.first().email)
            Toast.makeText(this@ProfileActivity, "Datos guardados", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showTermsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Términos y privacidad")
            .setMessage(
                "• AeroSmart Home usa las lecturas de temperatura, humedad y presencia solo para controlar la ventilación de tu hogar.\n\n" +
                    "• Tu nombre, correo y celular se guardan en este teléfono y se usan para las alertas.\n\n" +
                    "• No vendemos ni compartimos tus datos con terceros.\n\n" +
                    "• Puedes borrar tus datos cerrando sesión o desinstalando la app."
            )
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cerrar sesión?")
            .setMessage("Tendrás que volver a ingresar tu correo y contraseña.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ ->
                lifecycleScope.launch {
                    userPreferences.clearSession()
                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            .show()
    }
}
