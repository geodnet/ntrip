plugins {
    // AGP 9+ ships built-in Kotlin support; do not also apply org.jetbrains.kotlin.android
    // (it duplicate-registers the "kotlin" extension AGP already creates).
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
