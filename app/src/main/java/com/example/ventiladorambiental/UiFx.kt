package com.example.ventiladorambiental

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
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
            child.setTag(R.id.ui_pending_reveal, null)
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

    // ---------- Entrada de pantalla, letras y desplazamiento ----------

    /**
     * Se llama en onResume de cada pestaña (cuando de verdad se ve):
     *  - las tarjetas visibles entran una tras otra;
     *  - los títulos del encabezado aparecen "juntando" las letras;
     *  - las tarjetas que están más abajo aparecen al desplazarse hasta ellas.
     */
    fun enterScreen(scroll: ScrollView, content: ViewGroup, header: ViewGroup?) {
        val fold = scroll.scrollY + (if (scroll.height > 0) scroll.height else Int.MAX_VALUE)
        var i = 0
        content.children.forEach { child ->
            child.animate().cancel()
            if (child.top > fold) {
                // fuera de la pantalla: se revela al desplazar
                child.alpha = 0f
                child.translationY = 48f
                child.setTag(R.id.ui_pending_reveal, true)
            } else {
                child.setTag(R.id.ui_pending_reveal, null)
                slideIn(child, i++ * 45L)
            }
        }
        header?.let { h -> textViews(h).forEachIndexed { n, tv -> revealText(tv, 80L + n * 70L) } }
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val bottom = scrollY + scroll.height - 24
            content.children.forEach { child ->
                if (child.getTag(R.id.ui_pending_reveal) == true && child.top < bottom) {
                    child.setTag(R.id.ui_pending_reveal, null)
                    slideIn(child, 0)
                    firstTextView(child)?.let { revealText(it, 60) }
                }
            }
        }
    }

    private fun slideIn(v: View, delay: Long) {
        v.alpha = 0f
        v.translationY = 24f
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(320)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    /** Las letras empiezan separadas y transparentes y se juntan hasta su espaciado normal. */
    fun revealText(tv: TextView, delay: Long = 0) {
        val target = (tv.getTag(R.id.ui_letter_spacing) as? Float) ?: tv.letterSpacing.also { tv.setTag(R.id.ui_letter_spacing, it) }
        (tv.getTag(R.id.ui_text_anim) as? ValueAnimator)?.cancel()
        tv.alpha = 0f
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520
            startDelay = delay
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                val f = it.animatedValue as Float
                tv.alpha = f
                tv.letterSpacing = target + 0.22f * (1f - f)
            }
            start()
        }
        tv.setTag(R.id.ui_text_anim, anim)
    }

    private fun textViews(root: View): List<TextView> = when (root) {
        is MaterialButton -> emptyList()
        is TextView -> listOf(root)
        is ViewGroup -> root.children.flatMap { textViews(it) }.toList()
        else -> emptyList()
    }

    private fun firstTextView(root: View): TextView? = textViews(root).firstOrNull()

    // ---------- Botones ----------

    /** Botón con ruedita de carga: se desactiva y muestra [loadingText] mientras dura la operación. */
    fun setButtonLoading(button: MaterialButton, loading: Boolean, loadingText: CharSequence? = null) {
        if (loading) {
            if (isButtonLoading(button)) return
            button.setTag(R.id.ui_button_text, button.text)
            button.setTag(R.id.ui_button_icon, button.icon ?: NO_ICON)
            val spec = CircularProgressIndicatorSpec(
                button.context, null, 0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
            ).apply { indicatorColors = intArrayOf(button.currentTextColor) }
            button.icon = IndeterminateDrawable.createCircularDrawable(button.context, spec)
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            loadingText?.let { button.text = it }
            button.isEnabled = false
        } else {
            if (!isButtonLoading(button)) return
            button.icon = button.getTag(R.id.ui_button_icon) as? android.graphics.drawable.Drawable
            (button.getTag(R.id.ui_button_text) as? CharSequence)?.let { button.text = it }
            button.setTag(R.id.ui_button_text, null)
            button.setTag(R.id.ui_button_icon, null)
            button.isEnabled = true
        }
    }

    fun isButtonLoading(button: MaterialButton) = button.getTag(R.id.ui_button_text) != null

    private val NO_ICON = Any()

    /** Latido suave para un botón que espera respuesta (ej. Google). Devuelve el animador para detenerlo. */
    fun pulse(view: View): ValueAnimator = ValueAnimator.ofFloat(1f, 0.88f).apply {
        duration = 520
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { val f = it.animatedValue as Float; view.scaleX = f; view.scaleY = f; view.alpha = 0.6f + 0.4f * f }
        start()
    }

    fun stopPulse(view: View, anim: ValueAnimator?) {
        anim?.cancel()
        view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).setInterpolator(OvershootInterpolator()).start()
    }
}
