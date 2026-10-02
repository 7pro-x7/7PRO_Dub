import java.util.Properties
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
// Imported explicitly (not referenced as java.io.File / java.net.URI / java.security.MessageDigest
// inline below): the android/java plugins add a `java` extension accessor to this script, which
// shadows the `java` package at expression position and breaks those qualified references.
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** Reads the project .env so Supabase credentials are compiled in without hardcoding them in source. */
fun envValue(key: String, fallback: String = ""): String {
    val candidates = listOf(rootProject.file(".env"), rootProject.file("../.env"))
    for (file in candidates) {
        if (!file.exists()) continue
        val props = Properties()
        file.inputStream().use { props.load(it) }
        val value = props.getProperty(key)
        if (!value.isNullOrBlank()) return value.trim().replace("\"", "").replace("'", "")
    }
    return System.getenv(key) ?: fallback
}

/**
 * Release signing material, read from `keystore.properties` (local) or the environment (CI).
 * Nothing is ever committed: when no keystore is supplied the build falls back to debug signing,
 * so day-to-day builds keep working untouched.
 */
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propertyKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile: String? = signingValue("storeFile", "ANDROID_KEYSTORE_PATH")
val hasReleaseKeystore: Boolean = releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

// Known-good set as of the jitsiMeetSdk 9.2.2 -> 11.6.3 upgrade. This is the floor the dynamic
// discovery in computeJitsiOverrideLibraries (see below, used from the androidComponents block
// after the android {} block) is unioned with, and the value used verbatim if that discovery
// can't run at all. Declared here, before android {}, because defaultConfig below reads it
// immediately — a top-level val referenced before its own initializer has run (i.e. declared
// later in the file) is simply null at that point, not a forward reference.
val jitsiOverrideLibrariesFallback = listOf(
    "org.jitsi.meet.sdk", "com.swmansion.rnscreens", "com.swmansion.gesturehandler",
    "com.swmansion.reanimated", "com.swmansion.worklets", "com.th3rdwave.safeareacontext",
    "com.reactnativecommunity.asyncstorage", "com.reactnativecommunity.netinfo",
    "com.reactnativecommunity.webview", "com.reactnativecommunity.clipboard",
    "com.reactnativecommunity.slider", "com.oney.WebRTCModule", "com.horcrux.svg",
    "com.corbt.keepawake", "com.rnimmersive", "com.zmxv.RNSound", "com.calendarevents",
    "com.learnium.RNDeviceInfo", "com.ocetnik.timer", "com.rnfs", "com.BV.LinearGradient",
    "org.wonday.orientation", "org.devio.rn.splashscreen", "com.dylanvann.fastimage",
    "io.invertase.googlesignin", "com.giphyreactnativesdk", "com.brentvatne.react",
    "com.oblador.vectoricons"
)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// ---------------------------------------------------------------------------------------------
// AI Tutor: fetches the open-source U-2-Net-p background-removal model (MIT license, ~4.7 MB)
// into assets at build time, straight from its official release, so nobody has to download or
// commit a binary by hand. Runs once — cached after the first build — and is verified by MD5
// so a corrupted or tampered download can never ship.
// ---------------------------------------------------------------------------------------------
val u2netpDir = layout.projectDirectory.dir("src/main/assets/character_ai")
val u2netpFile = u2netpDir.file("u2netp.onnx").asFile
val u2netpUrl = "https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx"
val u2netpMd5 = "8e83ca70e441ab06c318d82300c84806"

