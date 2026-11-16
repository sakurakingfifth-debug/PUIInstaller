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

# 密钥必须存在，缺了就直接停 —— 不然会签出一个「能装但签名不对」的包
for f in testkey.pk8 testkey.x509.pem; do
  [ -f "$KEYS/$f" ] || { echo "缺少签名密钥：$KEYS/$f" >&2; exit 1; }
done

"$BT/apksigner.bat" sign \
  --key "$KEYS/testkey.pk8" \
  --cert "$KEYS/testkey.x509.pem" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled false \
  --out "$OUT" \
  out/aligned.apk

rm -f out/aligned.apk
# ── 签名自检 ───────────────────────────────────────────────────────────────
# 每次构建都把证书指纹打出来，并和仓库里的 tools/expected-cert.txt 比对。
# 之前踩过「签名不对」：包能装、但和系统安装器签名冲突，更新时才发现。
VERIFY=$("$BT/apksigner.bat" verify -v --print-certs "$OUT" 2>&1 || true)
SIGNER=$(echo "$VERIFY" | grep -m1 "certificate SHA-256 digest" | sed 's/.*digest: *//' | tr -d '\r' | tr 'A-F' 'a-f')
EXPECTED=$(grep -v '^#' tools/expected-cert.txt | tr -d ' \r\n' | tr 'A-F' 'a-f')
# minSdk 34 时 apksigner 只签 v3（v1/v2 是给老系统用的，不开也没问题；
# 之前的 3.7.x 也都是 v3-only，设备上验证过能覆盖安装）
V3=$(echo "$VERIFY" | grep -c "Verified using v3 scheme (APK Signature Scheme v3): true" || true)

if [ -z "$SIGNER" ]; then
  echo "签名自检失败：读不出签名证书指纹" >&2
  echo "$VERIFY" | head -5 >&2
  exit 1
fi
if [ "$SIGNER" != "$EXPECTED" ]; then
  echo "签名不一致！" >&2
  echo "  本次: $SIGNER" >&2
  echo "  期望: $EXPECTED（tools/expected-cert.txt）" >&2
  echo "  设备上的系统安装器是 testkey 签的，签名不一致会导致覆盖安装失败。" >&2
  exit 1
fi
echo "签名: testkey ${SIGNER:0:16}… （与 expected-cert.txt 一致）"
if [ "$V3" -lt 1 ]; then
  echo "签名方案缺失：v3=$V3（minSdk 34 至少要 APK Signature Scheme v3）" >&2
  exit 1
fi
echo "签名方案: v3 ✓"

# 16 KB 页对齐自检：不通过就别交付
if "$BT/zipalign.exe" -c -P 16 -v 4 "$OUT" > /dev/null 2>&1; then
  echo "16 KB alignment: OK"
else
  echo "16 KB alignment: FAILED" >&2
  exit 1
fi

echo "APK: $OUT"
