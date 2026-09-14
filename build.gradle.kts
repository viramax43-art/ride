import java.io.File

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.legacy.kapt) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.google.services) apply false
}

allprojects {
    buildDir = File(System.getProperty("user.home"), ".codex-builds/ride/$name")
}
