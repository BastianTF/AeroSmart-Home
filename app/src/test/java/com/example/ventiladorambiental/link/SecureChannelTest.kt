package com.example.ventiladorambiental.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del protocolo seguro. Los MAC esperados se calcularon también con Python (hmac + sha256)
 * y con el sketch del Arduino, así se comprueba que ambos lados firman igual.
 * Ejecutar en Android Studio: clic derecho en esta clase > Run 'SecureChannelTest'.
 */
class SecureChannelTest {

    private val key = SecureChannel.keyFromHex("00112233445566778899aabbccddeeff")!!
    private val session = "0102030405060708a1b2c3d4e5f60718"
    private lateinit var app: SecureChannel
    private lateinit var dev: SecureChannel

    @Before
    fun setUp() {
        app = SecureChannel(key, 'A', 'D')
        dev = SecureChannel(key, 'D', 'A')
        assertEquals("0|HELLO:0102030405060708|f3d5a893ed0e6f1b", app.hello("0102030405060708"))
        assertTrue(app.acceptWelcome("0|WELCOME:a1b2c3d4e5f60718|fe1634594beb85ca"))
    }

    private fun fromDevice(seq: Long, body: String) = "$seq|$body|${dev.computeMac('D', session, seq, body)}"

    @Test
    fun commandIsSignedLikePythonAndArduino() {
        assertEquals("1|VENT:1|2452c10d7021754f", app.encode("VENT:1"))
    }

    @Test
    fun validDataIsAccepted() {
        val r = app.decode(fromDevice(1, "DATA:25,55,1,0,60"))
        assertEquals(SecureChannel.Decoded.Accepted(1, "DATA:25,55,1,0,60"), r)
    }

    @Test
    fun tamperedDataIsRejected() {
        val line = fromDevice(1, "DATA:25,55,1,0,60").replace("25,55", "35,55")
        assertEquals(SecureChannel.Decoded.Rejected(SecureChannel.Reason.MAC), app.decode(line))
    }

    @Test
    fun replayIsRejected() {
        val line = fromDevice(1, "DATA:25,55,1,0,60")
        app.decode(line)
        assertEquals(SecureChannel.Decoded.Rejected(SecureChannel.Reason.REPLAY), app.decode(line))
    }

    @Test
    fun gapsCountAsLost() {
        app.decode(fromDevice(1, "DATA:25,55,1,0,60"))
        app.decode(fromDevice(4, "DATA:25,55,1,0,60"))
        assertEquals(2, app.lostCount)
    }

    @Test
    fun welcomeWithWrongKeyIsRejected() {
        val other = SecureChannel(key, 'A', 'D')
        other.hello("0102030405060708")
        assertFalse(other.acceptWelcome("0|WELCOME:a1b2c3d4e5f60718|0000000000000000"))
    }

    @Test
    fun malformedLinesAreRejected() {
        assertEquals(SecureChannel.Decoded.Rejected(SecureChannel.Reason.FORMAT), app.decode("hola"))
        assertEquals(SecureChannel.Decoded.Rejected(SecureChannel.Reason.FORMAT), app.decode("1|DATA:1|2|0011223344556677"))
    }

    @Test
    fun telemetryRejectsImpossibleValues() {
        assertEquals(Telemetry(25, 55, true, false, 60), Telemetry.parse("DATA:25,55,1,0,60"))
        assertNull(Telemetry.parse("DATA:99,55,1,0,60"))
        assertNull(Telemetry.parse("DATA:25,55,1,0"))
    }
}
