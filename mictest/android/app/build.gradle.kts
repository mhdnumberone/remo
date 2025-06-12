plugins {
    id("com.android.application")
    kotlin("android") // This is another way to write id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")

    // FIX: Add the version that matches your project's Kotlin version
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.22"
}
val lifecycleVersion = "2.7.0"
val okHttpVersion = "4.12.0"
val coroutinesVersion = "1.7.3"

android {
    namespace = "com.example.mictest"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    // ✅ تحسين Kotlin configuration
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    defaultConfig {
        applicationId = "com.example.mictest"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // ✅ إضافة multiDexEnabled للمشاريع الكبيرة
        multiDexEnabled = true

        // ✅ إضافة test instrumentation runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ✅ إضافة manifest placeholders للـ network security
        manifestPlaceholders["usesCleartextTraffic"] = "true"
    }

    // ✅ تحسين buildTypes
    buildTypes {
        release {
            isMinifyEnabled = true // ✅ تفعيل للإنتاج
            isShrinkResources = true // ✅ تفعيل للإنتاج
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // ✅ إضافة packaging options لحل تضارب الملفات
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/gradle/incremental.annotation.processors"
        }
    }

    // ✅ إضافة lint options
    lint {
        disable += "MissingTranslation"
        disable += "ExtraTranslation"
        checkReleaseBuilds = false
        abortOnError = false
    }
}

flutter {
    source = "../.."
}

dependencies {
    // ✅ Kotlin Standard Library مع BOM
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.10"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // FIX: Add the serialization library. This is CRITICAL for the build to succeed.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // ✅ Kotlin Coroutines (مطلوب للـ MainActivity المحسن)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${coroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")

    // Core Android Libraries
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // ✅ MultiDex Support
    implementation("androidx.multidex:multidex:2.0.1")

    // Lifecycle Components (محسن)
    implementation("androidx.lifecycle:lifecycle-process:${lifecycleVersion}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${lifecycleVersion}")
    implementation("androidx.lifecycle:lifecycle-service:${lifecycleVersion}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${lifecycleVersion}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${lifecycleVersion}")

    // ✅ OkHttp with proper BOM management
    implementation(platform("com.squareup.okhttp3:okhttp-bom:${okHttpVersion}"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    // ✅ JSON Parsing (أفضل من org.json)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.json:json:20231013") // backup للتوافق

    // ✅ Background Processing Support محسن
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.work:work-gcm:2.9.0") // للـ GCM compatibility

    // ✅ Network and Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ✅ Permissions and Settings
    implementation("androidx.preference:preference-ktx:1.2.1")

    // ✅ Enhanced Notification Support
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ✅ Development Tools (Debug only)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")

    // Testing Dependencies (محسن)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${coroutinesVersion}")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
}

// ✅ إضافة task لتنظيف الـ build
tasks.register("cleanBuildCache") {
    doLast {
        delete(layout.buildDirectory.dir("tmp"))
        delete(layout.buildDirectory.dir("intermediates"))
    }
}