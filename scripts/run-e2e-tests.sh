#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
EMULATOR="${ANDROID_HOME:-$HOME/Android/Sdk}/emulator/emulator"
AVD_NAME="${AVD_NAME:-test}"
DOCKER_DIR="$PROJECT_DIR/docker"
CONTAINER_NAME="ssh-auto-forward-test"
TIMEOUT=120

log() { echo "[$(date +%H:%M:%S)] $*"; }

wait_for_emulator() {
    log "Waiting for emulator to boot..."
    local elapsed=0
    while [ $elapsed -lt $TIMEOUT ]; do
        local boot_done
        boot_done=$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
        if [ "$boot_done" = "1" ]; then
            log "Emulator booted."
            return 0
        fi
        sleep 3
        elapsed=$((elapsed + 3))
    done
    log "ERROR: Emulator did not boot within ${TIMEOUT}s"
    return 1
}

start_docker() {
    if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log "Docker container already running."
        return 0
    fi
    log "Starting Docker SSH test server..."
    docker compose -f "$DOCKER_DIR/docker-compose.yml" up -d --build
    sleep 2
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log "ERROR: Docker container failed to start"
        return 1
    fi
    log "Docker SSH server running on localhost:2222"
}

start_emulator() {
    if $ADB devices 2>/dev/null | grep -q "emulator"; then
        log "Emulator already running."
        return 0
    fi
    log "Starting emulator (AVD: $AVD_NAME)..."
    setsid $EMULATOR -avd "$AVD_NAME" -no-snapshot -no-audio -no-window \
        -gpu swiftshader_indirect -memory 2048 &>/tmp/emulator.log &
    disown
    wait_for_emulator
}

run_tests() {
    log "Building and running instrumented tests..."
    cd "$PROJECT_DIR"
    ./gradlew connectedDebugAndroidTest
}

cleanup() {
    log "Stopping Docker container..."
    docker compose -f "$DOCKER_DIR/docker-compose.yml" down 2>/dev/null || true
}

trap cleanup EXIT

log "=== E2E Test Runner ==="
start_docker
start_emulator
run_tests
log "=== All tests passed ==="
