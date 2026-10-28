plugins {
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
}

tasks.wrapper {
    gradleVersion = "8.13"
    distributionType = Wrapper.DistributionType.BIN
    validateDistributionUrl = false
}
