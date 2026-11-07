#!/usr/bin/env bash
# 构建 → 对齐 → AOSP testkey 签名。
#
# 为什么必须用 testkey：设备上的系统安装器 /system_ext/priv-app/PackageInstaller
# 就是用它签的，签名不一致的包无法作为系统应用的更新安装（会报签名冲突）。
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/c/Users/sakur/.jdks/jbr-21.0.11}"
SDK="${ANDROID_SDK_ROOT:-/c/Users/sakur/AppData/Local/Android/Sdk}"
BT="$SDK/build-tools/36.0.0"
KEYS="${KEYS_DIR:-/d/APK/keys}"

./gradlew :composeApp:assembleRelease --console=plain -q

VERSION=$(grep -oP 'versionName = "\K[^"]+' composeApp/build.gradle.kts)
CODE=$(grep -oP 'versionCode = \K\d+' composeApp/build.gradle.kts)
OUT="out/PUIInstaller-${VERSION}-${CODE}.apk"
mkdir -p out

# -P 16：按 16 KB 页对齐（16 KB 页设备上未压缩的 .so 必须落在 16 KB 边界，
# 否则装得上但一加载就崩）。旧的 -p 4 只保证 4 KB。
"$BT/zipalign.exe" -f -P 16 4 \
  composeApp/build/outputs/apk/release/composeApp-release.apk \
  out/aligned.apk

"$BT/apksigner.bat" sign \
  --key "$KEYS/testkey.pk8" \
  --cert "$KEYS/testkey.x509.pem" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$OUT" \
  out/aligned.apk

rm -f out/aligned.apk
"$BT/apksigner.bat" verify --print-certs "$OUT" | head -3

# 16 KB 页对齐自检：不通过就别交付
if "$BT/zipalign.exe" -c -P 16 -v 4 "$OUT" > /dev/null 2>&1; then
  echo "16 KB alignment: OK"
else
  echo "16 KB alignment: FAILED" >&2
  exit 1
fi

echo "APK: $OUT"
