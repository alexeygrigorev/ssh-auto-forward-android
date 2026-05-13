package com.sshautoforward.ui.hosts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditHostScreen(
    hostId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: AddEditHostViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sshKeys by viewModel.sshKeys.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(hostId) {
        if (hostId != null) viewModel.loadHost(hostId)
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

    val keyPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.loadKeyFromFile(context, it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (hostId == null) "Add Host" else "Edit Host") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.updateState { it.copy(name = v) } },
                label = { Text("Host Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.name.isBlank() && state.error != null,
            )

            OutlinedTextField(
                value = state.hostname,
                onValueChange = { v -> viewModel.updateState { it.copy(hostname = v) } },
                label = { Text("Hostname / IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = { v -> viewModel.updateState { it.copy(port = v) } },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = { v -> viewModel.updateState { it.copy(username = v) } },
                    label = { Text("Username") },
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                )
            }

            var keyDropdownExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = keyDropdownExpanded,
                onExpandedChange = { keyDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = sshKeys.find { it.id == state.selectedKeyId }?.name
                        ?: state.keyFileName
                        ?: "Select or upload key",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyDropdownExpanded) },
                    label = { Text("SSH Private Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = keyDropdownExpanded,
                    onDismissRequest = { keyDropdownExpanded = false },
                ) {
                    sshKeys.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(key.name) },
                            onClick = {
                                viewModel.updateState {
                                    it.copy(selectedKeyId = key.id, keyContent = null, keyFileName = null)
                                }
                                keyDropdownExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { keyPickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Upload Private Key File")
            }

            OutlinedTextField(
                value = state.keyPassphrase,
                onValueChange = { v -> viewModel.updateState { it.copy(keyPassphrase = v) } },
                label = { Text("Key Passphrase (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Advanced Settings", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = state.maxAutoPort,
                    onValueChange = { v -> viewModel.updateState { it.copy(maxAutoPort = v) } },
                    label = { Text("Max Auto Port") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.skipPortsBelow,
                    onValueChange = { v -> viewModel.updateState { it.copy(skipPortsBelow = v) } },
                    label = { Text("Skip Ports Below") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = state.scanInterval,
                onValueChange = { v -> viewModel.updateState { it.copy(scanInterval = v) } },
                label = { Text("Scan Interval (seconds)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Enable auto-forward")
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { v -> viewModel.updateState { it.copy(enabled = v) } },
                )
            }

            state.error?.let {
                Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.save(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (hostId == null) "Add Host" else "Save Changes")
            }
        }
    }
}
