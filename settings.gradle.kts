rootProject.name = "cw_1kito"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // 使用阿里云 Google 镜像（代替 Google Maven，解决国内网络问题）
        maven {
            url = java.net.URI("https://maven.aliyun.com/repository/google")
            name = "aliyun-google"
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // 使用阿里云 Google 镜像（代替 Google Maven，解决国内网络问题）
        maven {
            url = java.net.URI("https://maven.aliyun.com/repository/google")
            name = "aliyun-google"
        }
    }
}

include(":composeApp")