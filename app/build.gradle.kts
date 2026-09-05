plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.sellernest.poreceiving"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sellernest.poreceiving"
        // Covers the rugged fleet (Zebra TC-series, Honeywell CT-series) in practice
        // while allowing modern APIs. See requirements §2.1.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Overridden per build type below; read at runtime via BuildConfig.API_BASE_URL.
        buildConfigField("String", "API_BASE_URL", "\"https://prod-api.example.com/api/mobile/receiving/\"")

        // §5.1: OAuth 2.0 Authorization Code + PKCE against Keycloak, via a
        // dedicated public client provisioned for this app (backend issue #1729).
        // OAUTH_ISSUER is the realm base -- AuthConfig appends the standard
        // Keycloak `/protocol/openid-connect/{auth,token}` suffixes.
        buildConfigField("String", "OAUTH_ISSUER", "\"https://auth.example.com/realms/sellernest\"")
        buildConfigField("String", "OAUTH_CLIENT_ID", "\"po-receiving-android\"")

        // AppAuth's RedirectUriReceiverActivity (merged in from its own manifest)
        // uses this placeholder to register the redirect URI's custom scheme.
        // Kept equal to applicationId, the conventional AppAuth-Android scheme.
        manifestPlaceholders["appAuthRedirectScheme"] = "com.sellernest.poreceiving"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://staging-api.example.com/api/mobile/receiving/\"")
            buildConfigField("String", "OAUTH_ISSUER", "\"https://staging-auth.example.com/realms/sellernest\"")
            buildConfigField("String", "OAUTH_CLIENT_ID", "\"po-receiving-android\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Lets a plain JVM unit test safely construct/touch simple android.jar
            // classes (e.g. a placeholder Intent passed to a fake) by returning
            // default values instead of throwing "Stub!" -- needed by
            // SignInViewModelTest, which never runs on a real device.
            isReturnDefaultValues = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Lets androidTest sources declare @EntryPoint interfaces (e.g. to reach a
    // Hilt-provided singleton like QueuedSubmissionDao from a test that doesn't
    // need the full @HiltAndroidTest / HiltTestApplication scaffolding).
    kspAndroidTest(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.appauth)
    implementation(libs.androidx.browser)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlin.reflect)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
