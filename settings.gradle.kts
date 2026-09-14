pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // مستودع محلي لأرتيفاكثين مثبتين (sherpa-onnx + ffmpeg-kit)
        // — يُنشّئ سكربت الجذر (build.gradle.kts) محتواه مع التحقق من البصمات
        maven { url = uri("${rootDir}/local-maven") }
        google()
        mavenCentral()
    }
}

rootProject.name = "ArabicDub"
include(":app")
