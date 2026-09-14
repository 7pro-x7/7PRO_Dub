import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// يوفّرها codemagic.yaml (خطوة "إعداد ملف التوقيع") في نسخة الإصدار الموقّعة.
// إن لم يوجد الملف (بناء تجريبي محلي) يبقى البناء كما كان بدون توقيع.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.arena.arabicdub"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.arena.arabicdub"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // ---------- واجهة Compose / Material 3 ----------
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ---------- مشغل الوسائط (Media3 / ExoPlayer) ----------
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // ---------- المحرك مفتوح المصدر: sherpa-onnx ----------
    // Whisper (تفهم كلام + ترجمة) و Piper (نطق عربي) على الجهاز
    // ملاحظة: JitPack ينشر هذا المستودع متعدد الوحدات، ويجرّ أحيانًا وحدة
    // sherpa-onnx-jvm الخاصة بجافا العادية بجانب وحدة أندرويد (AAR) —
    // وكلتاهما تحتويان نفس الأصناف المُصرَّفة، ما يسبب خطأ "Duplicate class".
    // نستثني الوحدة غير المطلوبة على أندرويد.
    implementation("com.github.k2-fsa:sherpa-onnx:1.13.8") {
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
    }

    // ---------- FFmpeg: استخراج/دمج الصوت والفيديو ----------
    // ملاحظة: مكتبة com.arthenica:ffmpeg-kit-full الأصلية تم التوقف عنها وحذفها
    // من Maven Central بتاريخ 1 أبريل 2025. تم استبدالها بالنسخة المُستمرة صيانتها
    // (dev.ffmpegkit-maintained) وهي بديل مباشر بنفس الـ API (com.arthenica.ffmpegkit)
    // وتحتوي على x264 (GPL) — مناسب للمشروع مفتوح المصدر.
    // إن أردت بناء غير GPL استبدله بـ ffmpeg-kit-min (بدون حرق ترجمة).
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:6.0.3")

    // ---------- تنزيل النماذج وتحميل روابط الفيديو ----------
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // فك الضغط tar.bz2 (صوت Piper) بلغة جافا خالصة
    implementation("org.apache.commons:commons-compress:1.26.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
