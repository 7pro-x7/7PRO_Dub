// Plugins على مستوى المشروع
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // مكوّل Compose (نفس إصدار Kotlin) - هذا البلوجن متوفر فقط بدءًا من Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
