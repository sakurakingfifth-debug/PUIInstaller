packages="com.android.packageinstaller"

# ---- 基础检查 ----
check_support() {
    android_ver=$(getprop ro.build.version.release)
    ui_print "- Android $android_ver"
    case "$android_ver" in
        ''|*[!0-9]*)
            ui_print "- ! Can't read Android version, keep going"
            return 0
            ;;
    esac
    # LineageOS 22.0 = Android 15；APK 自己 minSdk 34，从 15 起都能跑，
    # 所以门槛放到 15 —— LineageOS 23/24（Android 16/17）与 ColorOS 15~17 同样适用。
    [ "$android_ver" -ge 15 ] || abort "- Android $android_ver is not supported (need 15+)"
}

# ---- 找到系统分区里那份安装器 ----
# 有更新时 `pm path` 只给 /data/app/... 那条，所以先按 pm 查、查不到再扫常见目录
resolve_target() {
    sys_apk=""
    for p in $(pm path com.android.packageinstaller 2>/dev/null | sed 's/^package://'); do
        case "$p" in
            /data/*) continue ;;
            *.apk) sys_apk=$p; break ;;
        esac
    done

    if [ -z "$sys_apk" ]; then
        for d in /system_ext/priv-app/PackageInstaller \
                 /system/priv-app/PackageInstaller \
                 /system/system_ext/priv-app/PackageInstaller \
                 /product/priv-app/PackageInstaller \
                 /system/product/priv-app/PackageInstaller \
                 /vendor/priv-app/PackageInstaller \
                 /system_ext/priv-app/OppoPackageInstaller \
                 /system/priv-app/OppoPackageInstaller; do
            [ -d "$d" ] || continue
            f=$(ls "$d"/*.apk 2>/dev/null | head -n 1)
            if [ -n "$f" ]; then
                sys_apk=$f
                break
            fi
        done
    fi

    [ -n "$sys_apk" ] || abort "- Can't locate the system PackageInstaller"

    target_dir=$(dirname "$sys_apk")
    apk_name=$(basename "$sys_apk")
    rel=$(to_rel "$target_dir")
    ui_print "- System installer: $sys_apk"
    ui_print "- Module target: /$rel/$apk_name"
}

# 绝对路径 → 模块内路径（统一用 system/ 前缀，/system 本身除外）
to_rel() {
    case "$1" in
        /system/*) echo "${1#/}" ;;
        *)         echo "system$1" ;;
    esac
}

# ColorOS 上同一个包名可能有两份：/system_ext/priv-app/PackageInstaller 和
# /system_ext/priv-app/OppoPackageInstaller（Oppo 自己那份 17.0.x）。PMS 扫描时
# 目录名靠前的先进，非目标那份会把我们的顶掉（3.7.41 实测踩过）。
# 把非目标那一份整个目录盖成空目录 + REPLACE，确保只剩我们这一份。
# 注意：目录"看起来是空的"也可能是上一个版本模块盖出来的，同样要盖，
# 否则重启后它会带着系统原本的 APK 回来（踩过一次）。
hide_duplicates() {
    for base in /system_ext/priv-app /system/priv-app /product/priv-app \
                /system/product/priv-app /vendor/priv-app; do
        for name in PackageInstaller OppoPackageInstaller; do
            d="$base/$name"
            [ "$d" = "$target_dir" ] && continue
            [ -d "$d" ] || continue
            hrel=$(to_rel "$d")
            mkdir -p "$MODPATH/$hrel" || continue
            REPLACE="$REPLACE /$hrel"
            ui_print "- Hiding duplicate installer: $d"
        done
    done
}

# /data/app 的更新会盖住模块里的系统 APK，必须清掉。
# ColorOS 上 `pm uninstall-system-updates` 对默认安装器一律失败（受保护），所以直接删目录。
uninstall_updates() {
    ui_print "- Removing installer updates"
    for pkg in $packages; do
        pm uninstall-system-updates "$pkg" >/dev/null 2>&1
        for d in /data/app/*/"$pkg"-* /data/app/"$pkg"-*; do
            [ -d "$d" ] || continue
            rm -rf "$d" && ui_print "- Removed update: $d"
        done
    done
}

install_package() {
    ui_print "- Placing $apk_name"
    mkdir -p "$MODPATH/$rel" || abort "- Can't create $MODPATH/$rel"
    cp -f "$MODPATH/files/PackageInstaller.apk" "$MODPATH/$rel/$apk_name" || abort "- Copy failed"
    chmod 755 "$MODPATH/$rel" 2>/dev/null
    # 这里**绝对不能**用 REPLACE / .replace：
    # KernelSU 的 metamodule（mountify）会把 REPLACE 目录做成 overlay opaque 目录，
    # 而它的 mark_replace() 里有一句 `chmod 644 $1` —— 目录没有 x 位，
    # 除 root 外谁都进不去（system_server 读 APK 直接 EACCES）：
    #   PMS: Failed to collect certificates … using APK Signature Scheme v3
    #   PMS: There must be exactly one installer; found []
    #   → system_server 自杀 → 开机循环（3.7.43 + mountify 实测踩过）
    # 我们只是换同名 APK，文件级覆盖就够了，整目录替换纯属多余。
}

