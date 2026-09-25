buildscript {
    dependencies {
        // AGP 9 ships built-in Kotlin support; pin KGP to the version GeckoView is built with.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
