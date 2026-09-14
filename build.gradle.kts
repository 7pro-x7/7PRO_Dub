// Plugins على مستوى المشروع
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // مكوّل Compose (نفس إصدار Kotlin)
    id("org.jetbrains.kotlin.plugin.compose") version "1.9.24" apply false
}
