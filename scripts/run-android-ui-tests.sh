#!/usr/bin/env bash
# Run from anywhere with a disposable emulator already booted. Preserve Gradle's status even
# if diagnostics cannot be downloaded (UTP may have removed the target APK by this point).
set -uo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
status=0
(
    cd "$repo_dir/src" || exit 1
    "${GRADLE_BIN:-gradle}" --no-daemon --console=plain --stacktrace --info \
        -Pandroid.testInstrumentationRunnerArguments.e2eScreenshots=true \
        :app:connectedDebugAndroidTest "$@"
) || status=$?

artifacts_dir="$repo_dir/src/app/build/outputs/e2e-artifacts"
if mkdir -p "$artifacts_dir"; then
    if ! "${ADB_BIN:-adb}" pull /sdcard/dice-thrower-e2e-artifacts "$artifacts_dir/"; then
        printf '%s\n' 'Warning: could not collect native E2E artifacts; keeping the Android test result.' >&2
    fi
else
    printf '%s\n' 'Warning: could not create the E2E artifacts directory; keeping the Android test result.' >&2
fi

exit "$status"
