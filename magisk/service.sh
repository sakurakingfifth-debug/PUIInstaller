#!/system/bin/sh
# 开机自愈：把安装器入口组件从「被禁用」状态恢复回来。
#
# 现场（用户 bugreport）：选择器能列出「安装器」，点下去却是
#   Unable to find explicit activity class {com.android.packageinstaller/com.panl.installer.ui.InstallActivity}
# 原因是某些「接管安装 / 静默安装」的工具（爱玩机工具箱之类）或 LSPosed 模块把这个组件禁用了。
# 禁用状态是按组件名记在 user state 里的，卸载重装安装器、重刷模块都不会清掉，只能显式 enable。
#
# 放后台、等 framework 起来再动手，避免拉慢开机。
(
    i=0
    while [ "$i" -lt 60 ]; do
        if pm path android >/dev/null 2>&1; then
            break
        fi
        sleep 2
        i=$((i + 1))
    done
    pm enable --user 0 com.android.packageinstaller/com.panl.installer.ui.InstallActivity >/dev/null 2>&1
    pm enable --user 0 com.android.packageinstaller/com.panl.installer.ui.UninstallActivity >/dev/null 2>&1
) &
