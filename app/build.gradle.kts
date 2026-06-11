plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.yawnlock"
    compileSdk = 34

    // Release 签名:密码从 gradle.properties 读(keystore 跟密码分离)
    // 第一次发布前需要:
    //   1) keytool -genkey -v -keystore app/release.keystore -alias yawn_lock -keyalg RSA -keysize 2048 -validity 10000
    //   2) 把 release.keystore 加到 .gitignore
    //   3) 在 ~/.gradle/gradle.properties 填 RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD
    signingConfigs {
        create("release") {
            val storeFileProp = providers.gradleProperty("RELEASE_STORE_FILE")
            val storePasswordProp = providers.gradleProperty("RELEASE_STORE_PASSWORD")
            val keyAliasProp = providers.gradleProperty("RELEASE_KEY_ALIAS")
            val keyPasswordProp = providers.gradleProperty("RELEASE_KEY_PASSWORD")
            if (storeFileProp.isPresent) {
                storeFile = file(storeFileProp.get())
                storePassword = storePasswordProp.get()
                keyAlias = keyAliasProp.get()
                keyPassword = keyPasswordProp.get()
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.yawnlock"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    sourceSets["main"].java.srcDirs("src/main/kotlin")

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
