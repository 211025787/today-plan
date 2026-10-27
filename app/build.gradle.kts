import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 版本号自增：打包时从 version.properties 读取，assemble 完成后自动 +1 写回
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}
val currentVersionCode = versionProps.getProperty("versionCode", "1").toInt()
val currentVersionName = versionProps.getProperty("versionName", "1.0.$currentVersionCode")

android {
    namespace = "com.focuslist.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.focuslist.app"
        minSdk = 26
        targetSdk = 34
        versionCode = currentVersionCode
        versionName = currentVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

// 打包完成后版本号 +1（本次仍用当前值，下次打包生效）
val bumpVersion = tasks.register("bumpVersionCode") {
    doLast {
        val nextCode = currentVersionCode + 1
        versionProps.setProperty("versionCode", nextCode.toString())
        versionProps.setProperty("versionName", "1.0.$nextCode")
        versionPropsFile.writer().use { versionProps.store(it, "auto-incremented after assemble") }
        println(">>> 版本号已自增：$currentVersionCode -> $nextCode（versionName 1.0.$nextCode）")
    }
}
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    finalizedBy(bumpVersion)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}