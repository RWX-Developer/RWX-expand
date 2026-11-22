package io.github.rwx.editor

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 反编译：把 .class 变成可读 Java 源码。
 *
 * cfr.jar 打在编辑器资源里（/cfr.jar），第一次用到时解到临时目录，
 * 再用当前 JVM 起子进程跑。走子进程而不是进程内调用，是因为 CFR 出错时会 System.exit，
 * 在编辑器进程里被它带走一次就够受了。
 */
object Decompiler {

    private const val RES = "/cfr.jar"

    private val workDir: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "rwx-editor-decompile").apply { mkdirs() }
    }

    /** 解出来的 cfr.jar；资源缺失就返回 null。 */
    val cfrJar: File? by lazy {
        runCatching {
            val out = File(workDir, "cfr-0.152.jar")
            if (out.isFile && out.length() > 1_000_000) return@runCatching out
            val input = Decompiler::class.java.getResourceAsStream(RES)
                ?: return@runCatching null
            input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
            out
        }.getOrNull()
    }

    val available: Boolean get() = cfrJar != null

    fun javaExe(): String {
        val home = System.getProperty("java.home") ?: return "java"
        val exe = File(File(home, "bin"), "java.exe")
        return if (exe.isFile) exe.absolutePath else File(File(home, "bin"), "java").absolutePath
    }

    /**
     * 反编译一个 class 文件的字节。
     * @param tag 仅用于临时文件名，随便给个唯一名字即可。
     */
    fun decompile(bytes: ByteArray, tag: String): String {
        val jar = cfrJar ?: return "反编译不可用：编辑器里没找到 cfr.jar"
        val safe = tag.replace(Regex("[^A-Za-z0-9_$]"), "_").takeLast(60)
        val classFile = File(workDir, safe + "-" + System.nanoTime() + ".class")
        return try {
            classFile.writeBytes(bytes)
            val cmd = listOf(
                javaExe(), "-jar", jar.absolutePath,
                "--comments", "false",
                "--silent", "true",
                classFile.absolutePath,
            )
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return "反编译超时（60 秒）"
            }
            // CFR 把警告也混在 stdout 里，去掉纯日志行
            val body = out.lineSequence()
                .filterNot { it.startsWith("INFO:") || it.startsWith("WARN:") }
                .joinToString("\n")
                .trim()
            if (body.isEmpty()) "反编译没有输出（退出码 " + p.exitValue() + "）\n" + out.take(2000) else body
        } catch (t: Throwable) {
            "反编译失败: " + t
        } finally {
            runCatching { classFile.delete() }
        }
    }
}
