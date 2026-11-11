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
    [ "$android_ver" -ge 16 ] || abort "- Android $android_ver is not supported (need 16+)"
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

run_install() {
    command -v mods_center >/dev/null 2>&1 && mods_center
    check_support
    uninstall_updates
    resolve_target
    install_package
    hide_duplicates
    fix_labels
    clean_files
    ver=$(grep '^version=' "$MODPATH/module.prop" | cut -d= -f2)
    ui_print "- PUI Installer $ver installed, reboot to take effect"
}

run_install
