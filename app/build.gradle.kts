import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.google.services)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

// google-services.json is gitignored, like local.properties, and absent in CI. Without it, the
// Google Services plugin's own per-variant task hard-fails with no built-in optional mode.
// Disabled below when it's missing; a real build with the file present is unaffected.
val googleServicesJsonExists = project.file("google-services.json").exists()

android {

    namespace = "com.screenlake"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.screenlake"
        minSdk = 28
        targetSdk = 35
        versionCode = 52
        versionName = "1.51.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    configurations.all {
        exclude(group = "com.google.protobuf", module = "protobuf-lite")
        exclude(group = "com.google.firebase", module = "protolite-well-known-types")
        // protobuf-java-util intentionally NOT excluded: AGP 8.13+ assembles the UTP
        // test driver classpath through project configurations, so excluding it here
        // strips Timestamps from the driver and breaks all instrumented test runs.
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }

    // Load local.properties file
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    // Extract properties with default fallback values
    val amazonRegionName: String = localProperties.getProperty("AMAZON_REGION_NAME", "default-region")
    val amazonBucketName: String = localProperties.getProperty("AMAZON_BUCKET_NAME", "https://default-bucket-url.com")
    val amazonBucketUrl: String = localProperties.getProperty("AMAZON_BUCKET_URL", "https://default-bucket-url.com")
    val cognitoIdentityPoolId: String = localProperties.getProperty("COGNITO_IDENTITY_POOL_ID", "default")
    val cognitoUserPoolId: String = localProperties.getProperty("COGNITO_POOL_ID", "default")
    val cognitoAppClientId: String = localProperties.getProperty("COGNITO_APP_CLIENT_ID", "default")

    buildTypes {
        release {
            // Left off: full-mode R8 currently fails independent of anything in this repo,
            // even with a real google-services.json present. Firebase Crashlytics's mapping-
            // file-upload task (created automatically whenever this is on) throws "Google-Services
            // plugin not found" — a NoClassDefFoundError on com.google.gms.googleservices.
            // GoogleServicesTask when Crashlytics's AppIdFetcher looks it up, even though that
            // class is present in the resolved google-services 4.4.4 jar and plugin declaration
            // order doesn't affect it. Looks like a genuine version-compatibility gap between
            // firebase-crashlytics-gradle 3.0.6 and google-services 4.4.4/AGP 8.13.2. Re-enable
            // only after confirming a working plugin version combination with a full
            // `./gradlew assembleRelease` (minified) locally first.
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"

            buildConfigField("String", "AMAZON_REGION_NAME", "\"$amazonRegionName\"")
            buildConfigField("String", "AMAZON_BUCKET_NAME", "\"$amazonBucketName\"")
            buildConfigField("String", "AMAZON_BUCKET_URL", "\"$amazonBucketUrl\"")
            buildConfigField("String", "COGNITO_IDENTITY_POOL_ID", "\"$cognitoIdentityPoolId\"")
            buildConfigField("String", "COGNITO_POOL_ID", "\"$cognitoUserPoolId\"")
            buildConfigField("String", "COGNITO_APP_CLIENT_ID", "\"$cognitoAppClientId\"")

            // ZipFileWorker references this unconditionally; its check is always guarded by
            // `BuildConfig.DEBUG &&` so the value here is unreachable, but the release variant
            // still needs the symbol to exist to compile.
            buildConfigField("Boolean", "DEBUG_ZIP_EXPORT", "false")
        }

        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"

            buildConfigField("String", "AMAZON_REGION_NAME", "\"$amazonRegionName\"")
            buildConfigField("String", "AMAZON_BUCKET_NAME", "\"$amazonBucketName\"")
            buildConfigField("String", "AMAZON_BUCKET_URL", "\"$amazonBucketUrl\"")
            buildConfigField("String", "COGNITO_IDENTITY_POOL_ID", "\"$cognitoIdentityPoolId\"")
            buildConfigField("String", "COGNITO_POOL_ID", "\"$cognitoUserPoolId\"")
            buildConfigField("String", "COGNITO_APP_CLIENT_ID", "\"$cognitoAppClientId\"")

            // Set to true to export a copy of each zip to external storage for local inspection.
            // Pull with: adb pull /sdcard/Android/data/com.screenlake/files/debug_zips/ ./debug-output/
            // Or use: python3 claude-docs/scripts/pull_debug_zip.py
            buildConfigField("Boolean", "DEBUG_ZIP_EXPORT", "false")
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("src/androidTest/assets")
        }
    }

    packaging {
        resources {
            excludes += "META-INF/LICENSE-notice.md"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        error += "PageAlignedJni"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
}

// The Google Services plugin's own per-variant task hard-fails when google-services.json is
// absent, with no built-in optional mode. Disable it in that case (CI/CodeQL); a real build with
// the file present is unaffected.
if (!googleServicesJsonExists) {
    tasks.matching { it.name.matches(Regex("process.*GoogleServices")) }.configureEach {
        enabled = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.squareup.okhttp3.bom))

    implementation(libs.adaptech.tesseract4Android)
    implementation(libs.airbnb.lottie)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.room.common)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.test.core.ktx)
    implementation(libs.androidx.test.ext.junit.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.testing)
    implementation(libs.amplify.aws.api)
    implementation(libs.amplify.aws.auth.cognito)
    implementation(libs.amplify.aws.storage.s3)
    implementation(libs.amplify.core.kotlin)
    implementation(libs.apache.commons.csv)
    implementation(libs.devrel.easy.permissions)
    implementation(libs.devtools.ksp)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.google.gson)
    implementation(libs.google.hilt)
    implementation(libs.google.material)
    implementation(libs.google.play.services.auth)
    implementation(libs.jakewharton.timber)
    implementation(libs.jetbrains.kotlinx.coroutines.android)
    implementation(libs.squareup.okhttp3)
    implementation(libs.squareup.okhttp3.logging.interceptor)
    implementation(libs.squareup.retrofit2)
    implementation(libs.squareup.retrofit2.converter.gson)

    ksp(libs.google.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)

    implementation("androidx.sqlite:sqlite-framework:2.4.0")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit)
//    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockito.core)
    androidTestImplementation(libs.mockk.android)
    implementation(libs.datastore.preferences)
}
