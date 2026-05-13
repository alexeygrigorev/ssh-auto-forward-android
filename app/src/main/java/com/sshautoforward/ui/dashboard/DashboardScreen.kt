package com.sshautoforward.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sshautoforward.ssh.ForwardState
import com.sshautoforward.ssh.PortForwardStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    hostId: Long,
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ports by viewModel.ports.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.host?.name ?: "Dashboard")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(8.dp),
                                tint = when {
                                    state.isConnected -> Color(0xFF4CAF50)
                                    state.lastError != null -> Color(0xFFF44336)
                                    else -> Color(0xFFFFC107)
                                },
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                when {
                                    state.isConnected -> "Connected"
                                    state.lastError != null -> "Error: ${state.lastError}"
                                    else -> "Connecting..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isRunning) {
                        IconButton(onClick = {
                            viewModel.disconnect()
                            onNavigateBack()
                        }) {
                            Icon(Icons.Default.Stop, contentDescription = "Disconnect")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.host?.let { host ->
                Text(
                    "Auto-forward ports <= ${host.maxAutoPort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (ports.isNotEmpty()) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(ports, key = { it.remotePort }) { port ->
                        PortRow(
                            port = port,
                            onToggle = { viewModel.togglePort(port.remotePort) },
                            onOpenUrl = {
                                val url = "http://127.0.0.1:${port.localPort}"
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            },
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.isConnected) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scanning ports...", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Connecting...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (logMessages.isNotEmpty()) {
                LogPanel(messages = logMessages)
            }

            if (state.isRunning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Running in background",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            viewModel.disconnect()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.wrapContentWidth(),
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortRow(
    port: PortForwardStatus,
    onToggle: () -> Unit,
    onOpenUrl: () -> Unit,
) {
    val bgColor by animateColorAsState(
        when (port.state) {
            ForwardState.FORWARDING, ForwardState.FORWARDING_MANUAL ->
                MaterialTheme.colorScheme.primaryContainer
            ForwardState.AVAILABLE ->
                MaterialTheme.colorScheme.surfaceVariant
            ForwardState.STOPPED ->
                MaterialTheme.colorScheme.errorContainer
        },
        label = "portBg",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${port.remotePort}",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
            )
            if (port.localPort != port.remotePort) {
                Text(
                    "→${port.localPort}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                port.processName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (port.state == ForwardState.FORWARDING ||
                port.state == ForwardState.FORWARDING_MANUAL
            ) {
                val traffic = formatBytes(port.bytesForwarded + port.bytesReceived)
                Text(
                    traffic,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (port.state == ForwardState.FORWARDING || port.state == ForwardState.FORWARDING_MANUAL) {
                IconButton(onClick = onOpenUrl, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open URL", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(18.dp))
                }
            } else if (port.state == ForwardState.AVAILABLE) {
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
