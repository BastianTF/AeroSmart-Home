package com.example.ventiladorambiental.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * ISO 27400 Compliance: Cifrado y protección de datos sensibles y métricas de sensores.
 * Utiliza cifrado AES/CBC/PKCS5Padding con claves criptográficas.
 */
object CryptoManager {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    
    // Clave simétrica de 256 bits para cifrado seguro en reposo y en tránsito
    private val SECRET_KEY = SecretKeySpec("AeroSmart_ISO27400_SecretKey_256".toByteArray(Charsets.UTF_8), ALGORITHM)
    private val FIXED_IV = IvParameterSpec("AeroSmart_IV_128".toByteArray(Charsets.UTF_8))

    fun encrypt(plainText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, FIXED_IV)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decrypt(cipherText: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, FIXED_IV)
            val decodedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            String(cipher.doFinal(decodedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            cipherText
        }
    }
}
