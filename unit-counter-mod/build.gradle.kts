plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

dependencies {
    // 平台类由宿主 classloader 提供（JvmModLoader 以平台 classloader 为父）
    compileOnly(project(":mod-api"))
    // 只读引擎的 PlayerTeam（同阵营判定）与 GameEngine.isMenuOpen
    compileOnly(project(":core"))
    compileOnly(libs.compose.runtime)
    compileOnly(libs.compose.foundation)
    compileOnly(libs.kotlinx.coroutines.core)

    testImplementation(project(":mod-api"))
    testImplementation(libs.compose.runtime)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 打包后拷到 uber jar 旁的 mods/units（storage root = jar 所在目录）
val stageMod = tasks.register<Copy>("stageModToStorage") {
    from(tasks.jar)
    into(layout.projectDirectory.dir("../desktop/build/compose/jars/mods/units"))
}
tasks.named("build") { dependsOn(stageMod) }
