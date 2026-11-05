package com.example.ventiladorambiental

import android.util.Patterns

/** Validaciones de formularios. */
object Validators {

    fun isValidEmail(email: String) = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** Nombre: 2 a 60 caracteres, solo letras, espacios, apóstrofe o guion. */
    fun isValidName(name: String) = name.trim().length in 2..60 && name.trim().all { it.isLetter() || it == ' ' || it == '\'' || it == '-' }

    /**
     * Celular chileno: 9 dígitos que empiezan con 9, con o sin +56.
     * Devuelve el número normalizado "+56 9 XXXX XXXX" o null si no es válido.
     */
    fun normalizeChileanMobile(input: String): String? {
        var digits = input.filter { it.isDigit() }
        if (digits.length == 11 && digits.startsWith("56")) digits = digits.substring(2)
        if (digits.length != 9 || !digits.startsWith("9")) return null
        return "+56 ${digits[0]} ${digits.substring(1, 5)} ${digits.substring(5)}"
    }

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}
