package com.sshautoforward.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sshautoforward.ssh.PortScanner
import com.sshautoforward.ssh.SshConfig
import com.sshautoforward.ssh.SshConnection
import com.sshautoforward.ssh.SshTunnel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class E2eTest {

    companion object {
        private const val HOST = "10.0.2.2"
        private const val PORT = 2222
        private const val USER = "root"
    }

    private lateinit var keyPath: String
    private var connection: SshConnection? = null

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val keyFile = File(targetContext.filesDir, "test_key")
        testContext.assets.open("test_key").use { input ->
            keyFile.outputStream().use { output -> input.copyTo(output) }
        }
        keyFile.setReadable(true, true)
        keyPath = keyFile.absolutePath
    }

    @After
    fun tearDown() {
        connection?.disconnect()
        connection = null
    }

    private fun connect(): SshConnection {
        val config = SshConfig(
            hostname = HOST,
            port = PORT,
            username = USER,
            privateKeyPath = keyPath,
        )
        val conn = SshConnection(config)
        runBlocking { conn.connect() }
        connection = conn
        return conn
    }

    @Test
    fun test1_connectAndExecuteCommand() {
        val conn = connect()
        assertTrue("Should be connected", conn.isConnected)

        val output = runBlocking { conn.executeCommand("echo hello") }
        assertEquals("hello\n", output)
    }

    @Test
    fun test2_portScannerDiscoversPorts() {
        val conn = connect()
        runBlocking {
            conn.executeCommand("nohup python3 -m http.server 19999 --bind 127.0.0.1 > /dev/null 2>&1 &")
            Thread.sleep(2000)

            val ports = PortScanner.scanRemotePorts(conn)
            val found = ports.any { it.port == 19999 }
            assertTrue(
                "Should discover port 19999, found: ${ports.map { it.port }}",
                found,
            )

            conn.executeCommand("pkill -f 'http.server 19999' 2>/dev/null || true")
        }
    }

    @Test
    fun test3_portForwardingTunnel() {
        val conn = connect()
        val session = conn.getSession()!!

        runBlocking {
            conn.executeCommand("nohup python3 -m http.server 19998 --bind 127.0.0.1 > /dev/null 2>&1 &")
            Thread.sleep(2000)
        }

        val tunnel = SshTunnel(session, remotePort = 19998, localPort = 19998)
        tunnel.start()
        assertTrue("Tunnel should be active", tunnel.isActive)

        try {
            Thread.sleep(500)
            assertTrue(
                "Local port 19998 should be open after forwarding",
                isPortOpen("127.0.0.1", 19998),
            )
        } finally {
            tunnel.stop()
            runBlocking {
                conn.executeCommand("pkill -f 'http.server 19998' 2>/dev/null || true")
            }
        }
    }

    @Test
    fun test4_multipleTunnels() {
        val conn = connect()
        val session = conn.getSession()!!

        runBlocking {
            conn.executeCommand("nohup python3 -m http.server 19990 --bind 127.0.0.1 > /dev/null 2>&1 &")
            conn.executeCommand("nohup python3 -m http.server 19991 --bind 127.0.0.1 > /dev/null 2>&1 &")
            Thread.sleep(2000)
        }

        val tunnel1 = SshTunnel(session, remotePort = 19990, localPort = 19990)
        val tunnel2 = SshTunnel(session, remotePort = 19991, localPort = 19991)
        tunnel1.start()
        tunnel2.start()

        try {
            assertTrue("Tunnel 1 should be active", tunnel1.isActive)
            assertTrue("Tunnel 2 should be active", tunnel2.isActive)

            assertTrue("Port 19990 should be open", isPortOpen("127.0.0.1", 19990))
            assertTrue("Port 19991 should be open", isPortOpen("127.0.0.1", 19991))
        } finally {
            tunnel1.stop()
            tunnel2.stop()
            runBlocking {
                conn.executeCommand("pkill -f 'http.server 1999' 2>/dev/null || true")
            }
        }
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 3000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
