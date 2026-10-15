package com.example.ventiladorambiental

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ventiladorambiental.data.UserDatabaseHelper
import com.example.ventiladorambiental.databinding.ActivityEditProfileBinding

class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding
    private lateinit var dbHelper: UserDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = UserDatabaseHelper(this)

        val currentUser = dbHelper.getActiveUser()
        if (currentUser != null) {
            binding.etEditFirstName.setText(currentUser.firstName)
            binding.etEditLastName.setText(currentUser.lastName)
            binding.etEditPhone.setText(currentUser.phone)
            binding.etEditEmail.setText(currentUser.email)
        }

        binding.btnSaveProfileChanges.setOnClickListener {
            val newFirstName = binding.etEditFirstName.text.toString().trim()
            val newLastName = binding.etEditLastName.text.toString().trim()
            val newPhone = binding.etEditPhone.text.toString().trim()
            val newEmail = binding.etEditEmail.text.toString().trim()

            val currentEmail = currentUser?.email ?: ""

            when {
                newFirstName.isEmpty() || newLastName.isEmpty() -> {
                    Toast.makeText(this, "Ingresa tu nombre y apellido", Toast.LENGTH_SHORT).show()
                }
                newPhone.isEmpty() -> {
                    Toast.makeText(this, "Ingresa un teléfono válido", Toast.LENGTH_SHORT).show()
                }
                !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() -> {
                    Toast.makeText(this, "Ingresa un correo electrónico válido", Toast.LENGTH_SHORT).show()
                }
                newEmail.lowercase() != currentEmail.lowercase() && dbHelper.isEmailRegistered(newEmail) -> {
                    binding.etEditEmail.error = "El correo ya pertenece a otra cuenta"
                    Toast.makeText(this, "El correo electrónico ya pertenece a otra cuenta registrada.", Toast.LENGTH_LONG).show()
                }
                else -> {
                    val updated = dbHelper.updateUser(
                        currentEmail = currentEmail,
                        firstName = newFirstName,
                        lastName = newLastName,
                        phone = newPhone,
                        newEmail = newEmail
                    )
                    if (updated) {
                        Toast.makeText(this, "Perfil actualizado con éxito", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "No se pudo actualizar el perfil.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
