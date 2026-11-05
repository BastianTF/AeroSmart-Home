package com.example.ventiladorambiental

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Desde Android 15 (targetSdk 35+) la app se dibuja detrás de las barras del sistema.
 * Esto deja las barras transparentes con íconos oscuros (el fondo de la app es claro).
 * Llamar antes de setContentView().
 */
fun ComponentActivity.enableAeroEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    )
}

/** Suma el alto de la barra de estado (y opcionalmente la inferior y el teclado) al padding de la vista. */
fun View.applySystemBarInsets(bottom: Boolean = true) {
    val startTop = paddingTop
    val startBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        v.updatePadding(
            top = startTop + bars.top,
            bottom = if (bottom) startBottom + bars.bottom else startBottom
        )
        insets
    }
}
