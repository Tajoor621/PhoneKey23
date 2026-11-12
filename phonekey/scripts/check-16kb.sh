#!/bin/sh
# Check ELF LOAD alignment of native libraries in an APK.
# 16 KB page-size devices are 64-bit. arm64-v8a and x86_64 must be aligned to
# at least 0x4000. 32-bit ABIs are reported and are not a Play 16 KB failure:
# tunnel 1.0.20260102 ships those at 0x1000, which 16 KB devices do not load.
set -eu
APK=${1:?usage: check-16kb.sh app.apk}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
unzip -q "$APK" -d "$TMP"
found=0
fail=0
# shellcheck disable=SC2044
for so in $(find "$TMP" -name '*.so'); do
  found=1
  rel=${so#"$TMP"/}
  align=$(readelf -lW "$so" | awk '/LOAD/ { print $NF }' | sort -u | tr '\n' ' ')
  echo "$rel LOAD-Align: $align"
  case "$rel" in
    *arm64-v8a*|*x86_64*)
      for value in $align; do
        # 0x4000 is 16 KB. Larger (0x10000) is also fine. 0x1000 is not.
        case "$value" in
          0x4000|0x8000|0x10000) ;;
          *)
            echo "FAIL $rel alignment $value is below 16 KB"
            fail=1
            ;;
        esac
      done
      ;;
    *)
      echo "NOTE $rel is 32-bit; 16 KB devices do not load it"
      ;;
  esac
done
if [ "$found" -eq 0 ]; then
  echo "FAIL no native libraries in $APK"
  exit 1
fi
if [ -n "${ANDROID_HOME:-}" ] && [ -n "${ANDROID_BUILD_TOOLS_VERSION:-}" ]; then
  ZIPALIGN="$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS_VERSION/zipalign"
  if [ -x "$ZIPALIGN" ]; then
    if "$ZIPALIGN" -c -P 16 -v 4 "$APK"; then
      echo "zipalign -P 16 passed"
    else
      echo "NOTE zipalign -P 16 did not pass. extractNativeLibs is true, so the ELF alignment above is the load-time check."
    fi
  fi
fi
exit "$fail"
