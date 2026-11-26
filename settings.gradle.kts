pluginManagement {
    repositories {
        // 国内网络直连 dl.google.com 常超时，插件仓库先走阿里云镜像
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 金标联盟「一镜到底」SharedViewKit：只有 gsai.sdk 这一组走它（内测仓库还是 HTTP）
        maven {
            url = uri("http://nexus.itgsa.com:5566/repository/sharedViewKit/")
            isAllowInsecureProtocol = true
            content { includeGroup("gsai.sdk") }
        }
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "PUIInstaller"
include(":composeApp")
