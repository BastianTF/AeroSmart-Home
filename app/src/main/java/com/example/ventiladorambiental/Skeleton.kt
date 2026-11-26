package com.example.ventiladorambiental

import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children

/**
 * Esqueleto de carga ("skeleton"): mientras llegan los datos, cada texto, ícono y gráfico de la
 * pantalla se cubre con un bloque gris redondeado que pulsa suavemente. No cambia los layouts:
 * usa el `foreground` de cada vista y lo restaura al terminar.
 */
object Skeleton {

    private class State(val drawables: List<GradientDrawable>, val animator: ValueAnimator)

    /** Muestra u oculta el esqueleto en [root]. Las vistas de [exclude] (y lo que contienen) quedan visibles. */
    fun set(root: ViewGroup, loading: Boolean, exclude: Collection<View> = emptyList()) {
        if (loading) show(root, exclude.toSet()) else hide(root)
    }

    fun show(root: ViewGroup, exclude: Set<View> = emptySet()) {
        if (root.getTag(R.id.skeleton_state) != null) return
        val color = ContextCompat.getColor(root.context, R.color.surface_variant)
        val radius = 8f * root.resources.displayMetrics.density
        val drawables = mutableListOf<GradientDrawable>()

        fun cover(v: View) {
            if (v in exclude || v.visibility != View.VISIBLE) return
            if (v is ViewGroup) { v.children.forEach(::cover); return }
            val block = GradientDrawable().apply { setColor(color); cornerRadius = radius }
            v.setTag(R.id.skeleton_original_fg, v.foreground ?: NO_FOREGROUND)
            v.foreground = block
            drawables += block
        }
        cover(root)

        val animator = ValueAnimator.ofInt(255, 110).apply {
            duration = 750
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { a -> val alpha = a.animatedValue as Int; drawables.forEach { it.alpha = alpha } }
            start()
        }
        root.setTag(R.id.skeleton_state, State(drawables, animator))
    }

    fun hide(root: ViewGroup) {
        val state = root.getTag(R.id.skeleton_state) as? State ?: return
        state.animator.cancel()
        root.setTag(R.id.skeleton_state, null)

        fun restore(v: View) {
            if (v is ViewGroup) { v.children.forEach(::restore); return }
            val original = v.getTag(R.id.skeleton_original_fg) ?: return
            v.foreground = original as? Drawable
            v.setTag(R.id.skeleton_original_fg, null)
        }
        restore(root)
        UiFx.animateChildrenIn(root, 30) // los datos reales aparecen con la misma animación de entrada
    }

    /** Marca para "no tenía foreground" (setTag no acepta null como valor guardado útil). */
    private val NO_FOREGROUND = Any()
}
