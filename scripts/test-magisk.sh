#!/bin/sh
# Run on a host shell with coreutils; all Android commands below are mocked.
set -eu
script_path=$1
test_root=$2
mkdir -p "$test_root"
scenario=ready

getprop() { printf '1\n'; }
sleep() { :; }
pm() { [ "$scenario" != missing ]; }
am() {
    if [ "$1" = get-started-user-state ]; then
        if [ "$scenario" = locked ]; then printf 'RUNNING_LOCKED\n';
        else printf 'RUNNING_UNLOCKED\n'; fi
    elif [ "$1" = start ]; then
        printf '%s\n' "$*" >> "$test_root/launches"
    else
        return 1
    fi
}
log() { :; }

for scenario in ready missing locked; do
    rm -f "$test_root/launches"
    . "$script_path"
    wait
    if [ "$scenario" = ready ]; then
        [ -f "$test_root/launches" ]
        [ "$(wc -l < "$test_root/launches" | tr -d ' ')" = 1 ]
        grep -q -- '-n com.doublesail.scheduleboard/.MainActivity --ez launched_at_boot true' "$test_root/launches"
    else
        [ ! -f "$test_root/launches" ]
    fi
    printf 'PASS %s\n' "$scenario"
done
