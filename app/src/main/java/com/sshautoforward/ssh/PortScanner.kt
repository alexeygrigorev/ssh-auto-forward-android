package com.sshautoforward.ssh

import android.util.Log

data class RemotePort(
    val port: Int,
    val processName: String,
)

object PortScanner {

    private const val TAG = "PortScanner"

    suspend fun scanRemotePorts(connection: SshConnection): List<RemotePort> {
        return tryPrimaryMethod(connection)
            ?: tryFallbackMethod(connection)
            ?: tryLastResort(connection)
            ?: emptyList()
    }

    private suspend fun tryPrimaryMethod(connection: SshConnection): List<RemotePort>? {
        return try {
            val output = connection.executeCommand("ss -tlnp 2>/dev/null | awk 'NR>1 {print \$4, \$7}'")
            if (output.isBlank()) return null
            parseSsOutput(output)
        } catch (e: Exception) {
            Log.w(TAG, "Primary scan method failed: ${e.message}")
            null
        }
    }

    private suspend fun tryFallbackMethod(connection: SshConnection): List<RemotePort>? {
        return try {
            val output = connection.executeCommand("netstat -tlnp 2>/dev/null | awk 'NR>1 && /LISTEN/ {print \$4, \$7}'")
            if (output.isBlank()) return null
            parseNetstatOutput(output)
        } catch (e: Exception) {
            Log.w(TAG, "Fallback scan method failed: ${e.message}")
            null
        }
    }

    private suspend fun tryLastResort(connection: SshConnection): List<RemotePort>? {
        return try {
            val output = connection.executeCommand("ss -tln 2>/dev/null | awk 'NR>1 {print \$4}'")
            if (output.isBlank()) return null
            parsePortsOnly(output)
        } catch (e: Exception) {
            Log.w(TAG, "Last resort scan failed: ${e.message}")
            null
        }
    }

    private fun parseSsOutput(output: String): List<RemotePort> {
        return output.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex(), limit = 2)
                if (parts.isEmpty()) return@mapNotNull null

                val port = extractPort(parts[0]) ?: return@mapNotNull null
                val processName = if (parts.size > 1) extractProcessName(parts[1]) else ""
                RemotePort(port, processName)
            }
            .toList()
    }

    private fun parseNetstatOutput(output: String): List<RemotePort> {
        return output.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex(), limit = 2)
                if (parts.isEmpty()) return@mapNotNull null

                val port = extractPort(parts[0]) ?: return@mapNotNull null
                val processName = if (parts.size > 1) extractNetstatProcess(parts[1]) else ""
                RemotePort(port, processName)
            }
            .toList()
    }

    private fun parsePortsOnly(output: String): List<RemotePort> {
        return output.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> extractPort(line.trim())?.let { RemotePort(it, "") } }
            .toList()
    }

    private fun extractPort(addressField: String): Int? {
        val colonIndex = addressField.lastIndexOf(':')
        if (colonIndex < 0) return null
        return addressField.substring(colonIndex + 1).toIntOrNull()
    }

    private fun extractProcessName(processField: String): String {
        val patterns = listOf(
            Regex("""users:\(\("([^"]+)""""),
            Regex("""users:\("([^"]+)""""),
        )
        for (pattern in patterns) {
            pattern.find(processField)?.groupValues?.get(1)?.let { return it }
        }
        return ""
    }

    private fun extractNetstatProcess(processField: String): String {
        val parts = processField.split("/")
        if (parts.size >= 2) {
            val name = parts[1].split(",")[0].trim()
            if (name.isNotBlank()) return name
        }
        return ""
    }
}
