// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
}

// ============================================================
// 发布配置 - 供所有子模块使用
// ============================================================
extra["publishGroupId"] = "com.push.mqtt"
extra["publishVersion"] = "1.0.0"
extra["publishLocalPath"] = "${rootProject.projectDir}/local-repo"

// ============================================================
// 发布辅助任务
// ============================================================
tasks.register("publishAllToLocal") {
    group = "publishing"
    description = "发布所有模块到本地仓库"
    
    dependsOn(
        ":core:publishReleasePublicationToLocalRepoRepository",
        ":ui-compose:publishReleasePublicationToLocalRepoRepository",
        ":ui-xml:publishReleasePublicationToLocalRepoRepository"
    )
    
    doLast {
        val publishVersion = extra["publishVersion"] as String
        val publishLocalPath = extra["publishLocalPath"] as String
        
        println("")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("  ✓ 所有模块发布成功！")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("")
        println("发布的模块:")
        println("  • com.push.mqtt:mqtt-push-core:$publishVersion")
        println("  • com.push.mqtt:mqtt-push-ui-compose:$publishVersion")
        println("  • com.push.mqtt:mqtt-push-ui-xml:$publishVersion")
        println("")
        println("仓库位置: $publishLocalPath")
        println("")
        println("在其它项目中使用:")
        println("""
        repositories {
            maven {
                url = uri("file://$publishLocalPath")
            }
        }
        
        dependencies {
            implementation("com.push.mqtt:mqtt-push-core:$publishVersion")
            implementation("com.push.mqtt:mqtt-push-ui-compose:$publishVersion")
            // 或
            implementation("com.push.mqtt:mqtt-push-ui-xml:$publishVersion")
        }
        """.trimIndent())
        println("")
    }
}

tasks.register("cleanAndPublish") {
    group = "publishing"
    description = "清理并重新发布所有模块"
    
    dependsOn("clean", "publishAllToLocal")
}

tasks.register("showPublishInfo") {
    group = "publishing"
    description = "显示当前发布配置信息"
    
    doLast {
        val publishGroupId = extra["publishGroupId"] as String
        val publishVersion = extra["publishVersion"] as String
        val publishLocalPath = extra["publishLocalPath"] as String
        
        println("")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("  MQTT Push 发布配置")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("")
        println("Group ID:    $publishGroupId")
        println("Version:     $publishVersion")
        println("Repository:  $publishLocalPath")
        println("")
        println("可用模块:")
        println("  • mqtt-push-core       (核心功能)")
        println("  • mqtt-push-ui-compose (Jetpack Compose UI)")
        println("  • mqtt-push-ui-xml     (传统 View UI)")
        println("")
        println("完整坐标:")
        println("  • com.push.mqtt:mqtt-push-core:$publishVersion")
        println("  • com.push.mqtt:mqtt-push-ui-compose:$publishVersion")
        println("  • com.push.mqtt:mqtt-push-ui-xml:$publishVersion")
        println("")
        println("可用任务:")
        println("  • gradlew publishAllToLocal  - 发布所有模块")
        println("  • gradlew cleanAndPublish    - 清理并发布")
        println("  • gradlew showPublishInfo    - 显示配置信息")
        println("")
    }
}
