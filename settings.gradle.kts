pluginManagement {
    repositories {
        // 本机到 Maven Central / Google 的直连不可达（实测 curl 返回 000），阿里云镜像是
        // 唯一可达的 Maven 源。镜像放最前，命中后不再回源，避免每次构建都等超时。
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        // F3-03: Tesseract4Android is only distributed via JitPack (docs/decisions/2026-10-13-f3-03-ocr-provider-licenses.md §5 T3)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "EnglishLearning"
include(":app")
