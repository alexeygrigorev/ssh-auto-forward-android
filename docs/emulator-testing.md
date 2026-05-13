# Testing with Android Emulator

## Prerequisites

- Android Studio with emulator (or just command-line tools)
- `ANDROID_HOME` set (typically `~/Android/Sdk`)
- `adb` on PATH or accessible at `$ANDROID_HOME/platform-tools/adb`
- An AVD (Android Virtual Device) running — e.g. Pixel 8, API 35

## Setup (one-time)

1. Start the emulator (or launch from Android Studio):
   ```bash
   $ANDROID_HOME/platform-tools/emulator -avd <avd_name> -no-snapshot-load &
   ```

2. Wait for boot:
   ```bash
   adb wait-for-device
   adb shell getprop sys.boot_completed  # should print "1"
   ```

3. Push your SSH private key to the emulator:
   ```bash
   adb push ~/.ssh/id_hetzner /sdcard/Download/id_hetzner
   ```

## Build & Install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test Flow

### 1. Launch the app
```bash
adb shell am start -n com.sshautoforward.debug/com.sshautoforward.ui.MainActivity
```

### 2. Add a host (manual)
- Tap the + FAB
- Fill in: name, hostname, port, username
- Tap "Upload Key" and select the key from Downloads
- Save

### 3. Connect to a host
- Tap the host card in the list
- Wait for "Connected" status (green dot)
- Ports will appear as they're discovered

### 4. Verify tunnel works
```bash
# Check if a forwarded port is listening inside the emulator
adb shell "nc -z -w 3 127.0.0.1 <port> && echo TUNNEL_OPEN || echo TUNNEL_CLOSED"
```

## Automated Testing (script)

Full cycle: force-stop, clear logs, launch, wait for host list, tap first host, wait for connection, screenshot:

```bash
ADB=~/Android/Sdk/platform-tools/adb

$ADB shell am force-stop com.sshautoforward.debug
sleep 1
$ADB logcat -c
$ADB shell am start -n com.sshautoforward.debug/com.sshautoforward.ui.MainActivity

# Wait for host list to render, then tap first host card (center of screen)
sleep 4
$ADB shell input tap 540 378

# Wait for SSH connection + port scan (20-30s)
sleep 25

# Take screenshot
$ADB shell screencap -p /sdcard/test-result.png
$ADB pull /sdcard/test-result.png /tmp/test-result.png
```

## Reading Logs

```bash
# All SSH-related logs
adb logcat -d | grep -E "SshConnection|AutoForwarder"

# All logs from the app (filter by PID)
adb logcat -d --pid=$(adb shell pidof com.sshautoforward.debug)

# Clear and watch live
adb logcat -c && adb logcat | grep -E "SshConnection|AutoForwarder"
```

## Dumping UI State

When you need to find tap coordinates or read what's on screen:

```bash
# Dump UI hierarchy
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml /tmp/ui.xml

# Extract text elements with coordinates
cat /tmp/ui.xml | python3 -c "
import sys, re
content = sys.stdin.read()
nodes = re.findall(r'<node[^>]*>', content)
for n in nodes:
    bounds = re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', n)
    text = re.search(r'text=\"([^\"]*?)\"', n)
    if bounds and text and text.group(1):
        print(f'[{bounds.group(1)},{bounds.group(2)}] {text.group(1)}')
"
```

## Testing Network Connectivity from Emulator

```bash
# ICMP (ping)
adb shell "ping -c 2 135.181.114.209"

# TCP port check
adb shell "nc -z -w 5 135.181.114.209 22 && echo OPEN || echo CLOSED"
```

Note: the emulator runs in its own QEMU VM. Port forwards created by the app
are on the emulator's localhost, not the host machine's. Use `adb shell` to
test them from inside the emulator.

## Known Issues

- First connection attempt sometimes fails with "Connection refused" — retry works
- JSch requires BouncyCastle (`bcprov-jdk18on`) for ed25519 keys on Android
- `Flow.collect` suspends forever — use `Flow.first()` for one-shot reads
- The emulator's network goes through NAT — high latency (~700ms) is normal

## Pushing Key Directly (bypass file picker)

If you need to push a key directly without using the file picker UI:

```bash
# Push to a temporary location
adb push ~/.ssh/id_hetzner /sdcard/Download/id_hetzner

# Then in the app, use the file picker to select it from Downloads

# Or push directly to the app's private storage (requires knowing the exact path):
adb push ~/.ssh/id_hetzner /data/data/com.sshautoforward.debug/files/ssh-keys/id_hetzner
```

## CI Build

Tag pushes trigger GitHub Actions to build a debug APK and create a release:

```bash
git tag v0.4.0
git push origin v0.4.0
```

Download the APK from: https://github.com/alexeygrigorev/ssh-auto-forward-android/releases
