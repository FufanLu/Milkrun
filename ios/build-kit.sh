#!/usr/bin/env bash
# Builds MilkrunKit.xcframework from the shared Kotlin modules, then regenerates the Xcode project.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :shared:kit:assembleMilkrunKitDebugXCFramework --console=plain -q
cd ios && xcodegen generate --quiet
