package com.example.ventiladorambiental.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Contraseñas con hash PBKDF2 + sal aleatoria. Nunca se guarda la contraseña.
 * Formato guardado: "algoritmo:iteraciones:sal:hash" (sal y hash en Base64).
 */
object PasswordHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    // PBKDF2WithHmacSHA256 existe desde Android 8 (API 26); antes se usa SHA1.
    private fun algorithm(): String = try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256"); "PBKDF2WithHmacSHA256"
    } catch (e: Exception) {
        "PBKDF2WithHmacSHA1"
    }

    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val alg = algorithm()
        val hash = derive(password, salt, ITERATIONS, alg)
        return listOf(alg, ITERATIONS.toString(), b64(salt), b64(hash)).joinToString(":")
    }

    fun verify(password: CharArray, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 4) return false
        return try {
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            val actual = derive(password, Base64.decode(parts[2], Base64.NO_WRAP), parts[1].toInt(), parts[0])
            MessageDigest.isEqual(expected, actual) // comparación en tiempo constante
        } catch (e: Exception) {
            false
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int, alg: String): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(alg).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
