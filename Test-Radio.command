#!/bin/bash
# Move to the directory where this script is located
cd "$(dirname "$0")"

ADB="/Users/familyimac/Library/Android/sdk/platform-tools/adb"

echo "--- Light Phone III Radio Tool Tester ---"

# Check if a device is connected
if ! $ADB get-state 1>/dev/null 2>&1; then
    echo "ERROR: No emulator or device found. Please start your emulator first!"
    exit 1
fi

echo "1. Installing LightOS Shell (Emulator)..."
$ADB install -r release/lightos-emulator.apk

echo "2. Installing Radio Tool..."
$ADB install -r release/radio-tool.apk

echo "3. Launching Radio Tool..."
$ADB shell am start -n com.thelightphone.radio/com.thelightphone.sdk.LightActivity

echo "--- SUCCESS! The Radio tool should be open on your phone screen. ---"
# Keep terminal open briefly so user can see success
sleep 3
