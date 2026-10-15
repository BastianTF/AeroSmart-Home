package com.example.ventiladorambiental.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.ventiladorambiental.security.CryptoManager
import java.util.UUID

class SensorDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "aerosmart_sensors.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_SENSOR_DATA = "sensor_data"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SENSOR_ID = "sensor_id"
        private const val COLUMN_TEMP_ENCRYPTED = "encrypted_temperature"
        private const val COLUMN_HUM_ENCRYPTED = "encrypted_humidity"
        private const val COLUMN_MOVEMENT = "is_movement"
        private const val COLUMN_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_SENSOR_DATA (
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_SENSOR_ID TEXT,
                $COLUMN_TEMP_ENCRYPTED TEXT,
                $COLUMN_HUM_ENCRYPTED TEXT,
                $COLUMN_MOVEMENT INTEGER,
                $COLUMN_TIMESTAMP INTEGER
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SENSOR_DATA")
        onCreate(db)
    }

    fun insertSensorReading(sensorId: String, temp: Double, hum: Double, isMovement: Boolean): Boolean {
        val db = writableDatabase
        val id = UUID.randomUUID().toString()
        val encryptedTemp = CryptoManager.encrypt(temp.toString())
        val encryptedHum = CryptoManager.encrypt(hum.toString())

        val values = ContentValues().apply {
            put(COLUMN_ID, id)
            put(COLUMN_SENSOR_ID, sensorId)
            put(COLUMN_TEMP_ENCRYPTED, encryptedTemp)
            put(COLUMN_HUM_ENCRYPTED, encryptedHum)
            put(COLUMN_MOVEMENT, if (isMovement) 1 else 0)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }

        val result = db.insert(TABLE_SENSOR_DATA, null, values)
        return result != -1L
    }

    fun getAllReadings(): List<SensorReading> {
        val list = mutableListOf<SensorReading>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SENSOR_DATA,
            null, null, null, null, null,
            "$COLUMN_TIMESTAMP DESC"
        )

        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val sensorId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SENSOR_ID))
            val encTemp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEMP_ENCRYPTED))
            val encHum = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HUM_ENCRYPTED))
            val movement = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_MOVEMENT)) == 1
            val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))

            val temp = CryptoManager.decrypt(encTemp).toDoubleOrNull() ?: 0.0
            val hum = CryptoManager.decrypt(encHum).toDoubleOrNull() ?: 0.0

            list.add(
                SensorReading(
                    id = id,
                    sensorId = sensorId,
                    temperature = temp,
                    humidity = hum,
                    isMovementDetected = movement,
                    timestamp = timestamp,
                    source = "SQLite Local"
                )
            )
        }
        cursor.close()
        return list
    }
}
