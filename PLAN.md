# SSH Auto Forward for Android - Implementation Plan

## Tech Stack

| Layer | Choice | Reason |
|-------|--------|--------|
| Language | **Kotlin** | Native Android, best interop |
| UI | **Jetpack Compose** | Modern, declarative, matches TUI's dynamic nature |
| SSH | **JSch** | Mature Java SSH library, supports port forwarding natively |
| Min SDK | **API 26** (Android 8.0) | Required for foreground services, NotificationChannel |
| Architecture | **MVVM + Repository** | Standard Android pattern |
| Storage | **Room** (SQLite) | Host configs, key references |
| DI | **Hilt** | Standard Android DI |

## Screens (mirroring the TUI)

1. **Host List Screen** (replaces host selector modal)
   - List of configured hosts with status indicators (connected/disconnected)
   - FAB to add new host
   - Pull to refresh

2. **Add/Edit Host Screen**
   - Hostname/IP, port, username
   - Upload SSH private key (file picker or paste text)
   - Key passphrase (optional)
   - Max auto-forward port (default 10000)
   - Skip ports below (default 1000)
   - Scan interval (default 5s)
   - Enable/disable toggle

3. **Dashboard Screen** (main screen, mirrors TUI dashboard)
   - Header: Connected to `<host>` | Auto-forward ports <= `<max>`
   - Port list table:
     - Remote Port | Local Port | Process | Status | Traffic | Speed
     - Tap row to open URL in browser
     - Long-press for context menu (toggle, remap, open URL)
   - Bottom sheet: log panel (collapsible, like the TUI)
   - Reconnect overlay when connection lost
   - Key bindings replaced by tap/long-press gestures

4. **Settings Screen**
   - Default scan interval
   - Default max auto-port
   - Notification settings
   - About

## Background Service

- **Foreground Service** with persistent notification showing:
  - Active host connection
  - Number of active tunnels
  - Quick disconnect action
- Keeps SSH connection alive when app is backgrounded
- Handles auto-reconnect with exponential backoff (same logic as original)
- **WakeLock** to prevent CPU sleep during active forwarding
- **WifiLock** to maintain network connectivity

## Key Management

- SSH keys stored in app-private storage (`Context.getFilesDir()`)
- Upload via: file picker, paste text, or generate new keypair
- Keys listed in settings with delete option
- Passphrase cached in memory only (never stored)

## Data Model (Room)

```
Host:
  id: Long (auto)
  name: String
  hostname: String
  port: Int (default 22)
  username: String
  keyId: Long (FK -> SshKey)
  maxAutoPort: Int (default 10000)
  skipPortsBelow: Int (default 1000)
  scanInterval: Int (default 5)
  enabled: Boolean
  createdAt: Instant
  lastConnectedAt: Instant?

SshKey:
  id: Long (auto)
  name: String
  privateKeyPath: String (app-private path)
  hasPassphrase: Boolean
  createdAt: Instant

PortRemapping:
  id: Long (auto)
  hostId: Long (FK -> Host)
  remotePort: Int
  localPort: Int
```

## Project Structure

```
ssh-auto-forward-android/
├── app/
│   └── src/main/
│       ├── java/com/sshautoforward/
│       │   ├── App.kt
│       │   ├── di/
│       │   │   ├── AppModule.kt
│       │   │   ├── DatabaseModule.kt
│       │   │   └── SshModule.kt
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── AppDatabase.kt
│       │   │   │   ├── dao/HostDao.kt, SshKeyDao.kt, PortRemappingDao.kt
│       │   │   │   └── entity/HostEntity.kt, SshKeyEntity.kt, PortRemappingEntity.kt
│       │   │   └── repository/
│       │   │       ├── HostRepository.kt
│       │   │       ├── SshKeyRepository.kt
│       │   │       └── PortRemappingRepository.kt
│       │   ├── ssh/
│       │   │   ├── SshConnection.kt        # JSch session management
│       │   │   ├── SshTunnel.kt            # Single port forward tunnel
│       │   │   ├── PortScanner.kt          # Remote port discovery (ss -tlnp)
│       │   │   └── AutoForwarder.kt        # Core engine (scan + forward loop)
│       │   ├── service/
│       │   │   └── ForwardingService.kt    # Foreground service
│       │   ├── ui/
│       │   │   ├── navigation/
│       │   │   │   └── NavGraph.kt
│       │   │   ├── theme/
│       │   │   │   └── Theme.kt, Color.kt, Type.kt
│       │   │   ├── hosts/
│       │   │   │   ├── HostListScreen.kt
│       │   │   │   ├── HostListViewModel.kt
│       │   │   │   ├── AddEditHostScreen.kt
│       │   │   │   └── AddEditHostViewModel.kt
│       │   │   ├── dashboard/
│       │   │   │   ├── DashboardScreen.kt
│       │   │   │   ├── DashboardViewModel.kt
│       │   │   │   ├── PortTable.kt
│       │   │   │   └── LogPanel.kt
│       │   │   └── settings/
│       │   │       ├── SettingsScreen.kt
│       │   │       └── KeyManagementScreen.kt
│       │   └── util/
│       │       ├── TrafficStats.kt
│       │       └── Extensions.kt
│       ├── res/
│       │   ├── values/strings.xml, themes.xml
│       │   └── drawable/
│       └── AndroidManifest.xml
│       └── build.gradle.kts
├── build.gradle.kts (project)
├── settings.gradle.kts
└── gradle.properties
```

## Implementation Order (Phases)

### Phase 1 - Foundation
1. Android project scaffold (Gradle, Compose, Hilt, Room)
2. Data layer (entities, DAOs, database, repositories)
3. SSH key management (upload, storage, listing)

### Phase 2 - SSH Core
4. `SshConnection` - JSch session connect/disconnect/reconnect
5. `PortScanner` - execute `ss -tlnp` over SSH, parse output (reuse Python parsing logic)
6. `SshTunnel` - single port forward with traffic stats
7. `AutoForwarder` - scan loop, auto-forward, detect closed ports

### Phase 3 - Service
8. `ForwardingService` - foreground service, notification, lifecycle

### Phase 4 - UI
9. Navigation scaffold + theme
10. Host list screen (add/edit/delete hosts)
11. Dashboard screen (port table, traffic, status)
12. Log panel (bottom sheet)
13. Reconnect overlay
14. Port toggle (tap) and remap (long-press dialog)

### Phase 5 - Polish
15. Auto-start on boot (optional)
16. Quick settings tile (toggle forwarding)
17. Widget (show active tunnels count)
18. Crash handling, error reporting

## Key Differences from Desktop Version

| Aspect | Desktop (Python) | Android (Kotlin) |
|--------|-----------------|-------------------|
| Config source | `~/.ssh/config` | Room database (custom UI) |
| Key management | Filesystem `~/.ssh/` | App-private storage + file picker |
| Background | Terminal process | Foreground service + notification |
| UI input | Keyboard (Q/R/L/O/X/M) | Touch (tap/long-press/swipe) |
| URL opening | `webbrowser.open()` | `Intent(ACTION_VIEW)` |
| Port scanning | `paramiko.exec_command()` | `JSch.execCommand()` |
| Tunneling | `transport.open_channel("direct-tcpip")` | `session.setPortForwardingL()` |
