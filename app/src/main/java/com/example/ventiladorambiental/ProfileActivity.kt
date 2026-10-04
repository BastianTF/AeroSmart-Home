package com.example.ventiladorambiental

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ventiladorambiental.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Carga de datos de perfil
        binding.etProfileName.setText("Usuario AeroSmart")
        binding.etProfilePhone.setText("+56 9 1234 5678")

        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etProfileName.text.toString().trim()
            val phone = binding.etProfilePhone.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                Toast.makeText(this, "Información personal y teléfono actualizados con éxito", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Por favor completa los campos", Toast.LENGTH_SHORT).show()
            }
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "activadas" else "desactivadas"
            Toast.makeText(this, "Notificaciones $status", Toast.LENGTH_SHORT).show()
        }

        binding.switchAutoVent.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) "automático" else "manual"
            Toast.makeText(this, "Modo de ventilación cambiado a $status", Toast.LENGTH_SHORT).show()
        }

        binding.txtTerms.setOnClickListener {
            showTermsDialog()
        }

        binding.btnLogout.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
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
