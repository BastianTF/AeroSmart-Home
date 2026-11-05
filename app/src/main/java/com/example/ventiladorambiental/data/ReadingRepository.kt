package com.example.ventiladorambiental.data

import android.content.ContentValues
import android.content.Context
import com.example.ventiladorambiental.Reading
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Historial de lecturas de sensores en SQLite. */
class ReadingRepository(context: Context) {

    private val db = AeroDatabase.get(context)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    suspend fun insert(temperature: Int, humidity: Int, movement: Boolean, ventOpen: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("temperatura", temperature)
            put("humedad", humidity)
            put("movimiento", if (movement) 1 else 0)
            put("compuerta_abierta", if (ventOpen) 1 else 0)
            put("creado_en", System.currentTimeMillis())
        }
        db.writableDatabase.insert(AeroDatabase.T_READINGS, null, values)
    }

    /** Las últimas [limit] lecturas, de la más antigua a la más nueva (para el gráfico). */
    suspend fun latest(limit: Int): List<Reading> = withContext(Dispatchers.IO) {
        val cursor = db.readableDatabase.rawQuery(
            "SELECT temperatura, humedad, creado_en FROM ${AeroDatabase.T_READINGS} ORDER BY creado_en DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        cursor.use {
            buildList {
                while (it.moveToNext()) add(Reading(it.getInt(0), it.getInt(1), timeFormat.format(Date(it.getLong(2)))))
            }.reversed()
        }
    }

    /** Borra lecturas de más de [days] días para que la base no crezca sin límite. */
    suspend fun purgeOlderThan(days: Long) = withContext(Dispatchers.IO) {
        val limit = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days)
        db.writableDatabase.delete(AeroDatabase.T_READINGS, "creado_en < ?", arrayOf(limit.toString()))
    }
}
