package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ventiladorambiental.databinding.ActivityRegisterBinding

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener {
            val firstName = binding.etFirstName.text.toString().trim()
            val lastName = binding.etLastName.text.toString().trim()
            val address = binding.etAddress.text.toString().trim()
            val email = binding.etRegEmail.text.toString().trim()
            val password = binding.etRegPassword.text.toString().trim()

            if (firstName.isNotEmpty() && lastName.isNotEmpty() && address.isNotEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches() && password.length >= 6
            ) {
                showVerificationCodeDialog(email)
            } else {
                Toast.makeText(this, "Por favor completa todos los campos (Contraseña >= 6 chars)", Toast.LENGTH_LONG).show()
            }
        }

        binding.txtBackToLogin.setOnClickListener {
            finish()
        }

        binding.btnRegGoogle.setOnClickListener { showSocialEmailDialog("Google") }
        binding.btnRegFacebook.setOnClickListener { showSocialEmailDialog("Facebook") }
        binding.btnRegApple.setOnClickListener { showSocialEmailDialog("Apple") }
    }

    private fun showVerificationCodeDialog(email: String) {
        val simulatedCode = "1234"
        Toast.makeText(this, "Código enviado a $email (Simulado: $simulatedCode)", Toast.LENGTH_LONG).show()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Verificación de Correo Oficial")
        builder.setMessage("Hemos enviado un código de verificación de 4 dígitos a $email.\n\n(Código de prueba: 1234)")

        val input = EditText(this)
        input.hint = "Ingresa el código (1234)"
        input.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setView(input)

        builder.setPositiveButton("Verificar y Registrar") { _, _ ->
            val enteredCode = input.text.toString().trim()
            if (enteredCode == simulatedCode) {
                Toast.makeText(this, "¡Correo verificado con éxito! Cuenta creada.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finishAffinity()
            } else {
                Toast.makeText(this, "Código incorrecto. Inténtalo de nuevo.", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun showSocialEmailDialog(provider: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Vincular cuenta con $provider")
        builder.setMessage("Ingresa tu correo electrónico oficial de $provider:")

        val input = EditText(this)
        input.hint = "correo@${provider.lowercase()}.com"
        input.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        builder.setView(input)

        builder.setPositiveButton("Continuar") { _, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "¡Autenticado con éxito en $provider ($email)!", Toast.LENGTH_LONG).show()
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
}
