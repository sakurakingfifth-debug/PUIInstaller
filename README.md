# PUI Installer

我懒，所以README.md是大肥鱼写的。

用 **PUI 设计系统**重写的系统安装器，替换 ColorOS 17 / Android 17 上的
`com.android.packageinstaller`（AOSP PackageInstaller 17.7.4）。

界面全部是 Compose + PUI 组件（灰底白卡、非凡洋红、20dp 圆角、悬浮胶囊导航栏），
安装能力走 `PackageInstaller` 会话，系统安装器身份带来静默安装 / 静默卸载。

```
PUIInstaller/
├── composeApp/src/commonMain/kotlin/com/panl/pui/    ← PUI 库（从 PUIDemo 1.0.92 原样搬入）
├── composeApp/src/androidMain/
│   ├── kotlin/com/panl/installer/
│   │   ├── core/          APK 解析、签名识别、安装引擎、设置、通知
│   │   └── ui/            首页 / 安装页 / 卸载页 / 设置 / 签名检测 / 应用商店能力
│   └── res/               自适应图标（含 -night 深色渐变底）、主题
└── tools/build.sh         构建 → zipalign → AOSP testkey 签名
```

---

## 一、为什么包名是 `com.android.packageinstaller`

设备上的系统安装器位于 `/system_ext/priv-app/PackageInstaller/`，签名是 **AOSP testkey**
（`CN=Android, O=Android`，SHA-256 `a40da80a…`）。这份 APK 用同一把公开测试密钥签名，
因此可以：

- 以「系统应用更新」的身份覆盖安装（`UPDATED_SYSTEM_APP`），保留 `PRIVILEGED` 标志；
- 继续持有 `INSTALL_PACKAGES` / `DELETE_PACKAGES`，实现**静默安装、静默卸载**；
- 通过 KernelSU 模块直接替换 `/system_ext/priv-app/PackageInstaller/PackageInstaller.apk`。

> 换 ROM / 换签名后需要重新确认：`apksigner verify --print-certs` 对比系统 APK 的证书。
> 签名不一致时只能用模块覆盖（且要清掉旧包数据），不能 `adb install -r`。

---

## 二、五个需求怎么落地的

### 1. 桌面图标 + 功能面板

- 自适应图标：`res/mipmap-anydpi-v26/ic_launcher.xml`
  - 图形：官方 `Directbox_Receive.svg` 原样搬入（24×24 视图，group 放大 3.2 倍居中），
    四个 path 全部改成白色；托盘与槽口保留原图 40% 的透明度，叠出来仍有层次
  - 亮色底：洋红斜向渐变（`drawable/ic_launcher_background.xml`）
  - **暗色底：深色渐变 + 左上柔光**（`drawable-night/ic_launcher_background.xml`），
    前景同样是白色，深底上对比度反而更强，因此不再单独做夜间前景
  - 单色层：Android 13+ 主题图标（只保留箭头 + 盒身剪影）
- 首页（`ui/main/HomeScreen.kt`）：Hero 状态卡 + 两个快捷入口 + 功能清单。
  新增功能只要往 `features` 列表里加一条，样式与点击行为统一。

### 2. 版本号对比（当前 → 更新后）

`ui/components/Widgets.kt` 的 `VersionCompareCard`：

```
当前 1.0.91 (92)   →   新版本 1.0.92 (93)
从 1.0.91 更新到 1.0.92，版本号 92 → 93
```

- 数据来自 `ApkInspector`：`getPackageArchiveInfo` 读 APK 的 versionName/versionCode，
  再与 `getPackageInfo` 的已安装版本比对，得到 `NEW / UPDATE / DOWNGRADE / REINSTALL` 四种关系；
- 未安装时左侧显示「未安装」，降级时整卡转错误色并提示去设置里开「允许降级安装」；
- 已安装应用列表（应用页）也逐行显示版本号。

### 3. 安装后删除安装包

