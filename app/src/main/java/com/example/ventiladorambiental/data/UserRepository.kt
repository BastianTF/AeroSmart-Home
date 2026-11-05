package com.example.ventiladorambiental.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.example.ventiladorambiental.security.KeystoreCipher
import com.example.ventiladorambiental.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalUser(val name: String, val email: String, val phone: String)

enum class RegisterResult { OK, EMAIL_EXISTS, ERROR }

/** Cuentas de usuario en SQLite. Todas las consultas usan parámetros (?) para evitar inyección SQL. */
class UserRepository(context: Context) {

    private val db = AeroDatabase.get(context)

    suspend fun register(name: String, email: String, phone: String, password: CharArray): RegisterResult =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("nombre", name.trim())
                put("email", email.trim().lowercase())
                put("telefono_cifrado", KeystoreCipher.encrypt(phone))
                put("password_hash", PasswordHasher.hash(password))
                put("creado_en", System.currentTimeMillis())
            }
            password.fill('\u0000')
            try {
                db.writableDatabase.insertOrThrow(AeroDatabase.T_USERS, null, values)
                RegisterResult.OK
            } catch (e: SQLiteConstraintException) {
                RegisterResult.EMAIL_EXISTS
            } catch (e: Exception) {
                RegisterResult.ERROR
            }
        }

    /** Devuelve el usuario si el correo y la contraseña coinciden; si no, null. */
    suspend fun login(email: String, password: CharArray): LocalUser? = withContext(Dispatchers.IO) {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT nombre, email, telefono_cifrado, password_hash FROM ${AeroDatabase.T_USERS} WHERE email = ?",
            arrayOf(email.trim().lowercase())
        )
        val user = cursor.use {
            if (!it.moveToFirst()) null
            else if (!PasswordHasher.verify(password, it.getString(3))) null
            else LocalUser(it.getString(0), it.getString(1), KeystoreCipher.decrypt(it.getString(2)).orEmpty())
        }
        password.fill('\u0000')
        user
    }

    suspend fun updateProfile(email: String, name: String, phone: String): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("nombre", name.trim())
            put("telefono_cifrado", KeystoreCipher.encrypt(phone))
        }
        db.writableDatabase.update(AeroDatabase.T_USERS, values, "email = ?", arrayOf(email.trim().lowercase())) > 0
    }
}
