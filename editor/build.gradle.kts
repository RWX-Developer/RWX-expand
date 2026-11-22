// RWX 编辑器：本地 Web 程序。前端 Vue（web/ 目录构建进 resources/web），后端纯 JDK。
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

application {
    mainClass.set("io.github.rwx.editor.MainKt")
}

// 单 jar：main class + 运行时依赖（目前只有 kotlin-stdlib）+ 前端资源
tasks.jar {
    manifest {
        attributes("Main-Class" to "io.github.rwx.editor.MainKt")
    }
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("RWX-Editor-0.2.0.jar")
}