- 设置 → 安装 → **安装后删除安装包**（另有「删除前确认」二级开关）；
- 安装成功后：先删暂存副本，再按开关删源文件；源是 `content://` 时依次尝试
  `ContentResolver.delete` → `DocumentsContract.deleteDocument` → 真实路径删除；
- 结果页会明确显示「已删除安装包」或「安装包未能删除（没有写入权限）」，
  设置关掉时结果页也提供手动「删除安装包」按钮；
- 已声明 `MANAGE_EXTERNAL_STORAGE`，设置页有「文件访问权限」入口，
  用于处理 `file://` 形式的安装包（公共目录直读直删）。

### 4. AOSP 签名检测 + `SHOW_APP_INFO`

**签名检测**（`core/ApkSignature.kt`）：

- 读取 APK 的 v2/v3 签名块，解析 X.509 证书，输出主题、颁发者、SHA-256/SHA-1、算法、有效期；
- 内置 AOSP 四把公开密钥的证书指纹，命中即标记：

  | 密钥 | SHA-256 |
  |---|---|
  | testkey | `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc` |
  | platform | `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8` |
  | shared | `28bbfe4a7b97e74681dc55c2fbb6ccb8d6c74963733f6a46ae74d8c3a6e879fd` |
  | media | `465983f7791f2abeb43ea2cbdc7f21a8260b72bc08a55c839fc1a43bc741a81e` |

- 另外识别 Android 调试密钥（`CN=Android Debug`）；
- 安装页给出红色警告卡，独立的「签名检测」页列出全部签名者详情。

**`SHOW_APP_INFO`**（`ui/UninstallActivity.kt`）：

```xml
<intent-filter>
    <action android:name="android.intent.action.SHOW_APP_INFO" />
    <data android:scheme="package" />
</intent-filter>
```

LibChecker 里点应用图标 → 用本安装器打开 → 直接进「应用信息 + 卸载」页。
同一页面还接 `ACTION_DELETE` / `ACTION_UNINSTALL_PACKAGE` / `APPLICATION_DETAILS_SETTINGS`。

### 5. 注册应用商店（Registered App Store）

参考 <https://developer.android.com/registered-app-stores/overview>，
代码侧要求都落在清单与实现里：

- 声明 `android.permission.INSTALL_PACKAGES_AS_REGISTERED_APP_STORE`
  与 `android.permission.REQUEST_INSTALL_PACKAGES`；
- 处理安装 / 卸载 / 应用信息三类必需 Intent；
- 安装会话设置 `SessionParams.USER_ACTION_NOT_REQUIRED`（Android 16+ 的一键安装语义）；
- 安装页展示名称、开发者（签名者）、版本、大小、权限 —— RAS 对「清晰的应用信息」的要求；
- 「应用商店能力」页（`ui/AppStoreActivity.kt`）做 9 项自检 + 注册流程说明。

> 注册本身是 Google 的运营计划（企业资质、年费、MASA 认证、目录上报），代码无法替代；
> 页面只做代码侧自检与流程说明。

---

### 6. 外观：模糊与毛玻璃实时可调

设置 → 外观 / 导航栏，对应 PUI 令牌页那组控件（全部实时生效、落盘保存）：

| 控件 | 范围 | 作用 |
|---|---|---|
| 顶部模糊与毛玻璃 | 开关 | 整屏是否挂 AGSL 逐像素采样；关掉最省 GPU |
| 顶部模糊深度 | 2–100 px | 顶部渐进模糊的最强半径 |
| 卡片展开模糊 | 0–120 px | 对话框（PUI 卡片）弹出时，背后页面的模糊半径 |
| 导航栏 · 模糊半径 | 2–64 px | 悬浮胶囊的毛玻璃半径 |
| 导航栏 · 背景不透明度 | 0–100 % | 玻璃底色 alpha，0 为全透 |

