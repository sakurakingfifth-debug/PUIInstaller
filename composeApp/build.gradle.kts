import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.application")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.animation)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.core:core-splashscreen:1.0.1")
            // 核心破解：Xposed 模块能力（api 只在编译期用，运行时由 LSPosed 注入）
            compileOnly("io.github.libxposed:api:101.0.0")
            implementation("io.github.libxposed:service:101.0.0")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
        }
    }
}

android {
    namespace = "com.panl.installer"
    compileSdk = 37

    defaultConfig {
        // 必须与系统安装器一致：这是替换 /system_ext/priv-app/PackageInstaller 的前提
        applicationId = "com.android.packageinstaller"
        minSdk = 34
        targetSdk = 37
        // 高于系统自带 17.7.4 (17700004)，模块覆盖 / 覆盖安装时才能生效
        // 版本规则：从 3.7.0 起，**每次改动 versionName 只加 patch（3.7.0 → 3.7.1 → …）**；
        // versionCode 每次迭代 **+1000000**（37000001 → 38000001 → …），
        // 且必须 ≥ 系统预装的 17700004，否则 PMS 会拒绝覆盖安装
        // （System app cannot be downgraded to older than its preloaded version）。
        versionCode = 37000001
        versionName = "3.7.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // 构建产物随后用 AOSP testkey 重新签名（见 tools/sign.sh）
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
