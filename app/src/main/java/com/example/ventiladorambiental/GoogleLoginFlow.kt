package com.example.ventiladorambiental

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ventiladorambiental.auth.GoogleAuth
import com.example.ventiladorambiental.data.UserPreferences
import com.example.ventiladorambiental.data.UserRepository
import kotlinx.coroutines.launch

/** Botón "Continuar con Google" de Login y Registro: inicia sesión y abre el panel principal. */
fun AppCompatActivity.startGoogleLogin(button: View) {
    button.isEnabled = false
    lifecycleScope.launch {
        when (val r = GoogleAuth.signIn(this@startGoogleLogin)) {
            is GoogleAuth.Result.Success -> {
                val user = UserRepository(this@startGoogleLogin).ensureGoogleUser(r.name, r.email)
                val prefs = UserPreferences(this@startGoogleLogin)
                prefs.saveEmail(user.email)
                prefs.saveProfile(user.name, user.phone)
                Toast.makeText(this@startGoogleLogin, "Bienvenido/a, ${user.name}", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@startGoogleLogin, MainActivity::class.java))
                finishAffinity()
            }
            GoogleAuth.Result.Cancelled -> button.isEnabled = true
            is GoogleAuth.Result.Error -> {
                button.isEnabled = true
                Toast.makeText(this@startGoogleLogin, r.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
