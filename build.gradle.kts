import io.github.rwx.build.I18nGenerationExtension
import io.github.rwx.build.I18nGenerationSupport

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

group = property("group") as String
version = property("version") as String

allprojects {
    group = rootProject.group
    version = rootProject.version
}

val generateI18nSources = I18nGenerationSupport.register(rootProject)

configure<I18nGenerationExtension> {
    inputFormat = "properties"
}

project(":core").tasks.matching { it.name.startsWith("compile") }.configureEach {
    dependsOn(generateI18nSources)
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        if (project.path != ":android") {
            options.release = 25
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("user.language", "en")
        systemProperty("user.country", "US")
        // 无头场景测试会创建 GameEngine 单例（"A game engine already exists"），
        // 一个 JVM 只能跑一场。每个测试类单开进程，避免多场无头测试互相踩。
        forkEvery = 1
    }
}
