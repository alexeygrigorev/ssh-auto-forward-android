package com.sshautoforward.ui.hosts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshautoforward.data.db.entity.HostEntity
import com.sshautoforward.data.db.entity.SshKeyEntity
import com.sshautoforward.data.repository.HostRepository
import com.sshautoforward.data.repository.SshKeyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class HostFormState(
    val name: String = "",
    val hostname: String = "",
    val port: String = "22",
    val username: String = "",
    val selectedKeyId: Long? = null,
    val maxAutoPort: String = "10000",
    val skipPortsBelow: String = "1000",
    val scanInterval: String = "5",
    val enabled: Boolean = false,
    val keyContent: String? = null,
    val keyPassphrase: String = "",
    val keyFileName: String? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AddEditHostViewModel @Inject constructor(
    private val hostRepository: HostRepository,
    private val sshKeyRepository: SshKeyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HostFormState())
    val state: StateFlow<HostFormState> = _state.asStateFlow()

    private var editingHostId: Long? = null

    val sshKeys: StateFlow<List<SshKeyEntity>> = sshKeyRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadHost(hostId: Long) {
        editingHostId = hostId
        viewModelScope.launch {
            val host = hostRepository.getById(hostId) ?: return@launch
            _state.value = _state.value.copy(
                name = host.name,
                hostname = host.hostname,
                port = host.port.toString(),
                username = host.username,
                selectedKeyId = host.keyId,
                maxAutoPort = host.maxAutoPort.toString(),
                skipPortsBelow = host.skipPortsBelow.toString(),
                scanInterval = host.scanIntervalSec.toString(),
                enabled = host.enabled,
            )
        }
    }

    fun updateState(update: (HostFormState) -> HostFormState) {
        _state.value = update(_state.value)
    }

    fun save(context: Context) {
        val s = _state.value
        if (s.name.isBlank() || s.hostname.isBlank() || s.username.isBlank()) {
            _state.value = s.copy(error = "Name, hostname and username are required")
            return
        }

        viewModelScope.launch {
            val keyId = s.selectedKeyId ?: createKey(context)

            val host = HostEntity(
                id = editingHostId ?: 0,
                name = s.name.trim(),
                hostname = s.hostname.trim(),
                port = s.port.toIntOrNull() ?: 22,
                username = s.username.trim(),
                keyId = keyId ?: return@launch,
                maxAutoPort = s.maxAutoPort.toIntOrNull() ?: 10000,
                skipPortsBelow = s.skipPortsBelow.toIntOrNull() ?: 1000,
                scanIntervalSec = s.scanInterval.toIntOrNull() ?: 5,
                enabled = s.enabled,
            )

            if (editingHostId != null) {
                hostRepository.update(host)
            } else {
                hostRepository.insert(host)
            }
            _state.value = _state.value.copy(saved = true)
        }
    }

    private suspend fun createKey(context: Context): Long? {
        val s = _state.value
        val content = s.keyContent ?: return s.selectedKeyId
        val keyName = s.keyFileName ?: "key-${UUID.randomUUID()}"

        val keyDir = File(context.filesDir, "ssh-keys")
        keyDir.mkdirs()
        val keyFile = File(keyDir, keyName)
        keyFile.writeText(content)

        val entity = SshKeyEntity(
            name = keyName,
            privateKeyPath = keyFile.absolutePath,
            hasPassphrase = s.keyPassphrase.isNotBlank(),
        )
        return sshKeyRepository.insert(entity)
    }

    fun loadKeyFromFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: return@launch

                val fileName = getFileName(context, uri)

                _state.value = _state.value.copy(
                    keyContent = content,
                    keyFileName = fileName,
                    selectedKeyId = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to read key file: ${e.message}")
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment?.substringAfterLast("/") ?: "uploaded-key"
    }
}
