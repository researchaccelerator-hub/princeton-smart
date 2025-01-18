import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.secrets)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {

    namespace = "com.screenlake"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.screenlake"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    configurations.all {
        exclude(group = "com.google.protobuf", module = "protobuf-lite")
        exclude(group = "com.google.firebase", module = "protolite-well-known-types")
        exclude(group = "com.google.protobuf", module = "protobuf-java-util")
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
            isMinifyEnabled = true
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
        }

        debug {
            isMinifyEnabled = false
            isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"

            buildConfigField("String", "AMAZON_REGION_NAME", "\"$amazonRegionName\"")
            buildConfigField("String", "AMAZON_BUCKET_NAME", "\"$amazonBucketName\"")
            buildConfigField("String", "AMAZON_BUCKET_URL", "\"$amazonBucketUrl\"")
            buildConfigField("String", "COGNITO_IDENTITY_POOL_ID", "\"$cognitoIdentityPoolId\"")
            buildConfigField("String", "COGNITO_POOL_ID", "\"$cognitoUserPoolId\"")
            buildConfigField("String", "COGNITO_APP_CLIENT_ID", "\"$cognitoAppClientId\"")
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(platform(libs.squareup.okhttp3.bom))

    implementation(libs.adaptech.tesseract4Android)
    implementation(libs.airbnb.lottie)
    implementation(libs.android.support.annotations)
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

    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.google.truth)
    androidTestImplementation(libs.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockito.core)
    androidTestImplementation(libs.mockk.android)
    implementation(libs.datastore.preferences)
}