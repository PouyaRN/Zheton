import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    // AGP 9 brings its own Kotlin support; applying kotlin-android on top is an error now.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Room's annotation processor. The first processor in this build, so it is also the first
    // time KSP is applied at all.
    alias(libs.plugins.ksp)
}

// The generated schema JSON is committed. It is what makes a Room migration testable rather
// than something you find out about on someone's phone, and durable.db is the database that
// must never be dropped and rebuilt.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.doxigo.muchtoman"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pouyarn.zheton"
        minSdk = 24
        targetSdk = 36
        versionCode = providers.gradleProperty("zheton.versionCode").orNull?.toInt()
            ?: providers.gradleProperty("muchtoman.versionCode").orNull?.toInt()
            ?: 2_000_001_000
        versionName = providers.gradleProperty("zheton.versionName").orNull
            ?: providers.gradleProperty("muchtoman.versionName").orNull
            ?: "1.0.5"
        buildConfigField("boolean", "LITE", "false")
    }

    val keystoreProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let {
        Properties().apply { it.inputStream().use(::load) }
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    val ratesUrl = providers.gradleProperty("zheton.ratesUrl")
        .orElse(providers.gradleProperty("muchtoman.ratesUrl"))
        .getOrElse("https://rates.zheton.app/rates")

    val syncUrl = providers.gradleProperty("zheton.syncUrl")
        .orElse(providers.gradleProperty("muchtoman.syncUrl"))
        .getOrElse("https://sync.zheton.app")

    buildTypes {
        debug {
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "RATES_URL", "\"$ratesUrl\"")
            buildConfigField("String", "SYNC_URL", "\"$syncUrl\"")
        }
        release {
            buildConfigField("String", "RATES_URL", "\"$ratesUrl\"")
            buildConfigField("String", "SYNC_URL", "\"$syncUrl\"")
            if (keystoreProps != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.zxing.core)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}

configurations.configureEach {
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

