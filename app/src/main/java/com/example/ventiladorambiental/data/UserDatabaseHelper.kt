package com.example.ventiladorambiental.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class User(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val password: String
)

class UserDatabaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "aerosmart_users.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_USERS = "users"
        private const val COLUMN_ID = "id"
        private const val COLUMN_FIRST_NAME = "first_name"
        private const val COLUMN_LAST_NAME = "last_name"
        private const val COLUMN_PHONE = "phone"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"

        private const val PREFS_NAME = "user_session_prefs"
        private const val KEY_ACTIVE_EMAIL = "active_email"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_USERS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_FIRST_NAME TEXT,
                $COLUMN_LAST_NAME TEXT,
                $COLUMN_PHONE TEXT,
                $COLUMN_EMAIL TEXT UNIQUE,
                $COLUMN_PASSWORD TEXT
            )
        """.trimIndent()

        db.execSQL(createTableQuery)

        // Usuario por defecto para pruebas iniciales
        val defaultUser = ContentValues().apply {
            put(COLUMN_FIRST_NAME, "Usuario")
            put(COLUMN_LAST_NAME, "AeroSmart")
            put(COLUMN_PHONE, "+56 9 1234 5678")
            put(COLUMN_EMAIL, "usuario@aerosmart.com")
            put(COLUMN_PASSWORD, "123456")
        }
        db.insert(TABLE_USERS, null, defaultUser)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        onCreate(db)
    }

    fun registerUser(user: User): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FIRST_NAME, user.firstName)
            put(COLUMN_LAST_NAME, user.lastName)
            put(COLUMN_PHONE, user.phone)
            put(COLUMN_EMAIL, user.email.lowercase().trim())
            put(COLUMN_PASSWORD, user.password)
        }
        val result = db.insert(TABLE_USERS, null, values)
        return result != -1L
    }

    fun isEmailRegistered(email: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            arrayOf(COLUMN_EMAIL),
            "$COLUMN_EMAIL = ?",
            arrayOf(email.lowercase().trim()),
            null, null, null
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun getUserByEmail(email: String): User? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            arrayOf(COLUMN_FIRST_NAME, COLUMN_LAST_NAME, COLUMN_PHONE, COLUMN_EMAIL, COLUMN_PASSWORD),
            "$COLUMN_EMAIL = ?",
            arrayOf(email.lowercase().trim()),
            null, null, null
        )

        var user: User? = null
        if (cursor.moveToFirst()) {
            val firstName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIRST_NAME))
            val lastName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_NAME))
            val phone = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE))
            val userEmail = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMAIL))
            val password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD))
            user = User(firstName, lastName, phone, userEmail, password)
        }
        cursor.close()
        return user
    }

    fun updateUser(currentEmail: String, firstName: String, lastName: String, phone: String, newEmail: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FIRST_NAME, firstName)
            put(COLUMN_LAST_NAME, lastName)
            put(COLUMN_PHONE, phone)
            put(COLUMN_EMAIL, newEmail.lowercase().trim())
        }
        val rowsAffected = db.update(
            TABLE_USERS,
            values,
            "$COLUMN_EMAIL = ?",
            arrayOf(currentEmail.lowercase().trim())
        )
        if (rowsAffected > 0) {
            saveActiveSession(newEmail)
            return true
        }
        return false
    }

    fun saveActiveSession(email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_EMAIL, email.lowercase().trim()).apply()
    }

    fun hasActiveSession(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_ACTIVE_EMAIL)
    }

    fun getActiveUser(): User? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeEmail = prefs.getString(KEY_ACTIVE_EMAIL, null) ?: return getUserByEmail("usuario@aerosmart.com")
        return getUserByEmail(activeEmail) ?: getUserByEmail("usuario@aerosmart.com")
    }

    fun clearActiveSession() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ACTIVE_EMAIL).apply()
    }
}
