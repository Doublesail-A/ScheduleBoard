#!/system/bin/sh
# Magisk runs service.sh in late_start service mode, as root.
MODDIR=${0%/*}

(
    attempts=0
    while [ "$(getprop sys.boot_completed)" != "1" ]; do
        [ -f "$MODDIR/disable" ] && exit 0
        [ -f "$MODDIR/remove" ] && exit 0
        attempts=$((attempts + 1))
        [ "$attempts" -ge 180 ] && exit 0
        sleep 2
    done

    # Give the launcher and user storage a moment to settle.
    sleep 8
    [ -f "$MODDIR/disable" ] && exit 0
    [ -f "$MODDIR/remove" ] && exit 0
    if ! pm path --user 0 com.doublesail.scheduleboard >/dev/null 2>&1; then
        log -t ScheduleBoardBoot "App is not installed for user 0; skipping."
        exit 0
    fi
    # Credential-encrypted DataStore needs the primary user to be unlocked.
    # A PIN/password is never dismissed by this module.
    attempts=0
    until [ "$(am get-started-user-state 0 2>/dev/null)" = "RUNNING_UNLOCKED" ]; do
        [ -f "$MODDIR/disable" ] && exit 0
        [ -f "$MODDIR/remove" ] && exit 0
        attempts=$((attempts + 1))
        [ "$attempts" -ge 300 ] && exit 0
        sleep 2
    done
    if am start --user 0 -W \
        -n com.doublesail.scheduleboard/.MainActivity \
        --ez launched_at_boot true >/dev/null 2>&1; then
        log -t ScheduleBoardBoot "Boot launch requested."
    else
        log -t ScheduleBoardBoot "Boot launch failed. Check installation and user unlock."
    fi
) &
