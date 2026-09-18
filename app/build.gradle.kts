plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
}
android {
    namespace = "com.microllate.miuyellowpage"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.microllate.miuyellowpage"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.highcapable.yukihookapi:api:1.2.1")
    ksp("com.highcapable.yukihookapi:ksp-xposed:1.2.1")
    compileOnly("de.robv.android.xposed:api:82")
}
