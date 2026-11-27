rootProject.name = "RWX"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://artifacts.consensys.net/public/maven/maven/")
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}

include(":core")
include(":ui")
include(":mod-api")
include(":mod-tools")
include(":slick2d-lwjgl3")
include(":desktop")
include(":android")

// 大叔加的台架模块，不属于上游：可控刷兵，仅用于性能基准
include(":bench-mod")

// 游戏内敌我单位计数 HUD（JVM mod）：本地私有模块，不随仓库分发。
// 目录存在时才纳入构建，别人克隆后不会因为缺少该模块而构建失败。
if (file("unit-counter-mod").isDirectory) {
    include(":unit-counter-mod")
}
