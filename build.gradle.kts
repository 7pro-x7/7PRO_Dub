import java.io.File
import java.net.URL
import java.security.MessageDigest

// Plugins على مستوى المشروع
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // مكوّل Compose (نفس إصدار Kotlin)
    id("org.jetbrains.kotlin.plugin.compose") version "1.9.24" apply false
}

/*
 * ============================================================================
 *  تثبيت أرتيفاكثين أصليين ضخمين عبر مستودع Maven محلي (local-maven)
 * ============================================================================
 * لماذا؟
 *  - ffmpeg-kit توقف عن الصيانة (يوليو 2026) وزالت أرتيفاكثه من Maven Central
 *    الرئيسي — تبقى متاحة في مرايا موثوقة مثبتة أدناه (مع تحقق SHA-256).
 *  - AAR sherpa-onnx ينشر في GitHub Releases مباشرة (مصدر مستقر).
 *
 * ماذا يفعل هذا الكود؟
 *  1. عند أول بناء فقط: ينزّل كل AAR إلى local-maven/ (تجاهل git)
 *  2. يتحقق من البصمة SHA-256 — أي تغيير في الملف = فشل صريح لا بناء معطوب.
 *  3. يكتب ملف POM بسيط (بشمول تبعات الأرتيفاك) حتى يعمل التجميع كالمعتاد.
 *  4. في البناءات التالية: الملف موجود وبصمته صحيحة → لا شيء يتم (ثانية واحدة).
 *
 * الإعدادات (روابط + بصمات) محددة يدويًا وتعمل حاليًا — إن تعطلت روابط،
 * أضف رابطًا بديلًا في أعلى القائمة قبل القديم.
 */

data class PinnedAar(
    val group: String,
    val artifact: String,
    val version: String,
    val urls: List<String>,
    val sha256: String,
    val pomDependencies: List<Triple<String, String, String>> = emptyList(),
)

fun sha256OfFile(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 20)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun ensurePinnedAar(spec: PinnedAar) {
    val dir = File(rootDir, "local-maven/${spec.group.replace('.', '/')}/${spec.artifact}/${spec.version}")
    val aar = File(dir, "${spec.artifact}-${spec.version}.aar")

    if (!(aar.exists() && sha256OfFile(aar) == spec.sha256)) {
        dir.mkdirs()
        val tmp = File(dir, "${spec.artifact}-${spec.version}.aar.part")
        var lastError: Exception? = null
        for (url in spec.urls) {
            try {
                URL(url).openStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
                }
                if (sha256OfFile(tmp) != spec.sha256) {
                    tmp.delete()
                    throw java.io.IOException("فشل التحقق SHA-256 من $url (تغيّر الملف في المصدر؟)")
                }
                if (!tmp.renameTo(aar)) {
                    tmp.copyTo(aar, overwrite = true)
                    tmp.delete()
                }
                lastError = null
                break
            } catch (e: Exception) {
                tmp.delete()
                lastError = e
            }
        }
        if (lastError != null) {
            throw GradleException(
                "تعذر جلب ${spec.group}:${spec.artifact}:${spec.version}. " +
                    "جُرّبت الروابط: ${spec.urls} — تحقق من اتصال الإنترنت وأعد المحاولة.",
                lastError,
            )
        }
    }

    val pom = File(dir, "${spec.artifact}-${spec.version}.pom")
    if (!pom.exists()) {
        val deps = spec.pomDependencies.joinToString("\n") { (g, a, v) ->
            """    <dependency>
      <groupId>$g</groupId>
      <artifactId>$a</artifactId>
      <version>$v</version>
    </dependency>"""
        }
        pom.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>${spec.group}</groupId>
    <artifactId>${spec.artifact}</artifactId>
    <version>${spec.version}</version>
    <packaging>aar</packaging>
    <dependencies>
$deps
    </dependencies>
</project>
""",
        )
    }
}

// sherpa-onnx 1.13.8 (Whisper + Piper) — GitHub Releases
ensurePinnedAar(
    PinnedAar(
        group = "com.github.k2-fsa",
        artifact = "sherpa-onnx",
        version = "1.13.8",
        urls = listOf(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar",
        ),
        sha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96",
    ),
)

// ffmpeg-kit-full 6.0.LTS (FFmpeg 6.0 + x264 + libass) — مرايا Maven Central
ensurePinnedAar(
    PinnedAar(
        group = "com.arthenica",
        artifact = "ffmpeg-kit-full",
        version = "6.0.LTS",
        urls = listOf(
            "https://maven.aliyun.com/repository/central/com/arthenica/ffmpeg-kit-full/6.0.LTS/ffmpeg-kit-full-6.0.LTS.aar",
            "https://repo.huaweicloud.com/repository/maven/com/arthenica/ffmpeg-kit-full/6.0.LTS/ffmpeg-kit-full-6.0.LTS.aar",
            "https://repo1.maven.org/maven2/com/arthenica/ffmpeg-kit-full/6.0.LTS/ffmpeg-kit-full-6.0.LTS.aar",
        ),
        sha256 = "e77eb8d02b67e225759b27e7288f84a8d2a4392abd2418f282c2936a990df4c3",
        pomDependencies = listOf(
            Triple("com.arthenica", "smart-exception-java", "0.2.1"),
        ),
    ),
)
