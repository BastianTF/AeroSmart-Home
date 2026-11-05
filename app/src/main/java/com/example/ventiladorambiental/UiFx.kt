package com.example.ventiladorambiental

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.view.children
import java.util.Calendar

/** Pequeñas ayudas de interfaz compartidas por las pantallas. */
object UiFx {

    /** "Buenos días", "Buenas tardes" o "Buenas noches" según la hora, con el nombre si existe. */
    fun greeting(name: String?): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val base = when (hour) {
            in 5..11 -> "Buenos días"
            in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        val first = name?.trim()?.split(" ")?.firstOrNull().orEmpty()
        return if (first.isNotEmpty()) "$base, $first" else base
    }

    /** Las tarjetas aparecen una tras otra, subiendo un poco (se usa al abrir cada pestaña). */
    fun animateChildrenIn(container: ViewGroup, stepMs: Long = 45L) {
        container.children.forEachIndexed { i, child ->
            child.alpha = 0f
            child.translationY = 24f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(i * stepMs)
                .setDuration(320)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Hace girar una vista sin fin (ícono de turbina). Devuelve el animador para detenerlo. */
    fun spin(view: View, durationMs: Long): ObjectAnimator =
        ObjectAnimator.ofFloat(view, View.ROTATION, view.rotation, view.rotation + 360f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

    fun formatMinutes(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
}
