package io.github.rwx.editor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.io.File

/**
 * RWX 编辑器：独立程序入口。
 *
 * 不启动游戏、不依赖引擎：只读工作区文件 + 自己的 Compose UI。
 * 需要用到游戏内能力时（预览、校验），以子进程或本地服务的方式接。
 *
 * 无界面自检：--selftest <模组路径>，用于验证 jar 读取 / ini 分节 / 打包回环。
 */
fun main(args: Array<String>) {
    val dIdx = args.indexOf("--decompile")
    if (dIdx >= 0) {
        runDecompile(args.getOrNull(dIdx + 1), args.getOrNull(dIdx + 2))
        return
    }
    if (args.any { it == "--selftest" }) {
        runSelfTest(args.last())
        return
    }
    val eIdx = args.indexOf("--entry")
    val initialEntry = if (eIdx >= 0) args.getOrNull(eIdx + 1) else null
    val positional = args.filterIndexed { i, a -> !a.startsWith("-") && (i == 0 || args[i - 1] != "--entry") }
    val workspace = positional.firstOrNull()
        ?: System.getenv("RWX_WORKSPACE")
        ?: detectDefaultWorkspace()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "RWX 编辑器 v1 — " + workspace,
            state = WindowState(size = DpSize(1440.dp, 900.dp)),
        ) {
            EditorRoot(workspaceRoot = File(workspace), initialEntry = initialEntry)
        }
    }
}

/** 无界面反编译：--decompile <模组jar> <条目名>（条目省略则取第一个 class）。 */
private fun runDecompile(jarPath: String?, entry: String?) {
    if (jarPath == null) {
        println("用法: --decompile <模组jar> [条目名]")
        return
    }
    val pkg = runCatching { ModPackage(File(jarPath)) }.getOrNull()
    if (pkg == null) {
        println("打不开: " + jarPath)
        return
    }
    val target = entry ?: pkg.entries.firstOrNull { it.endsWith(".class") }
    if (target == null) {
        println("没有 .class 条目")
        return
    }
    val bytes = pkg.readBytes(target)
    if (bytes == null) {
        println("读不出: " + target)
        return
    }
    println("decompile: " + target + "  " + bytes.size + " B  cfr=" + Decompiler.available)
    val t0 = System.currentTimeMillis()
    val src = Decompiler.decompile(bytes, target)
    println("耗时 " + (System.currentTimeMillis() - t0) + " ms  输出 " + src.length + " 字符")
    src.lineSequence().take(45).forEach { println("  " + it) }
}

private fun detectDefaultWorkspace(): String {
    val candidates = listOf(
        "D:/Game/RWX-整合包/mods/units",
        "D:/Game/铁锈战争/Rusted Warfare1.15/mods/units",
    )
    return candidates.firstOrNull { File(it).isDirectory } ?: "."
}

