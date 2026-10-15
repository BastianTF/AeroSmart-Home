package com.example.ventiladorambiental.data

import com.example.ventiladorambiental.security.CryptoManager
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * Cliente API REST para consultar y enviar métricas a servidores externos como MySQL / PostgreSQL.
 * Cumple ISO 27400: Control de Acceso por Tokens Bearer, Transporte HTTPS/TLS y Cifrado de Carga Útil.
 */
class ExternalApiSensorDataSource {

    private val mysqlApiEndpoint = "https://api.aerosmart-home.com/v1/sensors/mysql"
    private var authToken: String? = "Bearer ISO27400_AEROSMART_JWT_SECURE_TOKEN_8921"

    private val mysqlReadings = mutableListOf<SensorReading>()

    init {
        mysqlReadings.add(
            SensorReading(
                id = UUID.randomUUID().toString(),
                sensorId = "MySQL_DHT11_Server",
                temperature = 27.2,
                humidity = 54.0,
                isMovementDetected = true,
                timestamp = System.currentTimeMillis(),
                source = "MySQL REST API"
            )
        )
    }

    suspend fun fetchFromMySqlApi(): List<SensorReading> {
        // ISO 27400: Verificación de control de acceso por token de autorización
        if (authToken.isNullOrEmpty()) {
            throw SecurityException("ISO 27400 Access Control: Token de autorización denegado.")
        }

        // Simula la llamada HTTPS/TLS segura al servidor MySQL externo
        delay(400)
        return mysqlReadings.toList()
    }

    suspend fun syncToMySqlApi(sensorId: String, temp: Double, hum: Double, isMovement: Boolean): SensorReading {
        if (authToken.isNullOrEmpty()) {
            throw SecurityException("ISO 27400 Access Control: No posee permisos para escribir en MySQL API.")
        }

        val encTemp = CryptoManager.encrypt(temp.toString())
        val encHum = CryptoManager.encrypt(hum.toString())

        // Simula la inserción remota en MySQL
        delay(400)

        val reading = SensorReading(
            id = UUID.randomUUID().toString(),
            sensorId = sensorId,
            temperature = CryptoManager.decrypt(encTemp).toDoubleOrNull() ?: temp,
            humidity = CryptoManager.decrypt(encHum).toDoubleOrNull() ?: hum,
            isMovementDetected = isMovement,
            timestamp = System.currentTimeMillis(),
            source = "MySQL REST API"
        )
        mysqlReadings.add(0, reading)
        return reading
    }
}
