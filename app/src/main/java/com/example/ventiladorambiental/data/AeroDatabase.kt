package com.example.ventiladorambiental.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local SQLite (archivo aerosmart.db dentro del teléfono).
 * - usuarios: cuentas creadas en Registro (contraseña con hash, celular cifrado).
 * - lecturas: historial de temperatura, humedad y presencia.
 * Ajustes, horario y reglas se guardan aparte en DataStore (UserPreferences).
 */
class AeroDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_USERS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                telefono_cifrado TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                creado_en INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $T_READINGS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                temperatura INTEGER NOT NULL,
                humedad INTEGER NOT NULL,
                movimiento INTEGER NOT NULL,
                compuerta_abierta INTEGER NOT NULL,
                creado_en INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_lecturas_fecha ON $T_READINGS(creado_en)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 1: todavía no hay migraciones. Cuando se cambie una tabla,
        // subir VERSION y agregar aquí un ALTER TABLE (no borrar datos del usuario).
    }

    companion object {
        const val NAME = "aerosmart.db"
        const val VERSION = 1
        const val T_USERS = "usuarios"
        const val T_READINGS = "lecturas"

        @Volatile private var instance: AeroDatabase? = null

        fun get(context: Context): AeroDatabase =
            instance ?: synchronized(this) { instance ?: AeroDatabase(context).also { instance = it } }
    }
}
