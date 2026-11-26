package com.example.ventiladorambiental.link

/** Lectura que envía el Arduino: "DATA:temp,hum,pir,compuerta,turbina". Valida rangos antes de aceptarla. */
data class Telemetry(val temperature: Int, val humidity: Int, val movement: Boolean, val ventOpen: Boolean, val fanPercent: Int) {
    companion object {
        fun parse(body: String): Telemetry? {
            if (!body.startsWith("DATA:")) return null
            val p = body.removePrefix("DATA:").split(',')
            if (p.size != 5) return null
            val t = p[0].toIntOrNull() ?: return null
            val h = p[1].toIntOrNull() ?: return null
            val pir = p[2].toIntOrNull() ?: return null
            val vent = p[3].toIntOrNull() ?: return null
            val fan = p[4].toIntOrNull() ?: return null
            // DHT11: 0–50 °C y 20–90 % según su hoja de datos; se deja margen y se descartan valores imposibles.
            if (t !in -10..60 || h !in 0..100 || pir !in 0..1 || vent !in 0..1 || fan !in 0..100) return null
            return Telemetry(t, h, pir == 1, vent == 1, fan)
        }
    }
}

/**
 * Lectura que la placa guardó mientras la app estaba desconectada: "HIST:edad_s,temp,hum,pir,compuerta".
 * La placa no tiene reloj real, por eso manda la edad en segundos y la app calcula la hora.
 */
data class HistoryReading(val ageSeconds: Long, val temperature: Int, val humidity: Int, val movement: Boolean, val ventOpen: Boolean) {
    fun timestamp(nowMs: Long): Long = nowMs - ageSeconds * 1000

    companion object {
        /** Máximo 1 día: el búfer de la placa guarda 1 hora, más que eso es un dato corrupto. */
        private const val MAX_AGE_S = 86_400L

        fun parse(body: String): HistoryReading? {
            if (!body.startsWith("HIST:")) return null
            val p = body.removePrefix("HIST:").split(',')
            if (p.size != 5) return null
            val age = p[0].toLongOrNull() ?: return null
            val t = p[1].toIntOrNull() ?: return null
            val h = p[2].toIntOrNull() ?: return null
            val pir = p[3].toIntOrNull() ?: return null
            val vent = p[4].toIntOrNull() ?: return null
            if (age !in 0..MAX_AGE_S || t !in -10..60 || h !in 0..100 || pir !in 0..1 || vent !in 0..1) return null
            return HistoryReading(age, t, h, pir == 1, vent == 1)
        }
    }
}

/** Estado sin temperatura/humedad (cuando falla el DHT11): "STATE:pir,compuerta,turbina". */
data class DeviceState(val movement: Boolean, val ventOpen: Boolean, val fanPercent: Int) {
    companion object {
        fun parse(body: String): DeviceState? {
            if (!body.startsWith("STATE:")) return null
            val p = body.removePrefix("STATE:").split(',')
            if (p.size != 3) return null
            val pir = p[0].toIntOrNull() ?: return null
            val vent = p[1].toIntOrNull() ?: return null
            val fan = p[2].toIntOrNull() ?: return null
            if (pir !in 0..1 || vent !in 0..1 || fan !in 0..100) return null
            return DeviceState(pir == 1, vent == 1, fan)
        }
    }
}

/** Órdenes que la app envía al Arduino. */
object Commands {
    fun vent(open: Boolean) = "VENT:${if (open) 1 else 0}"
    fun fan(percent: Int) = "FAN:${percent.coerceIn(0, 100)}"
    fun mode(auto: Boolean) = "MODE:${if (auto) "A" else "M"}"
    fun ping(millis: Long) = "PING:$millis"
    fun isCommand(body: String) = body.startsWith("VENT:") || body.startsWith("FAN:") || body.startsWith("MODE:")
}

/** Calidad del enlace para la tarjeta "Estabilidad de la conexión". */
data class LinkQuality(
    val rttMs: Long? = null,
    val sent: Long = 0,
    val received: Long = 0,
    val rejected: Long = 0,
    val lost: Long = 0,
    val retries: Long = 0,
    val reconnects: Int = 0
) {
    val lossPercent: Int
        get() = if (received + lost == 0L) 0 else ((lost * 100) / (received + lost)).toInt()

    val label: String
        get() = when {
            received == 0L -> "Sin datos aún"
            lossPercent <= 2 && (rttMs ?: 0) < 300 -> "Estable"
            lossPercent <= 10 -> "Aceptable"
            else -> "Inestable"
        }
}
