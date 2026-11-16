#!/system/bin/sh
# 模块卡片上的「执行」按钮：直接打开安装器本体。
#
# KernelSU / APatch 里的模块卡会调这个脚本（Magisk 也有 action 按钮，但要在管理器里手动点）。
# 安装器被禁用或没装时给出提示，而不是静默失败。
MODDIR=${0%/*}
PKG=com.android.packageinstaller
ACT=$PKG/com.panl.installer.ui.MainActivity

if ! pm path "$PKG" >/dev/null 2>&1; then
  echo "安装器本体没有安装，请先安装 PUIInstaller APK"
  exit 1
fi

# 优先走主 Activity；个别 ROM 上被停用时退回桌面别名
if ! am start -n "$ACT" >/dev/null 2>&1; then
  am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    -n "$PKG/com.panl.installer.ui.LauncherAlias" >/dev/null 2>&1
fi

# 用软重启前的等待里把模块信息也报一下，方便排查
grep '^version=' "$MODDIR/module.prop" 2>/dev/null && echo "已打开安装器"
exit 0
