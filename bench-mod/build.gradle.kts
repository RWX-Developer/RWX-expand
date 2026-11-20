plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

dependencies {
    // 运行时类由平台 classloader 提供（JvmModLoader.kt: platformClassLoader 为父）
    compileOnly(project(":mod-api"))
}

// 打完包顺手拷到 jar 旁的 mods/units（storage root = uber jar 所在目录）
val stageMod = tasks.register<Copy>("stageModToStorage") {
    from(tasks.jar)
    into(layout.projectDirectory.dir("../desktop/build/compose/jars/mods/units"))
}
tasks.named("build") { dependsOn(stageMod) }
