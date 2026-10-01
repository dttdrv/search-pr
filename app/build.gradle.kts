plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.pane.browser"
    // GeckoView requires the 37.1 minor SDK.
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "app.pane.browser"
        minSdk = 26
        targetSdk = 36
        // The version lives in /VERSION; bumping it on the default branch publishes a release
        // (see .github/workflows/release.yml), which can also pass these explicitly.
        val version = providers.gradleProperty("pane.versionName").orNull
            ?: rootProject.file("VERSION").readText().trim()
        versionName = version
        versionCode = providers.gradleProperty("pane.versionCode").orNull?.toInt() ?: run {
            // 1.2.3 → 10203, so every release installs over the previous one.
            val (major, minor, patch) = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
            major * 10000 + minor * 100 + patch
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val abis = providers.gradleProperty("pane.abis").orNull
        if (abis != null) {
            ndk { abiFilters += abis.split(",") }
        }
    }

    // One APK per CPU type keeps downloads small: GeckoView's native code is most of the size.
    splits {
        abi {
            isEnable = providers.gradleProperty("pane.splitAbis").isPresent
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    signingConfigs {
        // The release key comes from the environment (a CI secret), never from the repository.
        val keystore = providers.environmentVariable("PANE_KEYSTORE").orNull
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("PANE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("PANE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("PANE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release key (plain CI builds) the debug key keeps the APK installable.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/*.kotlin_module", "DebugProbesKt.bin")
        // GeckoView loads its libraries straight from the APK.
        jniLibs.useLegacyPackaging = false
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        disable += setOf("MissingTranslation", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation("app.pane:core")
    implementation(libs.geckoview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
