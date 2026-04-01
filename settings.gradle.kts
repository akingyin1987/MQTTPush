pluginManagement {
    repositories {
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://developer.huawei.com/repo/") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://developer.huawei.com/repo/") }
        mavenCentral()
        google()
    }
}

rootProject.name = "MQTTPush"
include(":core")
include(":ui-compose")
include(":ui-xml")
include(":app")
