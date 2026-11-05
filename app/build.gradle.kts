import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "top.yogiczy.mytv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lemoniptv.lite"
        minSdk = 21
        targetSdk = 35
        versionCode = 21
        versionName = "1.1.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        release {
            // CI/无 keystore 环境先关闭混淆，保证能打出包；需要加固再改回 true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            pickFirsts += "META-INF/*.kotlin_module"
        }
        jniLibs {
            pickFirsts += "**/libffmpegJNI.so"
            pickFirsts += "**/libc++_shared.so"
            pickFirsts += "**/libjsc.so"
        }
    }
    signingConfigs {
        create("release") {
            val storePath = System.getenv("KEYSTORE")
                ?: keystoreProperties.getProperty("storeFile")
            val storePass = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
            val alias = System.getenv("KEY_ALIAS")
                ?: keystoreProperties.getProperty("keyAlias")
            val keyPass = System.getenv("KEY_PASSWORD")
                ?: keystoreProperties.getProperty("keyPassword")
            // 有完整签名配置才启用，否则后面回退 debug 签名
            if (!storePath.isNullOrBlank() && !storePass.isNullOrBlank()
                && !alias.isNullOrBlank() && !keyPass.isNullOrBlank()
            ) {
                val f = file(storePath)
                if (f.exists()) {
                    storeFile = f
                    storePassword = storePass
                    keyAlias = alias
                    keyPassword = keyPass
                }
            }
        }
    }
    buildTypes {
        getByName("release") {
            // 无 keystore 时用 debug 签名，保证 GitHub Actions / 本地可打出 APK
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null && rel.storeFile!!.exists()) {
                rel
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.material.icons.extended)

    // TV Compose
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // 播放器（Jellyfin FFmpeg 扩展与 media3 主版本须一致，用于广电类 AC3/DTS 等音轨）
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.jellyfin.media3.ffmpeg.decoder)

    // 序列化
    implementation(libs.kotlinx.serialization)

    // 网络请求
    implementation(libs.okhttp)
    implementation(libs.androidasync)

    // 二维码
    implementation(libs.qrose)

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}