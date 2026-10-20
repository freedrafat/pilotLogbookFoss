import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinx.serialization)
}

val timestamp = ZonedDateTime.now(ZoneOffset.UTC)
    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))!!

android {
    namespace = "com.my.striptologbook"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.my.striptologbook"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Append timestamp to versionName for debug builds only
            versionNameSuffix = timestamp
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            vcsInfo {
                include = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}


androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            // versionName is a Property<String> on the output
            val versionName = output.versionName.get()

            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set("pilotLogbookFoss-${versionName}.apk")
            }
        }
    }
}

//androidComponents {
//    onVariants { variant ->
//        variant.outputs.forEach { output ->
//            output.outputFileName.set(
//                project.provider {
//                        "pilotLogbookFoss_${timestamp}.apk"
//                }
//            )
//        }
//    }
//}


dependencies {

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.sqlite)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)

    // Compose BOM and core (THE MISSING LINE)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Room – use KSP instead of kapt
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.ksp)          // KSP annotation processor

    // Coroutines
    runtimeOnly(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.monitor)
    androidTestRuntimeOnly(libs.androidx.runner)
    androidTestRuntimeOnly(libs.kotlinx.coroutines.test)

    // Compose UI Tooling (this line fixes the ui-tooling error)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugRuntimeOnly(libs.androidx.compose.ui.test.manifest)

    implementation(libs.okhttp)

    implementation(libs.androidx.documentfile)
}