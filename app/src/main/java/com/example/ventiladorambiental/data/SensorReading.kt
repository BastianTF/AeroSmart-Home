package com.example.ventiladorambiental.data

data class SensorReading(
    val id: String,
    val sensorId: String,
    val temperature: Double,
    val humidity: Double,
    val isMovementDetected: Boolean,
    val timestamp: Long,
    val source: String // "SQLite Local", "Firebase Cloud", "MySQL REST API"
)
