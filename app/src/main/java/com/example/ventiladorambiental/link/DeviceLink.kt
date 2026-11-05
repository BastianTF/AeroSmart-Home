package com.example.ventiladorambiental.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Enlace serial con la placa ESP8266 (Wemos D1) a través de su red Wi-Fi propia (modo punto de acceso).
 * La placa escucha en un socket TCP: es un flujo de bytes igual a un puerto serial, por el que viajan
 * las líneas del protocolo [SecureChannel].
 *
 * - Saludo HELLO/WELCOME con nonces nuevos en cada conexión.
 * - Latido PING cada 3 s; si no llega nada en 10 s se considera caída y se reconecta.
 * - Reconexión automática con espera creciente (1, 2, 4, 8 s) hasta 6 intentos.
 * - Las órdenes (VENT, FAN, MODE) esperan ACK; si no llega en 1,5 s se reenvían hasta 2 veces.
 */
class DeviceLink(
    private val context: Context,
    private val key: ByteArray,
    private val host: String,
    private val port: Int,
    private val scope: CoroutineScope
) {
    sealed class State {
        object Idle : State()
        object Connecting : State()
        object Connected : State()
        data class Reconnecting(val attempt: Int) : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    /** Cuerpos de mensaje ya verificados (DATA, ACK, ERR…). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _quality = MutableStateFlow(LinkQuality())
    val quality: StateFlow<LinkQuality> = _quality.asStateFlow()

    private var job: Job? = null
    @Volatile private var wanted = false
    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var channel: SecureChannel? = null
    @Volatile private var lastRxAt = 0L
    private val pending = LinkedHashMap<Long, Pending>()
    private val writeLock = Any()

    private data class Pending(val body: String, val sentAt: Long, val tries: Int)

    fun connect() {
        if (job?.isActive == true) return
        wanted = true
        _quality.value = LinkQuality()
        job = scope.launch(Dispatchers.IO) { runLoop() }
    }

    fun disconnect() {
        wanted = false
        closeSocket()
        job?.cancel()
        _state.value = State.Idle
    }

    /** Envía una orden firmada. Devuelve false si no hay conexión. */
    fun send(body: String): Boolean {
        val ch = channel ?: return false
        if (!ch.isEstablished) return false
        return try {
            val line: String
            synchronized(writeLock) {
                line = ch.encode(body)
                write(line)
                if (Commands.isCommand(body)) synchronized(pending) { pending[ch.lastSentSeq] = Pending(body, now(), 0) }
            }
            _quality.value = _quality.value.copy(sent = _quality.value.sent + 1)
            true
        } catch (e: Exception) {
            closeSocket()
            false
        }
    }

    // ---------- Bucle de conexión ----------

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive && wanted) {
            _state.value = if (attempt == 0) State.Connecting else State.Reconnecting(attempt)
            try {
                openSession()
                attempt = 0
                _state.value = State.Connected
                val heartbeat = scope.launch(Dispatchers.IO) { heartbeatLoop() }
                try {
                    readLoop()
                } finally {
                    heartbeat.cancel()
                }
            } catch (e: IOException) {
                // conexión cerrada o caída: se intenta de nuevo abajo
            } catch (e: SecurityException) {
                _state.value = State.Failed("La placa no respondió con la clave correcta.")
                wanted = false
            } finally {
                closeSocket()
            }
            if (!wanted) break
            attempt++
            _quality.value = _quality.value.copy(reconnects = _quality.value.reconnects + 1)
            if (attempt > MAX_ATTEMPTS) {
                _state.value = State.Failed("No se pudo conectar con la placa. Revisa que el teléfono esté en la red Wi-Fi AeroSmart-Home.")
                wanted = false
                break
            }
            delay(minOf(1000L shl (attempt - 1), 8000L))
        }
    }

    private fun openSession() {
        val network = wifiNetwork() ?: throw IOException("sin Wi-Fi")
        val s = network.socketFactory.createSocket() as Socket
        socket = s
        s.tcpNoDelay = true
        s.soTimeout = CONNECT_TIMEOUT_MS
        s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        output = s.getOutputStream()
        val ch = SecureChannel(key)
        channel = ch
        rxBuffer.setLength(0)
        rxOverflow = false
        synchronized(writeLock) { write(ch.hello()) }
        val welcome = readLine(s.getInputStream()) ?: throw IOException("sin respuesta")
        if (!ch.acceptWelcome(welcome)) throw SecurityException("WELCOME inválido")
        s.soTimeout = READ_TIMEOUT_MS
        lastRxAt = now()
        synchronized(pending) { pending.clear() }
    }

    private fun readLoop() {
        val input = socket?.getInputStream() ?: throw IOException("socket cerrado")
        while (wanted) {
            var line: String? = null
            try {
                line = readLine(input)
                if (line == null) throw IOException("fin del flujo")
            } catch (e: java.net.SocketTimeoutException) {
                // sin bytes en 1 s: normal entre lecturas; si pasan 10 s sin nada, la conexión está caída
                if (now() - lastRxAt > LINK_TIMEOUT_MS) throw IOException("sin datos")
            }
            line?.let { handle(it) }
        }
    }

    private fun handle(line: String) {
        val ch = channel ?: return
        when (val r = ch.decode(line)) {
            is SecureChannel.Decoded.Rejected -> _quality.value = _quality.value.copy(rejected = _quality.value.rejected + 1)
            is SecureChannel.Decoded.Accepted -> {
                lastRxAt = now()
                val q = _quality.value
                _quality.value = q.copy(received = q.received + 1, lost = ch.lostCount)
                val body = r.body
                when {
                    body.startsWith("PONG:") -> body.removePrefix("PONG:").toLongOrNull()?.let {
                        _quality.value = _quality.value.copy(rttMs = (now() - it).coerceAtLeast(0))
                    }
                    body.startsWith("ACK:") -> body.removePrefix("ACK:").toLongOrNull()?.let { seq ->
                        synchronized(pending) { pending.remove(seq) }
                    }
                }
                _messages.tryEmit(body)
            }
        }
    }

    private suspend fun heartbeatLoop() {
        while (wanted) {
            delay(HEARTBEAT_MS)
            send(Commands.ping(now()))
            retryPending()
        }
    }

    private fun retryPending() {
        val due = synchronized(pending) {
            pending.entries.filter { now() - it.value.sentAt > ACK_TIMEOUT_MS }.map { it.key to it.value }
                .also { list -> list.forEach { pending.remove(it.first) } }
        }
        for ((_, p) in due) {
            if (p.tries >= MAX_RETRIES) continue
            val ch = channel ?: return
            try {
                synchronized(writeLock) {
                    write(ch.encode(p.body))
                    synchronized(pending) { pending[ch.lastSentSeq] = p.copy(sentAt = now(), tries = p.tries + 1) }
                }
                _quality.value = _quality.value.copy(retries = _quality.value.retries + 1, sent = _quality.value.sent + 1)
            } catch (e: Exception) {
                closeSocket()
            }
        }
    }

    // ---------- E/S ----------

    private fun write(line: String) {
        val out = output ?: throw IOException("sin salida")
        out.write((line + "\n").toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    /** Lee una línea con tope de largo: una línea demasiado larga se descarta completa. */
    private val rxBuffer = StringBuilder()
    private var rxOverflow = false

    // El búfer vive fuera de la función: si vence la espera a mitad de una línea, no se pierde lo ya leído.
    private fun readLine(input: InputStream): String? {
        val sb = rxBuffer
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) {
                val done = sb.toString()
                sb.setLength(0)
                if (rxOverflow) { rxOverflow = false; continue }
                return done
            }
            if (b == '\r'.code) continue
            if (sb.length >= SecureChannel.MAX_LINE) rxOverflow = true else sb.append(b.toChar())
        }
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        output = null
        channel = null
    }

    /**
     * La red de la placa no tiene internet, así que Android podría mandar el tráfico por datos móviles.
     * Por eso el socket se crea desde la red Wi-Fi activa.
     */
    @Suppress("DEPRECATION")
    private fun wifiNetwork(): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4000
        private const val READ_TIMEOUT_MS = 1000
        private const val LINK_TIMEOUT_MS = 10_000L
        private const val HEARTBEAT_MS = 3000L
        private const val ACK_TIMEOUT_MS = 1500L
        private const val MAX_RETRIES = 2
        private const val MAX_ATTEMPTS = 6
    }
}
