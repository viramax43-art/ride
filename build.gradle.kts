import java.io.File

plugins {
    id("com.android.application") version "9.0.1" apply false
    id("com.android.legacy-kapt") version "9.0.1" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
}

allprojects {
    buildDir = File(System.getProperty("user.home"), ".codex-builds/ride/$name")
}
