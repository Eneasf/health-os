#!/usr/bin/env bash
# Health Dashboard Sync Service Manager for macOS launchd

PLIST_NAME="com.healthdashboard.sync.plist"
LABEL="com.healthdashboard.sync"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$(dirname "$SCRIPT_DIR")"
PLIST_SRC="$SCRIPT_DIR/$PLIST_NAME"
PLIST_DEST="$HOME/Library/LaunchAgents/$PLIST_NAME"
LOG_FILE="/tmp/sync_server.log"

case "$1" in
    install)
        echo "Installing LaunchAgent service..."
        mkdir -p "$HOME/Library/LaunchAgents"
        cp "$PLIST_SRC" "$PLIST_DEST"
        launchctl unload "$PLIST_DEST" 2>/dev/null
        launchctl load "$PLIST_DEST"
        echo "✅ Service installed and started as $LABEL"
        echo "Log file: $LOG_FILE"
        ;;
    start)
        echo "Starting service..."
        launchctl load "$PLIST_DEST" 2>/dev/null || launchctl start "$LABEL"
        echo "✅ Service started."
        ;;
    stop)
        echo "Stopping service..."
        launchctl unload "$PLIST_DEST" 2>/dev/null || launchctl stop "$LABEL"
        echo "✅ Service stopped."
        ;;
    status)
        echo "Checking service status..."
        if launchctl list | grep -q "$LABEL"; then
            echo "🟢 Service is RUNNING ($LABEL)"
            launchctl list | grep "$LABEL"
        else
            echo "🔴 Service is NOT running."
        fi
        ;;
    logs)
        echo "Tailing log file ($LOG_FILE)... (Press Ctrl+C to exit)"
        touch "$LOG_FILE"
        tail -n 30 -f "$LOG_FILE"
        ;;
    restart)
        echo "Restarting service..."
        launchctl unload "$PLIST_DEST" 2>/dev/null || launchctl stop "$LABEL" 2>/dev/null
        sleep 1
        launchctl load "$PLIST_DEST" 2>/dev/null || launchctl start "$LABEL"
        echo "✅ Service restarted."
        ;;
    uninstall)
        echo "Uninstalling LaunchAgent service..."
        launchctl unload "$PLIST_DEST" 2>/dev/null
        rm -f "$PLIST_DEST"
        echo "✅ Service uninstalled cleanly."
        ;;
    *)
        echo "Usage: $0 {install|start|stop|restart|status|logs|uninstall}"
        exit 1
        ;;
esac
