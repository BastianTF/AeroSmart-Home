package com.example.ventiladorambiental.link

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Protocolo seguro sobre la comunicación serial Bluetooth (HC-05) entre la app y el Arduino.
 *
 * Cada mensaje es una línea de texto:   SEQ|CUERPO|MAC\n
 *  - SEQ: número de secuencia que siempre sube. Un mensaje con SEQ repetido o menor se rechaza (antirepetición).
 *  - CUERPO: la orden o el dato, por ejemplo "VENT:1" o "DATA:25,55,1,0,60".
 *  - MAC: HMAC-SHA256(clave, DIR|SESION|SEQ|CUERPO), primeros 8 bytes en hexadecimal.
 *    Si cambia un solo carácter del mensaje, el MAC no coincide y el mensaje se descarta (integridad + autenticidad).
 *  - DIR: 'A' si lo envía la app, 'D' si lo envía el dispositivo (evita reenviar un mensaje en sentido contrario).
 *  - SESION: nonceApp + nonceDispositivo, ambos aleatorios y nuevos en cada conexión.
 *    Así un mensaje grabado de una conexión anterior no sirve en la siguiente.
 *
 * Saludo inicial:
 *  app  -> "0|HELLO:<nonceApp>|MAC"      (MAC calculado con SESION = nonceApp)
 *  disp -> "0|WELCOME:<nonceDisp>|MAC"   (MAC calculado con SESION = nonceApp + nonceDisp)
 *
 * El mismo algoritmo está en arduino/AeroSmartHome/AeroSmartHome.ino.
 * Esta clase no usa nada de Android para poder probarla con JUnit (ver app/src/test).
 */
class SecureChannel(
    key: ByteArray,
    private val ownDir: Char = 'A',
    private val peerDir: Char = 'D',
    private val random: SecureRandom = SecureRandom()
) {
    sealed class Decoded {
        data class Accepted(val seq: Long, val body: String) : Decoded()
        data class Rejected(val reason: Reason) : Decoded()
    }

    enum class Reason { FORMAT, MAC, REPLAY, NO_SESSION }

    private val keySpec = SecretKeySpec(key.copyOf(), "HmacSHA256")
    private var appNonce = ""
    private var devNonce = ""
    private var txSeq = 0L
    private var rxSeq = 0L

    var isEstablished = false
        private set

    /** Mensajes perdidos detectados por saltos en la secuencia del otro lado. */
    var lostCount = 0L
        private set

    private val session get() = appNonce + devNonce

    // ---------- Lado app ----------

    /** Primer mensaje de la conexión. Genera un nonce nuevo y reinicia contadores. */
    fun hello(nonce: String = randomHex(NONCE_BYTES)): String {
        require(isHex(nonce, NONCE_BYTES * 2)) { "nonce inválido" }
        appNonce = nonce; devNonce = ""; txSeq = 0; rxSeq = 0; lostCount = 0; isEstablished = false
        val body = "HELLO:$nonce"
        return frame(0, body, ownDir, appNonce)
    }

    /** Procesa la respuesta WELCOME del dispositivo. Devuelve true si la sesión quedó lista. */
    fun acceptWelcome(line: String): Boolean {
        val parts = split(line) ?: return false
        val (seq, body, mac) = parts
        if (seq != 0L || !body.startsWith("WELCOME:")) return false
        val nonce = body.removePrefix("WELCOME:")
        if (!isHex(nonce, NONCE_BYTES * 2)) return false
        if (!macEquals(mac, computeMac(peerDir, appNonce + nonce, seq, body))) return false
        devNonce = nonce
        isEstablished = true
        return true
    }

    // ---------- Ambos lados ----------

    /** Arma un mensaje firmado con el siguiente número de secuencia. */
    fun encode(body: String): String {
        check(isEstablished) { "sesión no establecida" }
        require(isValidBody(body)) { "cuerpo inválido" }
        txSeq += 1
        return frame(txSeq, body, ownDir, session)
    }

    /** Verifica un mensaje recibido: formato, MAC y que la secuencia sea nueva. */
    fun decode(line: String): Decoded {
        if (!isEstablished) return Decoded.Rejected(Reason.NO_SESSION)
        val parts = split(line) ?: return Decoded.Rejected(Reason.FORMAT)
        val (seq, body, mac) = parts
        if (!macEquals(mac, computeMac(peerDir, session, seq, body))) return Decoded.Rejected(Reason.MAC)
        if (seq <= rxSeq) return Decoded.Rejected(Reason.REPLAY)
        if (seq > rxSeq + 1) lostCount += seq - rxSeq - 1
        rxSeq = seq
        return Decoded.Accepted(seq, body)
    }

    val lastSentSeq get() = txSeq

    // ---------- Internos ----------

    private fun frame(seq: Long, body: String, dir: Char, sess: String) = "$seq|$body|${computeMac(dir, sess, seq, body)}"

    fun computeMac(dir: Char, sess: String, seq: Long, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        val full = mac.doFinal("$dir|$sess|$seq|$body".toByteArray(Charsets.US_ASCII))
        return toHex(full.copyOf(MAC_BYTES))
    }

    private fun split(line: String): Triple<Long, String, String>? {
        val clean = line.trimEnd('\r', '\n')
        if (clean.length > MAX_LINE) return null
        val parts = clean.split('|')
        if (parts.size != 3) return null
        val seq = parts[0].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        if (!isValidBody(parts[1]) || !isHex(parts[2], MAC_BYTES * 2)) return null
        return Triple(seq, parts[1], parts[2].lowercase())
    }

    private fun macEquals(a: String, b: String) =
        MessageDigest.isEqual(a.toByteArray(Charsets.US_ASCII), b.toByteArray(Charsets.US_ASCII))

    private fun randomHex(bytes: Int) = toHex(ByteArray(bytes).also { random.nextBytes(it) })

    companion object {
        const val NONCE_BYTES = 8
        const val MAC_BYTES = 8
        const val MAX_LINE = 120
        private val BODY = Regex("^[A-Z]+:[A-Za-z0-9,.-]{0,60}$")

        fun isValidBody(body: String) = BODY.matches(body)
        fun isHex(s: String, len: Int) = s.length == len && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        fun toHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

        /** Convierte la clave en hexadecimal (32 o 64 caracteres) en bytes; null si no es válida. */
        fun keyFromHex(hex: String): ByteArray? {
            val h = hex.trim()
            if (!(isHex(h, 32) || isHex(h, 64))) return null
            return ByteArray(h.length / 2) { i -> h.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }
    }
}
