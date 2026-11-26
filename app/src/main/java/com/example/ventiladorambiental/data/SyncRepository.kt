package com.example.ventiladorambiental.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Sincroniza las lecturas guardadas en SQLite (sincronizado = 0) con Firebase Realtime Database.
 *
 * - Almacenamiento temporal: sin Internet (por ejemplo, conectado a la red de la placa) las lecturas
 *   quedan en SQLite y se suben cuando vuelve la conexión ([watchNetwork] avisa al recuperar Internet).
 * - Protección de datos personales: en la nube NO se usa el correo; se usa el UID anónimo de
 *   Firebase Auth. Las reglas (firebase/database.rules.json) solo dejan escribir a ese mismo UID.
 * - Energía: se sube en lotes y como máximo cada [MIN_INTERVAL_MS], salvo al recuperar la red.
 */
class SyncRepository(private val context: Context) {

    private val readingRepo = ReadingRepository(context)
    private val isSyncing = AtomicBoolean(false)
    @Volatile private var lastSyncMs = 0L

    fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Llama a [onInternet] cada vez que aparece una red con Internet validado.
     * Devuelve una función para dejar de escuchar.
     */
    fun watchNetwork(onInternet: () -> Unit): () -> Unit {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return {}
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onInternet()
        }
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            return {}
        }
        val stop: () -> Unit = {
            try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        }
        return stop
    }

    /** Cuántas lecturas quedan por subir. */
    suspend fun pendingCount(): Int = readingRepo.countUnsynced()

    /**
     * Sube las lecturas pendientes si hay Internet. Con [force] = false respeta el intervalo mínimo.
     * Devuelve cuántas lecturas se subieron.
     */
    suspend fun syncPendingReadings(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncMs < MIN_INTERVAL_MS) return@withContext 0
        if (!isInternetAvailable() || !isSyncing.compareAndSet(false, true)) return@withContext 0
        var uploaded = 0
        try {
            lastSyncMs = now
            val uid = ensureSignedIn() ?: return@withContext 0
            val node = FirebaseDatabase.getInstance().reference.child("lecturas_iot").child(uid)

            while (true) {
                val batch = readingRepo.getUnsynced(limit = BATCH)
                if (batch.isEmpty()) break
                // Un solo envío por lote (updateChildren) en vez de uno por lectura: menos radio = menos batería.
                val updates = batch.associate { r ->
                    "${r.timestamp}_${r.id}" to mapOf(
                        "temperatura" to r.temperature,
                        "humedad" to r.humidity,
                        "movimiento" to r.movement,
                        "compuerta_abierta" to r.ventOpen,
                        "timestamp" to r.timestamp
                    )
                }
                try {
                    node.updateChildren(updates).await()
                } catch (_: Exception) {
                    break // sin red o rechazo: quedan pendientes y se reintenta después
                }
                readingRepo.markAsSynced(batch.map { it.id })
                uploaded += batch.size
                if (batch.size < BATCH) break
            }
        } finally {
            isSyncing.set(false)
        }
        uploaded
    }

    /** Sesión anónima de Firebase: identifica al dispositivo sin enviar el correo ni el nombre. */
    private suspend fun ensureSignedIn(): String? {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it.uid }
        return try {
            auth.signInAnonymously().await().user?.uid
        } catch (_: Exception) {
            null // Proveedor anónimo desactivado o sin red: los datos siguen guardados en SQLite
        }
    }

    companion object {
        private const val BATCH = 100
        private const val MIN_INTERVAL_MS = 5 * 60_000L
    }
}