实现上沿用 PUI 的两条路径：顶部 + 胶囊在**同一个 AGSL** 里算（`puiProgressiveTopBlur`），
对话框背后的模糊走 `puiRealtimeBlur`（RenderEffect，进度 0→1 动画）。

---

### 7. 浮窗安装界面（背景模糊）

设置 → 安装界面：

| 项 | 选项 | 说明 |
|---|---|---|
| 样式 | 浮窗 / 全屏 | 浮窗是底部弹出的卡片，身后界面保留并做背景模糊；全屏是整页样式 |
| 浮窗位置 | 居中 / 底部 | 贴底时只圆上面两角，居中时四角全圆、四周留 14dp |

实现要点（踩过的坑）：

- **窗口半透明必须在清单里定死**。`ActivityManager` 在启动时就根据清单主题判断任务是否
  `translucent`，代码里 `setTheme()` 已经来不及 —— 那样会出现「背景全黑、后面的应用被隐藏」。
  所以 `InstallActivity` 清单主题挂 `Theme.PuiInstaller.Sheet`，全屏模式在 `onCreate` 里
  再 `setTheme(Theme.PuiInstaller)` 换回不透明。
- **模糊是系统做的**：`FLAG_BLUR_BEHIND` + `LayoutParams.setBlurBehindRadius(26dp)`，
  依赖 `ro.surface_flinger.supports_background_blur=1`。系统不支持时只剩一层 18% 黑遮罩。
- 浮窗与全屏共用同一个 `InstallController` 状态机，只有容器与信息密度不同；
  浮窗里签名只保留「AOSP 公开密钥」风险提示，完整证书信息在全屏版。
- 浮窗头部的「⋯」可以直接切换样式与位置；改样式会 `recreate()`（窗口属性不能热改）。

---

## 版本号规则

对外 `versionName` 从 **2.0.0** 起迭代：`2.0.1` / `2.1.0` …

`versionCode` 不能跟着从 20000 起：这个包在系统镜像里有预装版本（模块里的 17700004），
PMS 明确拒绝把系统应用降到预装版本以下（`System app cannot be downgraded to older than
its preloaded version`）。所以保留 17700000 前缀：

```
versionCode = 17700000 + major*100 + minor*10 + patch
2.0.0 → 17700200
2.0.1 → 17700201
2.1.0 → 17700210
```

---

### 8. 实时活动（Live Updates）与 ColorOS 流体云

安装过程中会发一条**进度型实时活动**，被系统提升后在通知栏顶部常驻、并在状态栏显示胶囊。

| 项 | 做法 |
|---|---|
| 权限 | 清单声明 `POST_PROMOTED_NOTIFICATIONS`（普通权限，装包即授予） |
| 样式 | `Notification.ProgressStyle` + 两段进度（复制 30 / 安装 70），无进度时 `setProgressIndeterminate(true)` |
| 提升 | 请求 `requestPromotedOngoing`（见下方注意），并满足 ongoing / 有 contentTitle / 无自定义 RemoteViews / 渠道非 MIN |
| 胶囊文字 | `setShortCriticalText`：有进度给 `86%`，不确定时给「安装中」 |
| 渠道 | 专用 `install_live`（IMPORTANCE_DEFAULT）；结果通知仍走 `install_status`（LOW，发完即走） |
| 开关 | 设置 → 通知 → 安装进度实时活动 |

实测（ColorOS 17 / Android 17）：

```
Notification(channel=install_live ... flags=ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING)
```

`PROMOTED_ONGOING` 出现即代表系统已把它当实时活动处理。

**注意：** `Notification.Builder#setRequestPromotedOngoing()` 到 API 37 才进公开 SDK，
本项目 compileSdk 是 36，所以直接写 extra：

```kotlin
builder.addExtras(Bundle().apply {
    putBoolean("android.requestPromotedOngoing", true)   // = EXTRA_REQUEST_PROMOTED_ONGOING
})
```

