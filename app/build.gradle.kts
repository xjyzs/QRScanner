plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.xjyzs.qrscanner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.xjyzs.qrscanner"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        androidResources.localeFilters += listOf("zh", "en")

        signingConfigs {
            val hasSigningInfo = System.getenv("KEY_STORE_PASSWORD") != null &&
                    System.getenv("KEY_ALIAS") != null &&
                    System.getenv("KEY_PASSWORD") != null &&
                    file("${project.rootDir}/keystore.jks").exists()
            if (hasSigningInfo) {
                create("release") {
                    storeFile = file("${project.rootDir}/keystore.jks")
                    storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("KEY_ALIAS") ?: ""
                    keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                    enableV1Signing = false
                }
            }
        }

        flavorDimensions += "abi"
        productFlavors {
            val signingConfig = if (signingConfigs.findByName("release") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            create("x86") {
                dimension = "abi"
                ndk { abiFilters.add("x86") }
                this.signingConfig = signingConfig
            }
            create("x86_64") {
                dimension = "abi"
                ndk { abiFilters.add("x86_64") }
                this.signingConfig = signingConfig
            }
            create("arm") {
                dimension = "abi"
                ndk { abiFilters.add("armeabi-v7a") }
                this.signingConfig = signingConfig
            }
            create("arm64Minsdk35") {
                dimension = "abi"
                ndk { abiFilters.add("arm64-v8a") }
                minSdk = 35
                this.signingConfig = signingConfig
            }
            create("arm64Minsdk29") {
                dimension = "abi"
                ndk { abiFilters.add("arm64-v8a") }
                minSdk = 29
                this.signingConfig = signingConfig
            }
            create("universal") {
                dimension = "abi"
                this.signingConfig = signingConfig
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
                packaging {
                    resources {
                        excludes += setOf(
                            "DebugProbesKt.bin",
                            "kotlin-tooling-metadata.json",
                            "META-INF/**",
                            "kotlin/**"
                        )
                    }
                }
                tasks.configureEach {
                    doLast {
                        outputs.files.forEach { outputDir ->
                            val filesToDelete = setOf("PublicSuffixDatabase.list")
                            for (i in filesToDelete) {
                                val file = outputDir.resolve(i)
                                if (file.exists()) {
                                    file.delete()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
//    implementation(libs.androidx.activity.ktx)
//    implementation(libs.androidx.appcompat)
//    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.navigation.fragment.ktx)
//    implementation(libs.androidx.navigation.ui.ktx)
//    implementation(libs.material)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.androidx.espresso.core)
//    androidTestImplementation(libs.androidx.junit)
    implementation("com.github.jenly1314.WeChatQRCode:opencv:2.6.0")
    // OpenCV的ABI（可选），根据你的需要选择想要支持的SO库架构（至少选一个）
    implementation("com.github.jenly1314.WeChatQRCode:opencv-armv7a:2.6.0")
    implementation("com.github.jenly1314.WeChatQRCode:opencv-armv64:2.6.0")
    implementation("com.github.jenly1314.WeChatQRCode:opencv-x86:2.6.0")
    implementation("com.github.jenly1314.WeChatQRCode:opencv-x86_64:2.6.0")
    // 微信二维码识别功能（可选）
    implementation("com.github.jenly1314.WeChatQRCode:wechat-qrcode:2.6.0")
    // 微信二维码扫码功能（可选）
    implementation("com.github.jenly1314.WeChatQRCode:wechat-qrcode-scanning:2.6.0")
}