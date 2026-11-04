import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
}

// 发布签名信息从 keystore.properties 读取，避免硬编码在构建脚本中
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.dyx.novelreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dyx.novelreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file("keystore/" + keystoreProps.getProperty("storeFile").removePrefix("keystore/"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // 便于真机覆盖安装调试：debug 包名加后缀，与正式版共存
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        // 离线打包环境下 lint-gradle 未必在缓存中，release 构建不做阻断式检查
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// 离线缓存里缺少 listenablefuture-1.0.jar（只有 pom）。
// 它只是 guava 的历史遗留拆分包，guava 33 本体已包含全部类，
// 直接排除，避免 profileinstaller 找不到 ListenableFuture 而崩溃。
// 离线环境无法解析 guava 的注解类元数据，改为本地 jar 依赖（见 app/libs/）
configurations.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)


    // profileinstaller 需要 ListenableFuture；离线缓存缺 listenablefuture-1.0.jar，
    // 用缓存中的 guava-33.0.0-android 本地 jar 补上（guava 本体包含这些类）
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.jcifs.ng)

    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)
}
