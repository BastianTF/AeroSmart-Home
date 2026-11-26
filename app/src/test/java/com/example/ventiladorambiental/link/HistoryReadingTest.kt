package com.example.ventiladorambiental.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lecturas que la placa guardó sin conexión (criterios 3.1.1.4 y 3.1.2.7). */
class HistoryReadingTest {

    @Test
    fun validHistoryIsParsed() {
        val h = HistoryReading.parse("HIST:90,24,61,1,0")!!
        assertEquals(HistoryReading(90, 24, 61, movement = true, ventOpen = false), h)
        assertEquals(1_000_000L - 90_000L, h.timestamp(1_000_000L))
    }

    @Test
    fun impossibleValuesAreRejected() {
        assertNull(HistoryReading.parse("HIST:90,99,61,1,0"))     // temperatura fuera de rango
        assertNull(HistoryReading.parse("HIST:90,24,150,1,0"))    // humedad fuera de rango
        assertNull(HistoryReading.parse("HIST:-5,24,61,1,0"))     // edad negativa
        assertNull(HistoryReading.parse("HIST:999999,24,61,1,0")) // más de 1 día
        assertNull(HistoryReading.parse("HIST:90,24,61,1"))       // faltan campos
        assertNull(HistoryReading.parse("DATA:24,61,1,0,60"))     // no es historial
    }

    @Test
    fun liveDataIsNotHistory() {
        assertNull(Telemetry.parse("HIST:90,24,61,1,0"))
    }
}
