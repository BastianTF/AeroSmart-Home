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

        // Simulación de carga de datos iniciales guardados
        binding.etProfileName.setText("Usuario AeroSmart")
        binding.etProfileAddress.setText("Calle Principal #123, Smart City")

        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etProfileName.text.toString().trim()
            val address = binding.etProfileAddress.text.toString().trim()
            if (name.isNotEmpty() && address.isNotEmpty()) {
                Toast.makeText(this, "Información personal actualizada con éxito", Toast.LENGTH_SHORT).show()
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
        builder.setMessage("AeroSmart Home recopila datos de sensores ambientales (DHT11, PIR) exclusivamente para el control inteligente de ventilación y confort en su hogar. Sus datos están protegidos bajo estándares de privacidad y cifrado.")
        builder.setPositiveButton("Aceptar", null)
        builder.show()
    }
}
