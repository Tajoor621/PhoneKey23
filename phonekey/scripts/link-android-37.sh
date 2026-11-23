#!/bin/sh
# compileSdk 37 looks up platforms/android-37. sdkmanager only publishes
# platforms;android-37.0, which installs as platforms/android-37.0.
set -eu
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK_ROOT" ]; then
  echo "ANDROID_SDK_ROOT is not set; cannot alias platforms/android-37" >&2
  exit 1
fi
platforms="$SDK_ROOT/platforms"
installed="$platforms/android-37.0"
alias_path="$platforms/android-37"
if [ ! -d "$installed" ]; then
  echo "platforms/android-37.0 is not installed under $SDK_ROOT" >&2
  exit 1
fi
if [ -L "$alias_path" ] || [ -e "$alias_path" ]; then
  echo "platforms/android-37 already present"
  exit 0
fi
ln -s android-37.0 "$alias_path"
echo "Linked platforms/android-37 -> android-37.0"
