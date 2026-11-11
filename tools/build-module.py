#!/usr/bin/env python3
"""把 out/ 里的 APK 打成 Magisk / KernelSU / APatch 模块。

用法：先跑 tools/build.sh 出 APK，再跑
    python3 tools/build-module.py
产物：D:/APK/PUIInstaller-<version>-magisk.zip

模块脚本在 magisk/ 目录（customize.sh / module.prop / uninstall.sh / META-INF），
版本号从 composeApp/build.gradle.kts 读，自动同步进 module.prop。
"""
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "magisk")
OUT_DIR = "D:/APK"


def read_version():
    text = open(os.path.join(ROOT, "composeApp", "build.gradle.kts"), encoding="utf-8").read()
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not name or not code:
        sys.exit("找不到 versionName / versionCode")
    return name.group(1), code.group(1)


def main():
    version, code = read_version()
    apk = os.path.join(ROOT, "out", f"PUIInstaller-{version}-{code}.apk")
    if not os.path.isfile(apk):
        sys.exit(f"没有 APK：{apk}\n先跑 tools/build.sh")
    out = os.path.join(OUT_DIR, f"PUIInstaller-{version}-magisk.zip")
    os.makedirs(OUT_DIR, exist_ok=True)

    # module.prop 的版本跟 APK 对齐
    prop = open(os.path.join(SRC, "module.prop"), encoding="utf-8").read()
    prop = re.sub(r"^version=.*$", f"version={version}", prop, flags=re.M)
    prop = re.sub(r"^versionCode=.*$", f"versionCode={code}", prop, flags=re.M)
    prop = re.sub(r"内嵌 PUI 安装器 [\d.]+", f"内嵌 PUI 安装器 {version}", prop)

    files = []
    for base, _dirs, names in os.walk(SRC):
        for name in names:
            full = os.path.join(base, name)
            files.append(os.path.relpath(full, SRC).replace(os.sep, "/"))
    files.sort(key=lambda p: (p.count("/"), p))

    dirs = sorted({"/".join(f.split("/")[:i]) + "/" for f in files for i in range(1, f.count("/") + 1)})

    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for d in dirs:
            zi = zipfile.ZipInfo(d, date_time=(2026, 1, 1, 0, 0, 0))
            zi.external_attr = (0o40755 << 16) | 0x10
            zi.compress_type = zipfile.ZIP_STORED
            z.writestr(zi, b"")
        for rel in files:
            data = prop.encode("utf-8") if rel == "module.prop" else open(os.path.join(SRC, rel), "rb").read()
            mode = 0o755 if rel.endswith((".sh", "update-binary", "updater-script")) else 0o644
            zi = zipfile.ZipInfo(rel, date_time=(2026, 1, 1, 0, 0, 0))
            zi.external_attr = mode << 16
            zi.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(zi, data)
        zi = zipfile.ZipInfo("files/PackageInstaller.apk", date_time=(2026, 1, 1, 0, 0, 0))
        zi.external_attr = 0o644 << 16
        zi.compress_type = zipfile.ZIP_DEFLATED
        z.writestr(zi, open(apk, "rb").read())

    print(f"module: {out}")
    print(f"  version {version} ({code}), apk {os.path.getsize(apk)} B, zip {os.path.getsize(out)} B")


if __name__ == "__main__":
    main()