fun fileMd5(f: File): String {
    val digest = MessageDigest.getInstance("MD5")
    f.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadCharacterAiModel by tasks.registering {
    description = "Downloads the open-source background-removal model used by the AI Tutor's character upload feature."
    outputs.file(u2netpFile)
    doLast {
        if (u2netpFile.exists() && runCatching { fileMd5(u2netpFile) == u2netpMd5 }.getOrDefault(false)) {
            logger.lifecycle("character_ai: u2netp.onnx already present and verified, skipping download")
            return@doLast
        }
        u2netpDir.asFile.mkdirs()
        logger.lifecycle("character_ai: downloading u2netp.onnx (~4.7 MB, one-time)...")
        val tmp = File(u2netpFile.parentFile, "u2netp.onnx.part")
        URI(u2netpUrl).toURL().openStream().use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        val got = fileMd5(tmp)
        check(got == u2netpMd5) { "character_ai: u2netp.onnx checksum mismatch (got $got, expected $u2netpMd5) — aborting build" }
        if (!tmp.renameTo(u2netpFile)) { tmp.copyTo(u2netpFile, overwrite = true); tmp.delete() }
        logger.lifecycle("character_ai: u2netp.onnx ready")
    }
}

// ---------------------------------------------------------------------------------------------
// AI Tutor: automatic eye / mouth placement for owner-uploaded characters uses MediaPipe Face
// Landmarker (Apache-2.0, on-device). Its model file is fetched into assets at build time, like
// the background-removal model above. Unlike that one this download is best-effort: if it fails
// the build still succeeds and the app falls back to an estimate that the owner adjusts by hand.
// Pin the checksum below after the first successful build (the task prints it) so a changed
// download is refused from then on; empty = size check only.
// ---------------------------------------------------------------------------------------------
val faceModelFile = u2netpDir.file("face_landmarker.task").asFile
val faceModelUrl = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
val faceModelMd5 = ""

val downloadFaceLandmarkerModel by tasks.registering {
    description = "Downloads the MediaPipe Face Landmarker model used for automatic eye/mouth placement."
    outputs.file(faceModelFile)
    doLast {
        fun usable(f: File) = f.exists() && f.length() > 1_000_000 && (faceModelMd5.isEmpty() || runCatching { fileMd5(f) == faceModelMd5 }.getOrDefault(false))
        if (usable(faceModelFile)) {
            logger.lifecycle("character_ai: face_landmarker.task already present, skipping download")
            return@doLast
        }
        val tmp = File(faceModelFile.parentFile, "face_landmarker.task.part")
        try {
            faceModelFile.parentFile.mkdirs()
            logger.lifecycle("character_ai: downloading face_landmarker.task (~4 MB, one-time)...")
            URI(faceModelUrl).toURL().openStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            check(tmp.length() > 1_000_000) { "downloaded file is too small (${tmp.length()} bytes)" }
            val got = fileMd5(tmp)
            check(faceModelMd5.isEmpty() || got == faceModelMd5) { "checksum mismatch (got $got, expected $faceModelMd5)" }
            if (!tmp.renameTo(faceModelFile)) { tmp.copyTo(faceModelFile, overwrite = true); tmp.delete() }
            logger.lifecycle("character_ai: face_landmarker.task ready (md5 $got) — set faceModelMd5 to this value to pin it")
        } catch (e: Exception) {
            tmp.delete()
            logger.warn("character_ai: could not fetch face_landmarker.task (${e.message}). Automatic eye/mouth detection will fall back to an estimate.")
        }
    }
}


android {
    namespace = "com.rork.pro"
    compileSdk = 36

    defaultConfig {
        // Android package segments may not begin with a digit, so "7pro" is spelled out.
        applicationId = "com.aca.sevenpro"
        // 24 on purpose: the app must install on every phone. The Jitsi SDK itself needs 26, so the
        // live-class feature is switched off below Android 8 (see the tools:overrideLibrary line in
        // AndroidManifest.xml and the SDK_INT guards in SevenProApp / the classroom lobby).
        minSdk = 24
        targetSdk = 36
        // CI can stamp a build number without editing the file.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 210
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0.10"
        // The Jitsi Meet SDK pulls in enough classes to occasionally cross the
        // 64k dex limit in debug builds; harmless to leave on for release too.
        multiDexEnabled = true

        buildConfigField("String", "SUPABASE_URL", "\"${envValue("EXPO_PUBLIC_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${envValue("EXPO_PUBLIC_SUPABASE_ANON_KEY")}\"")

        // The AdMob application id is a build-time value (the ad unit ids are dynamic and come
        // from the database). Left blank, the ads SDK is never started and no ad is requested.
        // AdMob app ids are not secrets (they ship in plain text in every app's manifest), so a
        // hardcoded last-resort fallback is safe here and guarantees a missing/forgotten .env
        // can never silently turn ads off for the whole app.
        val admobAppId = envValue("EXPO_PUBLIC_ADMOB_APP_ID").ifBlank { envValue("ADMOB_APP_ID") }
            .ifBlank { "ca-app-pub-2143545712755970~9042955889" }
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        manifestPlaceholders["admobAppId"] = admobAppId
        // Static floor for ${jitsiOverrideLibraries} (see AndroidManifest.xml and the
        // androidComponents block below) in case the dynamic discovery can't resolve
        // dependencies for some reason (offline build, a flaky repo) — better a stale list than
        // an empty one. androidComponents.onVariants overwrites this with the live-discovered
        // value lazily, at manifest-merge time, once dependencies are actually resolved.
        manifestPlaceholders["jitsiOverrideLibraries"] = jitsiOverrideLibrariesFallback.joinToString(",")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 on so the AAB carries a deobfuscation (mapping) file — Play Console's "no
            // deobfuscation file" warning. Resource shrinking stays off on purpose: Jitsi/React
            // Native look drawables up by name at runtime.
            isMinifyEnabled = true
            // Native symbol tables for the .so files — clears the "no debug symbols" warning.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // MediaPipe memory-maps its model straight from the APK: it must be stored uncompressed.
    androidResources {
        noCompress += "task"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // The Jitsi Meet SDK (React Native + several native libs under the
            // hood) ships duplicate metadata that otherwise fails packaging.
            excludes += "/META-INF/rxjava.properties"
            excludes += "/META-INF/library-*_release.kotlin_module"
            pickFirsts += "/lib/**/libc++_shared.so"
            pickFirsts += "/lib/**/libjsc.so"
            pickFirsts += "/lib/**/libfbjni.so"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

/**
 * Pulls the `package` attribute out of an AAR's own AndroidManifest.xml. Unlike an APK's, an
 * AAR's AndroidManifest.xml is plain text (not compiled binary), so this is a normal XML parse.
 */
fun readAarManifestPackage(aarFile: File): String? {
    if (!aarFile.exists() || !aarFile.name.endsWith(".aar")) return null
    return try {
        ZipFile(aarFile).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return null
            zip.getInputStream(entry).use { stream ->
                val doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(stream)
                doc.documentElement.getAttribute("package").takeIf { it.isNotBlank() }
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Every react-native-* module the Jitsi Meet SDK bundles is republished by Jitsi's own build with
 * a "-jitsi-<qualifier>" version tag (see jitsi/jitsi-meet android/build.gradle: "Release every
 * dependency the SDK has with a -jitsi-XXX qualified version"), and every one of them declares
 * minSdk 26 in its own manifest while this app supports 24+. Rather than hand-maintain the
 * <uses-sdk tools:overrideLibrary="..."> package list and hit a fresh "Manifest merger failed"
 * one library at a time whenever Jitsi adds or renames a bundled module (this is exactly how
 * react-native-giphy and react-native-video showed up after the 9.2.2 -> 11.6.3 jump), this walks
 * the given variant's resolved runtime classpath, finds every artifact with that "-jitsi-"
 * qualifier, and reads its real package name straight out of its own AAR.
 */
fun Project.computeJitsiOverrideLibraries(variantName: String): String {
    val discovered = linkedSetOf<String>()
    try {
        val config = configurations.findByName("${variantName}RuntimeClasspath")
        config?.resolvedConfiguration?.lenientConfiguration?.artifacts?.forEach { artifact ->
            if (!artifact.moduleVersion.id.version.contains("-jitsi-")) return@forEach
            readAarManifestPackage(artifact.file)?.let { discovered += it }
        }
    } catch (e: Exception) {
        logger.warn("jitsiOverrideLibraries: dynamic discovery failed for $variantName, using the static fallback only (${e.message})")
    }
    return (jitsiOverrideLibrariesFallback + discovered).distinct().joinToString(",")
}

// Overwrites the static manifestPlaceholders default set in defaultConfig above with the live-
// discovered list. variant.manifestPlaceholders.put takes a Provider, so this is only evaluated
// lazily at manifest-merge time — by which point the variant's runtime classpath is genuinely
// resolvable — never eagerly during configuration.
androidComponents {
    onVariants { variant ->
        variant.manifestPlaceholders.put(
            "jitsiOverrideLibraries",
            provider { computeJitsiOverrideLibraries(variant.name) }
        )
    }
}

/**
 * Same idea as [readAarManifestPackage], but pulls the `<uses-sdk android:minSdkVersion="...">`
 * value straight out of the AAR's own (uncompiled) AndroidManifest.xml too, when the library
 * declares one explicitly. A missing `<uses-sdk>` (or a missing minSdkVersion attribute on it)
 * means "this AAR declares no explicit floor" — returned as null — not "minSdk 1"; callers must
 * treat null as nothing to flag, never as an omitted 1.
 */
fun readAarManifestMinSdk(aarFile: File): Int? {
    if (!aarFile.exists() || !aarFile.name.endsWith(".aar")) return null
    return try {
        ZipFile(aarFile).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return null
            zip.getInputStream(entry).use { stream ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
                val usesSdkNodes = doc.documentElement.getElementsByTagName("uses-sdk")
                if (usesSdkNodes.length == 0) return null
                val usesSdk = usesSdkNodes.item(0) as org.w3c.dom.Element
                usesSdk
                    .getAttributeNS("http://schemas.android.com/apk/res/android", "minSdkVersion")
                    .takeIf { it.isNotBlank() }
                    ?.toIntOrNull()
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Diagnostic companion to [computeJitsiOverrideLibraries]: instead of that function's narrow
 * "-jitsi-" qualifier heuristic, this walks EVERY AAR on a variant's resolved runtime classpath,
 * reads each one's own declared minSdkVersion, and reports every package whose floor sits above
 * this app's minSdk in one pass — the same set the manifest merger would otherwise surface one
 * "uses-sdk:minSdkVersion N cannot be smaller than version M declared in library ..." failure at
 * a time, on whichever dependency happens to be resolved next.
 *
 * Usage:
 *   ./gradlew listLibrariesNeedingOverride
 *   ./gradlew listLibrariesNeedingOverride -PoverrideVariant=debug   (defaults to "release")
 */
tasks.register("listLibrariesNeedingOverride") {
    group = "help"
    description = "Scans resolved dependencies and lists every AAR whose minSdkVersion is above this app's minSdk."
    doLast {
        val variantName = (project.findProperty("overrideVariant") as String?) ?: "release"
        val appMinSdk = android.defaultConfig.minSdk
            ?: error("android.defaultConfig.minSdk is not set")
        val config = configurations.findByName("${variantName}RuntimeClasspath")
            ?: error("No such configuration: ${variantName}RuntimeClasspath (check -PoverrideVariant)")

        data class Offender(val pkg: String, val minSdk: Int, val coordinates: String)

        val offenders = sortedSetOf(compareBy<Offender> { it.pkg })
        config.resolvedConfiguration.lenientConfiguration.artifacts.forEach { artifact ->
            if (!artifact.file.name.endsWith(".aar")) return@forEach
            val minSdk = readAarManifestMinSdk(artifact.file) ?: return@forEach
            if (minSdk <= appMinSdk) return@forEach
            val pkg = readAarManifestPackage(artifact.file) ?: return@forEach
            offenders += Offender(pkg, minSdk, artifact.moduleVersion.id.toString())
        }

        if (offenders.isEmpty()) {
            println("No resolved '$variantName' dependency declares a minSdkVersion above $appMinSdk.")
            return@doLast
        }

        println("Libraries needing tools:overrideLibrary (app minSdk = $appMinSdk, variant = $variantName):")
        offenders.forEach { println("  ${it.pkg}  (minSdk ${it.minSdk}, from ${it.coordinates})") }
        println()
        println("Paste-ready list (comma-separated, for tools:overrideLibrary or jitsiOverrideLibrariesFallback):")
        println(offenders.joinToString(",") { it.pkg })
    }
}

// Safety net: block the legacy ExoPlayer2 group from entering the classpath through ANY
// dependency path (not just Jitsi), since its UI resources collide with androidx.media3-ui
// and cause ClassCastExceptions when inflating video player controls (see LessonVideo.kt).
configurations.all {
    exclude(group = "com.google.android.exoplayer")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.androidx.compose)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    // Virtual Classroom: native video/audio/screen-share conferencing. localbroadcastmanager is
    // pulled in transitively by the Jitsi SDK too, but we import it directly (BroadcastEvent
    // listening in ClassroomCallActivity) so it's declared explicitly here as well.
    // Jitsi bundles the legacy ExoPlayer2 UI library internally; it ships layout resources
    // (exo_player_control_view.xml, exo_progress, etc.) with the same names as androidx.media3-ui,
    // which the lesson video player (LessonVideo.kt) uses. Letting both onto the classpath causes
    // a resource-merge collision where the wrong TimeBar class gets inflated at runtime
    // (ClassCastException: exoplayer2 DefaultTimeBar -> media3 TimeBar). Excluded here so only
    // media3's copy exists in the final app.
    implementation(libs.jitsi.meet.sdk) {
        exclude(group = "com.google.android.exoplayer")
    }
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.play.services.ads)
    implementation(libs.androidx.work.runtime)
    // Incoming-call-style push for Virtual Classroom invites: a high-priority FCM data message
    // wakes the device (even locked/app-closed) and opens IncomingClassroomCallActivity — see
    // push/ClassroomFcmService. Everything else in the app still uses Supabase directly; this is
    // the one place Firebase is used, purely as the delivery transport for that one alert.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // Live Voice Translation: WebSocket to the self-hosted open-source translation server
    // (classroom/translation). OkHttp already ships inside the app through Coil and the Jitsi SDK;
    // it is declared here because the translator uses it directly.
    implementation(libs.okhttp)
    // AI Tutor face-to-face call: Filament (Apache-2.0) renders the realistic 3D tutor on-device.
    // A rendering engine, not an AI model — the tutor's thinking, hearing and voice all run on
    // the Cloudflare Worker (see ai-tutor-worker/). gltfio loads the .glb avatar; filament-utils
    // provides the ModelViewer + camera helpers.
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio.android)
    implementation(libs.filament.utils.android)

    // AI Tutor: background removal for owner-uploaded characters. ONNX Runtime Mobile
    // (MIT license) runs the open U-2-Net-p model entirely on-device — no server, no cost.
    implementation(libs.onnxruntime.android)

    // AI Tutor: automatic eye / mouth detection for owner-uploaded characters. MediaPipe Face
    // Landmarker (Apache-2.0) runs on-device on the model downloaded by downloadFaceLandmarkerModel.
    // It adds native libraries to the APK — measure the size, and drop this line plus
    // character/MediaPipeLandmarks.kt if it is too heavy (the estimate fallback keeps working).
    implementation(libs.mediapipe.tasks.vision)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    debugImplementation(libs.androidx.ui.tooling)
}

// Every build (debug or release) fetches the character-AI model first, so `preBuild` — which
// every variant's compile task already depends on — is the one hook that can't be missed.
tasks.named("preBuild") { dependsOn(downloadCharacterAiModel, downloadFaceLandmarkerModel) }
