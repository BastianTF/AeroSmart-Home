package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.databinding.ActivityLoginBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()

            if (validateInputs(email, password)) {
                performLogin()
            }
        }

        binding.txtRegisterLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        binding.txtForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        binding.btnGoogle.setOnClickListener { showSocialEmailDialog("Google") }
        binding.btnFacebook.setOnClickListener { showSocialEmailDialog("Facebook") }
        binding.btnApple.setOnClickListener { showSocialEmailDialog("Apple") }
    }

    private fun showSocialEmailDialog(provider: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Iniciar sesión con $provider")
        builder.setMessage("Ingresa tu correo electrónico oficial de $provider para continuar:")

        val input = EditText(this)
        input.hint = "correo@${provider.lowercase()}.com"
        input.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        builder.setView(input)

        builder.setPositiveButton("Continuar") { _, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "¡Autenticación exitosa con $provider ($email)!", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()
            } else {
                Toast.makeText(this, "Correo de $provider no válido", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun showForgotPasswordDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Recuperar Contraseña")
        builder.setMessage("Ingresa el correo electrónico del titular de la cuenta para enviar el enlace de recuperación:")

        val input = EditText(this)
        input.hint = "correo@ejemplo.com"
        input.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        builder.setView(input)

        builder.setPositiveButton("Enviar Enlace") { _, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
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

    private fun performLogin() {
        binding.btnLogin.text = "Iniciando..."
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            delay(1500)

            binding.btnLogin.isEnabled = true
            binding.btnLogin.text = "Iniciar Sesión"

            val intent = Intent(this@LoginActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
