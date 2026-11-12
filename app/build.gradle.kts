import java.util.Properties

// ---------------------------------------------------------------------------
//  Everything the app looks like and does comes from app-config.properties.
//  This script also validates that file, so a typo produces a readable error
//  instead of a cryptic aapt/Gradle stack trace.
// ---------------------------------------------------------------------------

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appConfigFile = rootProject.file("app-config.properties")

val appConfig = Properties().apply {
    if (appConfigFile.exists()) {
        appConfigFile.inputStream().use { load(it) }
    }
}

fun failConfig(message: String): Nothing =
    throw GradleException("app-config.properties -> $message")

/** Reads a key, falling back when it is missing or blank. */
fun cfg(key: String, fallback: String): String =
    appConfig.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

fun cfgColor(key: String, fallback: String): String {
    val value = cfg(key, fallback)
    if (!value.matches(Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"))) {
        failConfig("$key = \"$value\" is not a colour. Use #RGB, #RRGGBB or #AARRGGBB (keep the #).")
    }
    return value
}

fun cfgBool(key: String, fallback: String): String {
    val value = cfg(key, fallback).lowercase()
    if (value != "true" && value != "false") {
        failConfig("$key = \"$value\" must be true or false.")
    }
    return value
}

// ---- identity --------------------------------------------------------------

val appIdValue = cfg("app_id", "com.webviewshell")
if (!appIdValue.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))) {
    failConfig(
        "app_id = \"$appIdValue\" is not a valid Android application id.\n" +
            "    Use letters, digits, underscores and dots only - at least two segments, e.g.\n" +
            "        app_id=com.yourname.yourapp\n" +
            "    Dashes (-), spaces and segments starting with a digit are not allowed."
    )
}

val startUrlValue = cfg("start_url", "https://example.com")
if (!startUrlValue.startsWith("http://") && !startUrlValue.startsWith("https://")) {
    failConfig("start_url = \"$startUrlValue\" must start with http:// or https://")
}

// ---- drawables / logo ------------------------------------------------------

val resRoot = file("src/main/res")
val drawableDirs = resRoot.listFiles().orEmpty()
    .filter { it.isDirectory && (it.name == "drawable" || it.name.startsWith("drawable-")) }

val invalidDrawables = drawableDirs.flatMap { dir ->
    dir.listFiles().orEmpty().filter { file ->
        !file.name.substringBeforeLast('.').matches(Regex("^[a-z][a-z0-9_]*$"))
    }
}
if (invalidDrawables.isNotEmpty()) {
    failConfig(
        "These drawable files have invalid names:\n" +
            invalidDrawables.joinToString("\n") { "        " + it.relativeTo(resRoot).path } +
            "\n    Android resource file names may only contain lowercase a-z, 0-9 and _\n" +
            "    (no dashes, spaces or capitals). Example: rename \"my-logo.xml\" to \"my_logo.xml\"."
    )
}

val logoName = cfg("app_logo", "logo")
if (!logoName.matches(Regex("^[a-z][a-z0-9_]*$"))) {
    failConfig(
        "app_logo = \"$logoName\" is not a valid drawable name.\n" +
            "    Use lowercase a-z, 0-9 and _ only, without the file extension."
    )
}
val logoExists = drawableDirs.any { dir ->
    dir.listFiles().orEmpty().any { it.name.substringBeforeLast('.') == logoName }
}
if (!logoExists) {
    failConfig(
        "app_logo = \"$logoName\" not found.\n" +
            "    Add app/src/main/res/drawable/$logoName.png (or .xml), or point app_logo at an existing file."
    )
}

// ---- build wiring ----------------------------------------------------------

val brandColor = cfgColor("color_brand", "#4338CA")
val generatedResDir = layout.buildDirectory.dir("generated/appConfig/res")

// The logo name in the config becomes a drawable alias, so the launcher icon,
// splash screen and error screen all follow it automatically.
val generateAppLogoAlias = tasks.register("generateAppLogoAlias") {
    val logo = logoName
    val outputDir = generatedResDir.get().asFile
    inputs.property("logo", logo)
    outputs.dir(outputDir)
    doLast {
        val valuesDir = File(outputDir, "values").apply { mkdirs() }
        File(valuesDir, "app_logo.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <item name="app_logo" type="drawable">@drawable/$logo</item>
            </resources>
            """.trimIndent() + "\n"
        )
    }
}

tasks.named("preBuild") { dependsOn(generateAppLogoAlias) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Resources") }
    .configureEach { dependsOn(generateAppLogoAlias) }

android {
    namespace = "com.webviewshell"
    compileSdk = 35

    defaultConfig {
        applicationId = appIdValue
        minSdk = 26
        targetSdk = 35
        versionCode = cfg("version_code", "1").toIntOrNull() ?: 1
        versionName = cfg("version_name", "1.0.0")

        resourceConfigurations += listOf("en")

        // ---- strings -------------------------------------------------
        resValue("string", "app_name", cfg("app_name", "WebViewShell"))
        resValue("string", "start_url", startUrlValue)

        // ---- colors --------------------------------------------------
        resValue("color", "brand", brandColor)
        resValue("color", "brand_dark", cfgColor("color_brand_dark", "#312E81"))
        resValue("color", "brand_light", cfgColor("color_brand_light", "#6366F1"))
        resValue("color", "on_brand", cfgColor("color_on_brand", "#FFFFFF"))
        resValue("color", "window_background", cfgColor("color_background", "#FFFFFF"))
        resValue("color", "text_primary", cfgColor("color_text_primary", "#111827"))
        resValue("color", "text_secondary", cfgColor("color_text_secondary", "#6B7280"))
        resValue("color", "system_bar", cfgColor("color_status_bar", brandColor))
        resValue("color", "ic_launcher_background", cfgColor("color_icon_background", brandColor))
        resValue("color", "splash_background", cfgColor("color_splash_background", brandColor))

        // ---- behaviour toggles ---------------------------------------
        resValue("bool", "enable_pull_to_refresh", cfgBool("enable_pull_to_refresh", "true"))
        resValue(
            "bool",
            "keep_audio_playing_in_background",
            cfgBool("keep_audio_playing_in_background", "true")
        )
        resValue(
            "bool",
            "open_non_web_links_in_other_apps",
            cfgBool("open_non_web_links_in_other_apps", "false")
        )
        resValue("bool", "show_loading_bar", cfgBool("show_loading_bar", "true"))
        resValue("bool", "enable_pinch_zoom", cfgBool("enable_pinch_zoom", "false"))

        // Goes straight into AndroidManifest.xml (resource refs are not reliable
        // for this boolean attribute, manifest placeholders are).
        manifestPlaceholders["cleartextTraffic"] = cfgBool("allow_cleartext_traffic", "true")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release builds are unsigned on purpose. Add your own keystore here
            // before publishing.
        }
    }

    sourceSets.getByName("main").res.srcDir(generatedResDir.get().asFile.path)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
}
