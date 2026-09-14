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
        google()
        mavenCentral()
        // sherpa-onnx (Whisper + Piper) يُنشر عبر JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ArabicDub"
include(":app")
