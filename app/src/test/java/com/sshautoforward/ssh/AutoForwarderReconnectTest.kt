package com.sshautoforward.ssh

import com.sshautoforward.data.db.dao.PortRemappingDao
import com.sshautoforward.data.db.entity.HostEntity
import com.sshautoforward.data.db.entity.PortRemappingEntity
import com.sshautoforward.data.repository.PortRemappingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class AutoForwarderReconnectTest {

    private lateinit var autoForwarder: AutoForwarder
    private lateinit var keyPath: String

    @Before
    fun setUp() {
        var keyFile = File("../docker/test_key")
        if (!keyFile.exists()) {
            keyFile = File("docker/test_key")
        }
        keyPath = keyFile.absolutePath
        autoForwarder = AutoForwarder(PortRemappingRepository(FakePortRemappingDao()))
    }

    @After
    fun tearDown() {
        autoForwarder.stop()
        runBlocking {
            cleanupRemoteServer()
        }
    }

    @Test
    fun reconnectRecreatesForwardedPortsAfterNetworkChange() = runBlocking {
        val remotePort = 19997
        cleanupRemoteServer()
        startRemoteServer(remotePort)

        autoForwarder.start(testHost(remotePort), keyPath)
        waitForForward(remotePort)
        waitForLocalPort(remotePort)

        autoForwarder.reconnectNow()
        waitForDisconnected()
        waitForConnectedAfterReconnect()
        waitForForward(remotePort)

        assertTrue(
            "Local port should be open after reconnect when UI reports forwarding",
            isPortOpen(remotePort),
        )
    }

    private fun testHost(remotePort: Int) = HostEntity(
        id = 1,
        name = "Docker test SSH",
        hostname = "127.0.0.1",
        port = 2222,
        username = "root",
        keyId = 1,
        maxAutoPort = remotePort,
        skipPortsBelow = remotePort,
        scanIntervalSec = 1,
    )

    private suspend fun waitForForward(remotePort: Int) {
        waitUntil("port $remotePort to be marked forwarding") {
            autoForwarder.ports.value.any {
                it.remotePort == remotePort &&
                    (it.state == ForwardState.FORWARDING || it.state == ForwardState.FORWARDING_MANUAL)
            }
        }
    }

    private suspend fun waitForConnectedAfterReconnect() {
        waitUntil("forwarder to reconnect") {
            autoForwarder.isConnected.value
        }
    }

    private suspend fun waitForDisconnected() {
        waitUntil("forwarder to disconnect") {
            !autoForwarder.isConnected.value
        }
    }

    private suspend fun waitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000L
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(250)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    private suspend fun startRemoteServer(port: Int) {
        val conn = connect()
        try {
            conn.executeCommand("nohup python3 -m http.server $port --bind 127.0.0.1 > /dev/null 2>&1 &")
            delay(1_000)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun waitForLocalPort(port: Int) {
        waitUntil("local port $port to open") {
            isPortOpen(port)
        }
    }

    private suspend fun cleanupRemoteServer() {
        val conn = connect()
        try {
            conn.executeCommand("pkill -f 'http.server 19997' 2>/dev/null || true")
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun connect(): SshConnection {
        val conn = SshConnection(
            SshConfig(
                hostname = "127.0.0.1",
                port = 2222,
                username = "root",
                privateKeyPath = keyPath,
            )
        )
        conn.connect()
        assertNotNull(conn.getSession())
        return conn
    }

    private fun isPortOpen(port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private class FakePortRemappingDao : PortRemappingDao {
        override fun getByHostId(hostId: Long): Flow<List<PortRemappingEntity>> = flowOf(emptyList())

        override suspend fun getByRemotePort(hostId: Long, remotePort: Int): PortRemappingEntity? = null

        override suspend fun insert(remapping: PortRemappingEntity): Long = 1

        override suspend fun deleteByRemotePort(hostId: Long, remotePort: Int) = Unit

        override suspend fun deleteByHostId(hostId: Long) = Unit
    }
}