# 从应用里 su 调 apd / ksud / magisk 刷模块时，解出来的文件会继承调用方的 SELinux
# 类别（u:object_r:system_file:s0:c129,c257,c512,c768）。overlay 之后所有普通 app
# 进程 search 被拒，APK 变成「打不开」，安装器点开就闪退。
# 这里统一归位成不带类别的 system_file，不管用哪种方式刷入都是干净的。
fix_labels() {
    [ -d "$MODPATH/system" ] || return 0
    if command -v chcon >/dev/null 2>&1; then
        if chcon -R u:object_r:system_file:s0 "$MODPATH/system" 2>/dev/null; then
            ui_print "- SELinux labels fixed"
        else
            ui_print "- ! Relabel failed, run chcon manually if it won't boot"
        fi
    else
        ui_print "- ! chcon not found, skip relabel"
    fi
}

clean_files() {
    rm -rf "$MODPATH/addon" "$MODPATH/files" 2>/dev/null
    rm -f "$MODPATH/install.sh" 2>/dev/null
    # 换掉系统 APK 后资源缓存和 package_cache 必须清，否则系统还在用旧资源
    rm -rf /data/resource-cache/* /data/system/package_cache/* 2>/dev/null
}


# ---- 挂载失败兜底 ----
# 某些 root 实现（现场：SukiSU LKM + ColorOS 17）**不会**把模块里的 system_ext
# 挂到 /system_ext，system_server 读到的还是厂商原 APK（PMS 仍报 17.0.1）。
# 更麻烦的是 KernelSU 系默认对普通应用进程 kernel_umount，就算挂上了也会被摘掉，
# 应用进程再打开系统路径读到厂商 APK → ClassNotFoundException → 闪退。
#
# 两段兜底，都只在「挂载确实没生效」时动作，挂载正常的机器上完全是空转：
#   1. post-fs-data.sh：把模块 APK 文件级 bind mount 到系统路径，让 PMS 先认出来；
#   2. service.sh 里追加：系统路径仍是厂商 APK 时，用同一份 APK 补一个 /data/app 更新，
#      这样应用进程从 /data/app 启动，不受 kernel_umount 影响。
write_fallbacks() {
    [ -n "$sys_apk" ] && [ -n "$rel" ] && [ -n "$apk_name" ] || return 0

    cat > "$MODPATH/post-fs-data.sh" <<PFS
#!/system/bin/sh
# 自动生成：模块里的 system_ext 没被挂上时，用 bind mount 兜底
MODDIR=\${0%/*}
TARGET="$sys_apk"
# 模块里这份 APK 可能被不同 root 实现挪到 system/ 下或顶层 system_ext/，
# 按名字找最稳（目录很小，find 开销可忽略）
SRC=\$(find "\$MODDIR" -type f -name "$apk_name" 2>/dev/null | head -n 1)
[ -f "\$SRC" ] || exit 0
[ -f "\$TARGET" ] || exit 0
a=\$(stat -c %s "\$SRC" 2>/dev/null)
b=\$(stat -c %s "\$TARGET" 2>/dev/null)
# 大小一致说明挂载已经生效（就是模块里的文件），不用动
[ -n "\$a" ] && [ "\$a" = "\$b" ] && exit 0
mount --bind "\$SRC" "\$TARGET" 2>/dev/null || true
PFS
    chmod 755 "$MODPATH/post-fs-data.sh"

    cat >> "$MODPATH/service.sh" <<SVC

# ---- 自动补装 /data/app 更新（SukiSU 等会对普通应用 kernel_umount 的场景）----
(
    i=0
    while [ "\$i" -lt 60 ]; do
        pm path android >/dev/null 2>&1 && break
        sleep 2
        i=\$((i + 1))
    done
    MODDIR=\$(cd "\$(dirname "\$0")" 2>/dev/null && pwd)
    APK=\$(find "\$MODDIR" -type f -name "$apk_name" 2>/dev/null | head -n 1)
    [ -f "\$APK" ] || exit 0
    # 已经有 /data/app 更新就不用管
    case "\$(pm path com.android.packageinstaller 2>/dev/null)" in
        *"/data/app/"*) exit 0 ;;
    esac
    SYS="\$(pm path com.android.packageinstaller 2>/dev/null | sed 's/^package://' | head -n 1)"
    [ -n "\$SYS" ] && [ -f "\$SYS" ] || exit 0
    a=\$(stat -c %s "\$APK" 2>/dev/null)
    b=\$(stat -c %s "\$SYS" 2>/dev/null)
    # 系统路径已经是模块里的 APK（挂载成功）→ 不用补装
    [ -n "\$a" ] && [ "\$a" = "\$b" ] && exit 0
    pm install -r -d "\$APK" >/dev/null 2>&1
) &
SVC
    ui_print "- Fallback scripts written (bind mount + auto update)"
}

run_install() {
    command -v mods_center >/dev/null 2>&1 && mods_center
    check_support
    uninstall_updates
    resolve_target
    install_package
    hide_duplicates
    fix_labels
    write_fallbacks
    clean_files
    ver=$(grep '^version=' "$MODPATH/module.prop" | cut -d= -f2)
    ui_print "- PUI Installer $ver installed, reboot to take effect"
}

run_install
