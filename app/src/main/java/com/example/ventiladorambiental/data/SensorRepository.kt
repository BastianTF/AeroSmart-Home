package com.example.ventiladorambiental.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

enum class StorageSource {
    SQLITE_LOCAL,
    FIREBASE_CLOUD,
    MYSQL_EXTERNAL_API,
    ALL_SOURCES
}

/**
 * Repositorio unificado de sensores interoperable.
 * Permite almacenar y consultar datos desde SQLite local, Firebase Cloud y MySQL API externo.
 * Cumple ISO 27400: Cifrado en Reposo, Cifrado en Tránsito (HTTPS/TLS) y Control de Acceso.
 */
class SensorRepository(context: Context) {

    private val localDbHelper = SensorDatabaseHelper(context.applicationContext)
    private val firebaseCloudSource = FirebaseSensorDataSource()
    private val mySqlApiSource = ExternalApiSensorDataSource()

    suspend fun saveReadingToAllSources(
        sensorId: String,
        temp: Double,
        hum: Double,
        isMovement: Boolean
    ) = withContext(Dispatchers.IO) {
        // 1. Guardado cifrado en SQLite local
        localDbHelper.insertSensorReading(sensorId, temp, hum, isMovement)

        // 2. Transmisión cifrada a Firebase Cloud
        try {
            firebaseCloudSource.pushSensorReading(sensorId, temp, hum, isMovement)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Sincronización mediante HTTPS/TLS a la API de MySQL externa
        try {
            mySqlApiSource.syncToMySqlApi(sensorId, temp, hum, isMovement)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchReadingsFrom(source: StorageSource): List<SensorReading> = withContext(Dispatchers.IO) {
        when (source) {
            StorageSource.SQLITE_LOCAL -> {
                localDbHelper.getAllReadings()
            }
            StorageSource.FIREBASE_CLOUD -> {
                firebaseCloudSource.fetchCloudReadings()
            }
            StorageSource.MYSQL_EXTERNAL_API -> {
                try {
                    mySqlApiSource.fetchFromMySqlApi()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            StorageSource.ALL_SOURCES -> {
                val sqliteList = localDbHelper.getAllReadings()
                val firebaseList = firebaseCloudSource.fetchCloudReadings()
                val mysqlList = try { mySqlApiSource.fetchFromMySqlApi() } catch (e: Exception) { emptyList() }
                (sqliteList + firebaseList + mysqlList).sortedByDescending { it.timestamp }
            }
        }
    }

    fun observeRealTimeReadings(): Flow<List<SensorReading>> = flow {
        while (true) {
            val allReadings = fetchReadingsFrom(StorageSource.ALL_SOURCES)
            emit(allReadings)
            delay(3000)
        }
    }.flowOn(Dispatchers.IO)
}
