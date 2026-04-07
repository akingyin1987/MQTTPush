plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.kotlin.parcelize)
    `maven-publish`
}

android {
    namespace = "com.push.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }

    // proto 源码目录
    sourceSets {
        named("main") {
            java.srcDirs("src/main/proto")
        }
    }
    
    // 避免资源重复复制警告
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ==================== Maven 发布配置 ====================
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                
                groupId = rootProject.extra["publishGroupId"] as String
                artifactId = "mqtt-push-core"
                version = rootProject.extra["publishVersion"] as String
                
                pom {
                    name.set("MQTT Push Core")
                    description.set("MQTT 推送核心模块 - 包含消息管理、连接管理、数据持久化等核心功能")
                    url.set("https://github.com/akingyin1987/MQTTPush")
                    
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("developer")
                            name.set("MQTT Push Team")
                        }
                    }
                }
            }
        }
        
        repositories {
            maven {
                name = "LocalRepo"
                url = uri(rootProject.extra["publishLocalPath"] as String)
            }
        }
    }
}

// ==================== Protobuf 配置 ====================
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protoc.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")   // Android 用 lite runtime
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
  //  implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)

//    // Compose BOM
//    implementation(platform(libs.compose.bom))

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // HiveMQ
    implementation(libs.hivemq.client)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.lifecycle.service)
    ksp(libs.androidx.room.compiler)

    // Gson
    implementation(libs.gson)

    // Proto DataStore（替换 Preferences DataStore）
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.kotlin.lite)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
}
