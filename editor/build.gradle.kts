import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// RWX 编辑器：独立程序。不依赖 core/desktop，启动时不需要游戏引擎。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
}

compose {
    desktop {
        application {
            mainClass = "io.github.rwx.editor.MainKt"
            jvmArgs.addAll(listOf("-Dfile.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED"))
            nativeDistributions {
                packageName = "RWX-Editor"
                vendor = "RWX"
                description = "RWX client editor"
                packageVersion = "0.1.0"
                targetFormats(TargetFormat.Msi)
            }
        }
    }
}
