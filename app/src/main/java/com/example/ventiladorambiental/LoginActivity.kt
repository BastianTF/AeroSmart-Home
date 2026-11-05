package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.databinding.ActivityLoginBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAeroEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                performLogin(email)
            }
        }

        binding.txtRegisterLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        binding.txtForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.btnGoogle.setOnClickListener { showComingSoon("Google") }
        binding.btnFacebook.setOnClickListener { showComingSoon("Facebook") }
        binding.btnApple.setOnClickListener { showComingSoon("Apple") }
    }

    private fun showComingSoon(provider: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Inicio con $provider")
            .setMessage("El inicio de sesión con $provider estará disponible próximamente. Por ahora ingresa con tu correo y contraseña.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun showForgotPasswordDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle("Recuperar Contraseña")
        builder.setMessage("Ingresa el correo electrónico del titular de la cuenta para enviar el enlace de recuperación:")

        val input = EditText(this)
        input.hint = "correo@ejemplo.com"
        input.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        builder.setView(input)

        builder.setPositiveButton("Enviar Enlace") { _, _ ->
            val email = input.text.toString().trim()
            if (Validators.isValidEmail(email)) {
                Toast.makeText(this, "Enlace de recuperación enviado a $email", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Por favor ingresa un correo válido", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        if (!Validators.isValidEmail(email)) {
            binding.edtEmail.error = "Ingresa un correo válido"
            isValid = false
        } else {
            binding.edtEmail.error = null
        }

        if (password.length < 6) {
            binding.edtPassword.error = "La contraseña debe tener al menos 6 caracteres"
            isValid = false
        } else {
            binding.edtPassword.error = null
        }

        return isValid
    }

    private fun performLogin(email: String) {
        binding.btnLogin.text = "Iniciando..."
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            delay(1200)
            UserPreferences(this@LoginActivity).saveEmail(email)

            binding.btnLogin.isEnabled = true
            binding.btnLogin.text = "Iniciar Sesión"

            val intent = Intent(this@LoginActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
