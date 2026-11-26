package com.example.ventiladorambiental.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Gestor de sincronización de lecturas locales (SQLite) a la nube externa (Firebase Realtime Database).
 * Cuando el celular recupera conexión a Internet, lee los registros donde sincronizado = 0
 * y los sube a la nube, marcándolos como sincronizados localmente.
 */
class SyncRepository(private val context: Context) {

    private val readingRepo = ReadingRepository(context)
    private val userPrefs = UserPreferences(context)
    private val isSyncing = AtomicBoolean(false)

    fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Revisa si hay lecturas no sincronizadas y conexión a Internet; si es así, las sube a Firebase. */
    suspend fun syncPendingReadings() = withContext(Dispatchers.IO) {
        if (!isInternetAvailable() || !isSyncing.compareAndSet(false, true)) return@withContext
        try {
            val unsynced = readingRepo.getUnsynced(limit = 100)
            if (unsynced.isEmpty()) return@withContext

            val database = FirebaseDatabase.getInstance().reference
            val rawEmail = userPrefs.profile.first().email
            val emailKey = rawEmail.replace(".", "_").replace("@", "_at_").ifBlank { "dispositivo_anonimo" }

            val syncedIds = mutableListOf<Long>()

            for (reading in unsynced) {
                val recordMap = mapOf(
                    "temperatura" to reading.temperature,
                    "humedad" to reading.humidity,
                    "movimiento" to reading.movement,
                    "compuerta_abierta" to reading.ventOpen,
                    "timestamp" to reading.timestamp
                )

                try {
                    database.child("lecturas_iot").child(emailKey).child(reading.timestamp.toString()).setValue(recordMap).await()
                    syncedIds.add(reading.id)
                } catch (_: Exception) {
                    // Si falla la red o Firebase en este registro, se interrumpe y reintentará en el próximo ciclo
                    break
                }
            }

            if (syncedIds.isNotEmpty()) {
                readingRepo.markAsSynced(syncedIds)
            }
        } finally {
            isSyncing.set(false)
        }
    }
}
