package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.ActivityRegisterBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAeroEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        // Volver a la pantalla anterior
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnRegister.setOnClickListener {
            val firstName = binding.etFirstName.text.toString().trim()
            val lastName = binding.etLastName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val email = binding.etRegEmail.text.toString().trim()
            val password = binding.etRegPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            when {
                firstName.isEmpty() || lastName.isEmpty() -> {
                    Toast.makeText(this, "Ingresa tu nombre y apellido", Toast.LENGTH_SHORT).show()
                }
                !Validators.isValidName("$firstName $lastName") -> {
                    Toast.makeText(this, "El nombre solo puede tener letras", Toast.LENGTH_SHORT).show()
                }
                Validators.normalizeChileanMobile(phone) == null -> {
                    Toast.makeText(this, "Ingresa un celular válido: +56 9 1234 5678", Toast.LENGTH_LONG).show()
                }
                !Validators.isValidEmail(email) -> {
                    Toast.makeText(this, "Ingresa un correo electrónico válido", Toast.LENGTH_SHORT).show()
                }
                password.length < 8 -> {
                    Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show()
                }
                password != confirmPassword -> {
                    Toast.makeText(this, "Las contraseñas no coinciden. Verifícala.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    showVerificationCodeDialog("$firstName $lastName", Validators.normalizeChileanMobile(phone)!!, email)
                }
            }
        }

        binding.txtBackToLogin.setOnClickListener {
            finish()
        }

        binding.btnRegGoogle.setOnClickListener { showComingSoon("Google") }
        binding.btnRegFacebook.setOnClickListener { showComingSoon("Facebook") }
        binding.btnRegApple.setOnClickListener { showComingSoon("Apple") }
    }

    private fun showVerificationCodeDialog(name: String, phone: String, email: String) {
        val simulatedCode = "1234"
        Toast.makeText(this, "Código enviado a $email (Simulado: $simulatedCode)", Toast.LENGTH_LONG).show()

        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Verificación de Correo Oficial")
        builder.setMessage("Hemos enviado un código de verificación de 4 dígitos a $email para activar las alertas y confirmar tu cuenta.\n\n(Código de prueba: 1234)")

        val input = EditText(this)
        input.hint = "Ingresa el código (1234)"
        input.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setView(input)

        builder.setPositiveButton("Verificar y Registrar") { _, _ ->
            val enteredCode = input.text.toString().trim()
            if (enteredCode == simulatedCode) {
                lifecycleScope.launch {
                    val prefs = UserPreferences(this@RegisterActivity)
                    prefs.saveProfile(name, phone)
                    prefs.saveEmail(email)
                    Toast.makeText(this@RegisterActivity, "¡Cuenta creada! Bienvenido/a", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    finishAffinity()
                }
            } else {
                Toast.makeText(this, "Código incorrecto. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun showComingSoon(provider: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Registro con $provider")
            .setMessage("El registro con $provider estará disponible próximamente. Por ahora crea tu cuenta con el formulario.")
            .setPositiveButton("Entendido", null)
            .show()
    }
}