**关于流体云**：ColorOS 16 起，流体云**直接消费 Android 16 的 Live Updates**，
不需要接 OPPO 私有 SDK —— 按上面这套标准实现即可上云。
OPPO 开放平台那份文档（id=12658）是 JS 渲染的，抓不到正文；如果个别机型上云不显示，
大概率是 OPPO 侧对应用做了白名单或额外条件，需要在开放平台申请，代码侧无需改动。

---

## 三、构建与签名

```bash
# 需要 JDK 17+；SDK 路径写在 local.properties
./tools/build.sh
# 产物：out/PUIInstaller-<versionName>-<versionCode>.apk
```

脚本做三件事：`assembleRelease` → `zipalign -p 4` → `apksigner` 用
`keys/testkey.pk8` + `keys/testkey.x509.pem` 签名（v1+v2+v3）。

密钥来自 AOSP `build/target/product/security/`，是公开的；不要用它签任何要分发的应用。

---

## 四、部署到设备

**方式 A：覆盖安装（当前使用）**

```bash
adb install -r out/PUIInstaller-2.0.0-17700200.apk
```

系统会把它当作系统安装器的更新，保留 `PRIVILEGED` 与安装权限。

**方式 B：KernelSU 模块**

把 APK 放到模块的 `system/system_ext/priv-app/PackageInstaller/PackageInstaller.apk`，
重启后由 magic mount 覆盖系统分区里的原 APK。模块目录：

```
/data/adb/modules/PUIAOSPPackageInstall/
└── system/system_ext/priv-app/PackageInstaller/PackageInstaller.apk
```

---

## 五、已知边界

- **Intent filter 必须带 mimeType**：`ACTION_INSTALL_PACKAGE` 的 intent 如果带了
  `type=application/vnd.android.package-archive`，清单里只写 `<data android:scheme="content"/>`
  是**匹配不上**的（酷安就是这种调用，会抛 `ActivityNotFoundException`，表现是「点安装没反应」）。
  清单里因此同时保留了「scheme + mimeType」和「仅 scheme」两组 filter。
- **应用归档（unarchive）**：AOSP 的 `UNARCHIVE_DIALOG` 由原 PackageInstaller 处理，
  本实现暂未接管；已归档应用恢复时可能提示找不到处理程序。
- **降级安装**：`SessionParams.setRequestDowngrade` 是 `@SystemApi`，公开 SDK 里没有，
  代码用反射调用；被隐藏 API 策略拦下时系统会返回 `INSTALL_FAILED_VERSION_DOWNGRADE`。
- **RAS 一键安装**：`USER_ACTION_NOT_REQUIRED` 只有在商店通过 Google 审核后才生效，
  普通签名下系统会忽略该标志，仍走正常安装流程。
- **静默能力依赖系统身份**：以普通应用安装时没有 `INSTALL_PACKAGES`，
  会话提交后系统会弹确认（本应用会把系统的确认 Intent 原样转交）。

---

## 六、代码地图

| 关注点 | 文件 |
|---|---|
| APK 解析（label/icon/版本/权限/签名） | `core/ApkInspector.kt` |
| 签名与 AOSP 密钥识别 | `core/ApkSignature.kt` |
| 安装 / 卸载会话 | `core/InstallEngine.kt` |
| 安装包清理 | `core/InstallEngine.kt` 的 `SourceCleaner` |
| 结果广播与通知 | `core/InstallResultReceiver.kt`、`core/UninstallResultReceiver.kt`、`core/Notifications.kt` |
| 安装页状态机 | `ui/install/InstallController.kt` |
| 安装页界面 | `ui/install/InstallScreen.kt` |
| 卸载 / 应用信息 | `ui/uninstall/UninstallScreen.kt` |
| 首页 / 应用 / 设置 | `ui/main/*.kt` |
| 签名检测页 | `ui/SignatureActivity.kt` |
| 应用商店能力页 | `ui/AppStoreActivity.kt` |
| 通用卡片与图标 | `ui/components/Widgets.kt` |
