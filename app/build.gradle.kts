import java.io.File

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

// 给所有 variant 设置 APK basename：yawn-lock-{versionName}-{buildType}.apk
// 与 RELEASE.md 的命名约定保持一致，省掉事后手动 cp 改名。
// AGP 8.5 public VariantOutput 不暴露 outputFileName，改用 doLast 后处理
// 把 assemble 阶段默认产出的 app-{buildType}.apk 改名为约定名字。
tasks.register("renameApksToReleaseConvention") {
    group = "build"
    description = "Rename APK outputs to yawn-lock-{versionName}-{buildType}.apk"
    val versionName = android.defaultConfig.versionName
    doLast {
        listOf("debug" to "app-debug.apk", "release" to "app-release.apk").forEach { (type, defaultName) ->
            val dir = layout.buildDirectory.dir("outputs/apk/$type").get().asFile
            val src = File(dir, defaultName)
            if (src.exists()) {
                val newName = "yawn-lock-$versionName-$type.apk"
                val target = File(dir, newName)
                src.renameTo(target)
                logger.lifecycle("renamed $defaultName → $newName")
            }
        }
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") && it.name != "renameApksToReleaseConvention" }
        .configureEach {
            finalizedBy("renameApksToReleaseConvention")
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

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
