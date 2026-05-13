package com.sshautoforward.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class SshConnectionTest {

    companion object {
        private const val HOST = "127.0.0.1"
        private const val PORT = 2222
        private const val USER = "root"
        private lateinit var KEY_PATH: String

        @JvmStatic
        @BeforeClass
        fun setUp() {
            var keyFile = File("../docker/test_key")
            if (!keyFile.exists()) {
                keyFile = File("docker/test_key")
            }
            if (!keyFile.exists()) {
                throw IllegalStateException(
                    "Test key not found. Searched: ../docker/test_key, docker/test_key"
                )
            }
            KEY_PATH = keyFile.absolutePath

            JSch.setLogger(object : Logger {
                override fun isEnabled(level: Int): Boolean = true
                override fun log(level: Int, message: String) {
                    println("[JSch $level] $message")
                }
            })
        }
    }

    private fun makeConfig() = SshConfig(
        hostname = HOST,
        port = PORT,
        username = USER,
        privateKeyPath = KEY_PATH,
    )

    @Test
    fun testConnectAndExecuteCommand() {
        runBlocking {
            val conn = SshConnection(makeConfig())
            try {
                val session = conn.connect()
                assertNotNull(session)
                assertTrue(conn.isConnected)

                val output = conn.executeCommand("echo hello")
                assertEquals("hello\n", output)
            } finally {
                conn.disconnect()
            }
        }
    }

    @Test
    fun testExecuteSsTlnp() {
        runBlocking {
            val conn = SshConnection(makeConfig())
            try {
                conn.connect()
                val output = conn.executeCommand("ss -tlnp 2>/dev/null | head -5")
                println("ss output:\n$output")
                assertTrue("ss should return some output", output.isNotBlank())
            } finally {
                conn.disconnect()
            }
        }
    }

    @Test
    fun testPortScannerDiscoversPorts() {
        runBlocking {
            val conn = SshConnection(makeConfig())
            try {
                conn.connect()
                conn.executeCommand("nohup python3 -m http.server 19999 --bind 127.0.0.1 > /dev/null 2>&1 &")
                Thread.sleep(2000)

                val ports = PortScanner.scanRemotePorts(conn)
                println("Discovered ports: $ports")

                val found = ports.any { it.port == 19999 }
                assertTrue("Should discover port 19999, found: ${ports.map { it.port }}", found)

                conn.executeCommand("pkill -f 'http.server 19999' 2>/dev/null || true")
            } finally {
                conn.disconnect()
            }
        }
    }

    @org.junit.Ignore("JSch port forwarding needs async handling")
    @Test
    fun testPortForwarding() {
        runBlocking {
            val conn = SshConnection(makeConfig())
            try {
                val session = conn.connect()

                conn.executeCommand("nohup python3 -m http.server 19998 --bind 127.0.0.1 > /dev/null 2>&1 &")
                Thread.sleep(1000)

                val tunnel = SshTunnel(session, remotePort = 19998, localPort = 19998)
                tunnel.start()
                assertTrue(tunnel.isActive)

                Thread.sleep(500)

                val url = java.net.URL("http://127.0.0.1:19998/")
                val httpConn = url.openConnection() as java.net.HttpURLConnection
                httpConn.connectTimeout = 5000
                httpConn.readTimeout = 5000
                val response = httpConn.inputStream.bufferedReader().readText()
                println("Response from forwarded port: ${response.take(200)}")
                assertTrue(
                    "Should get HTTP response",
                    response.contains("Directory listing") || response.contains("html")
                )

                tunnel.stop()
            } finally {
                conn.executeCommand("pkill -f 'http.server 19998' 2>/dev/null || true")
                conn.disconnect()
            }
        }
    }
}