private fun runSelfTest(target: String) {
    val f = File(target)
    println("== selftest " + f.absolutePath)
    println("exists=" + f.exists() + " size=" + f.length())
    if (!f.exists()) {
        println("== FAIL 找不到目标")
        return
    }
    val pkg = ModPackage(f)
    println("isArchive=" + pkg.isArchive + " entries=" + pkg.entries.size)
    val inis = pkg.entries.filter { it.endsWith(".ini") }
    val kts = pkg.entries.filter { it.endsWith(".kt") }
    println("ini=" + inis.size + " kt=" + kts.size)

    var roundTripOk = 0
    var roundTripBad = 0
    for (name in inis.take(5)) {
        val text = pkg.readText(name) ?: continue
        val doc = IniDocument.parse(text)
        val rendered = doc.render()
        val same = rendered == text.trimEnd('\n')
        if (same) roundTripOk++ else roundTripBad++
        println("  " + name)
        println("    sections=" + doc.sections.size + " " + doc.sections.joinToString(" ") { "[" + it.name + "]" })
        println("    roundtrip=" + same + " srcLen=" + text.length + " outLen=" + rendered.length)
        val core = doc.sections.firstOrNull { it.name == "core" }
        if (core != null) println("    [core] 项=" + core.keyValues().size + " 首项=" + core.keyValues().first())
    }
    println("roundtrip ok=" + roundTripOk + " bad=" + roundTripBad)

    val first = inis.firstOrNull()
    if (first != null) {
        val text = pkg.readText(first)!!
        val doc = IniDocument.parse(text)
        val before = doc.sections.size
        doc.addSection("editor_selftest")
        val after = doc.sections.size
        val hadIt = doc.sections.count { it.name == "editor_selftest" }
        doc.removeSection(doc.sections.first { it.name == "editor_selftest" })
        val restored = doc.render() == text.trimEnd('\n')
        println("add/remove: " + before + " -> " + after + " (含新节=" + hadIt + ") -> " + doc.sections.size + " 复原=" + restored)
        val core = doc.sections.firstOrNull { it.name == "core" }
        if (core != null) {
            val old = core.valueOf("name")
            core.setValue("name", "xyz_selftest")
            val changed = core.valueOf("name")
            if (old != null) core.setValue("name", old)
            println("改值: " + old + " -> " + changed + " -> 回滚 " + core.valueOf("name"))
        }
    }

    val classNames = pkg.entries.filter { it.endsWith(".class") }
    val dexNames = pkg.entries.filter { it.endsWith(".dex") }
    println("jvm: class=" + classNames.size + " dex=" + dexNames.size)
    classNames.take(3).forEach { n ->
        val info = pkg.readBytes(n)?.let { JvmInspect.parseClass(it) }
        if (info == null) {
            println("  " + n + " -> 解析失败")
        } else {
            println("  " + info.name + "  super=" + (info.superName ?: "—") +
                "  " + info.javaVersion + "  方法=" + info.methods.size + "  平台引用=" + info.apiRefs.size)
            info.apiRefs.take(3).forEach { (r, c) -> println("      " + r + " x" + c) }
        }
    }
    dexNames.take(1).forEach { n ->
        val raw = pkg.readBytes(n)
        println("  " + n + " 字节=" + (raw?.size ?: -1) +
            " 头=" + (raw?.take(8)?.joinToString(" ") { String.format("%02x", it) } ?: "—"))
        val d = raw?.let { JvmInspect.parseDex(it) }
        if (d == null) {
            println("  " + n + " -> 解析失败: " + JvmInspect.lastError)
        } else {
            println("  " + n + " 版本=" + d.version + " 类=" + d.classCount +
                " 类型=" + d.typeCount + " 方法=" + d.methodCount)
            println("     顶层包前 5: " + d.packages.take(5))
            val hit = d.classNames.firstOrNull { it.endsWith("MyFirstMod") }
            println("     含 MyFirstMod 类: " + hit)
        }
    }

    val tomlName = pkg.entries.firstOrNull { it.endsWith(".toml") }
    if (tomlName != null) {
        val text = pkg.readText(tomlName)!!
        val doc = IniDocument.parse(text, "=")
        val rendered = doc.render()
        println("toml [" + tomlName + "] 节=" + doc.sections.size + " 复原=" + (rendered == text.trimEnd('\n')))
        doc.sections.take(4).forEach { s ->
            val head = if (s.headerless) "（顶层键）" else "[" + s.name + "]"
            println("  " + head + " 项=" + s.keyValues().size + " " + s.keyValues().take(3))
        }
        val g = doc.sections.firstOrNull { it.keyValues().isNotEmpty() }
        if (g != null) {
            val k = g.keyValues().first().first
            val old = g.valueOf(k)
            g.setValue(k, "\"selftest-marker\"")
            println("  改值 " + k + " -> " + g.valueOf(k))
            if (old != null) g.setValue(k, old)
            println("  回滚后原文复原=" + (doc.render() == text.trimEnd('\n')))
        }
    } else {
        println("toml: 这个包里没有 .toml")
    }

    val out = File(f.absoluteFile.parentFile, f.nameWithoutExtension + ".selftest.jar")
    runCatching { pkg.exportJar(out) }
        .onSuccess {
            val back = ModPackage(out)
            val sameNames = back.entries == pkg.entries
            val sample = inis.firstOrNull()
            val sameContent = sample != null && back.readText(sample) == pkg.readText(sample)
            println("打包 -> " + out.name + " " + out.length() + " B  条目数=" + back.entries.size +
                " 条目同名=" + sameNames + " 内容一致=" + sameContent)
            out.delete()
            println("已清理临时包")
        }
        .onFailure { println("打包失败: " + it.message) }
    println("== selftest done")
}
