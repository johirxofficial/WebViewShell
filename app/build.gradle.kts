import java.util.Properties

// ---------------------------------------------------------------------------
//  Everything the app looks like and does comes from app-config.properties.
//  Nothing in this file needs to be edited to customise the app.
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

/** Reads a key from app-config.properties, falling back when blank/missing. */
fun cfg(key: String, fallback: String): String =
    appConfig.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

val brandColor = cfg("color_brand", "#4338CA")
val logoDrawable = cfg("app_logo", "logo")
val generatedResDir = layout.buildDirectory.dir("generated/appConfig/res")

// The logo name in the config becomes a drawable alias, so the manifest,
// splash screen and error screen all follow it automatically.
val generateAppLogoAlias = tasks.register("generateAppLogoAlias") {
    val logo = logoDrawable
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
        applicationId = cfg("app_id", "com.webviewshell")
        minSdk = 26
        targetSdk = 35
        versionCode = cfg("version_code", "1").toIntOrNull() ?: 1
        versionName = cfg("version_name", "1.0.0")

        resourceConfigurations += listOf("en")

        // ---- strings -------------------------------------------------
        resValue("string", "app_name", cfg("app_name", "WebViewShell"))
        resValue("string", "start_url", cfg("start_url", "https://example.com"))

        // ---- colors --------------------------------------------------
        resValue("color", "brand", brandColor)
        resValue("color", "brand_dark", cfg("color_brand_dark", "#312E81"))
        resValue("color", "brand_light", cfg("color_brand_light", "#6366F1"))
        resValue("color", "on_brand", cfg("color_on_brand", "#FFFFFF"))
        resValue("color", "window_background", cfg("color_background", "#FFFFFF"))
        resValue("color", "text_primary", cfg("color_text_primary", "#111827"))
        resValue("color", "text_secondary", cfg("color_text_secondary", "#6B7280"))
        resValue("color", "system_bar", cfg("color_status_bar", brandColor))
        resValue("color", "ic_launcher_background", cfg("color_icon_background", brandColor))
        resValue("color", "splash_background", cfg("color_splash_background", brandColor))

        // ---- behaviour toggles ---------------------------------------
        resValue("bool", "enable_pull_to_refresh", cfg("enable_pull_to_refresh", "true"))
        resValue(
            "bool",
            "keep_audio_playing_in_background",
            cfg("keep_audio_playing_in_background", "true")
        )
        resValue(
            "bool",
            "open_non_web_links_in_other_apps",
            cfg("open_non_web_links_in_other_apps", "false")
        )
        resValue("bool", "show_loading_bar", cfg("show_loading_bar", "true"))
        resValue("bool", "enable_pinch_zoom", cfg("enable_pinch_zoom", "false"))

        // Goes straight into AndroidManifest.xml (resource refs are not
        // reliable for this boolean attribute, placeholders are).
        manifestPlaceholders["cleartextTraffic"] = cfg("allow_cleartext_traffic", "true")
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
