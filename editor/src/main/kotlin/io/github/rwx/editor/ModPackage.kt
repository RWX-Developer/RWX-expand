package io.github.rwx.editor

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private val TEXT_EXT = setOf(
    "ini", "kt", "kts", "txt", "toml", "json", "md", "template", "py", "cfg",
    "properties", "xml", "yml", "yaml", "lua", "js", "sh", "bat", "ps1",
)

fun isTextEntry(name: String): Boolean {
    if (name.endsWith("/")) return false
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext.isNotEmpty() && ext in TEXT_EXT
}

fun isEditableSource(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext == "ini" || ext == "kt" || ext == "template" || ext == "txt" || ext == "toml"
}

/**
 * 一个模组包：.jar / .rwmod / .zip 归档，或者一个目录。
 *
 * 归档整份读进内存，改过的文本条目在保存时重新打包；原来用 STORED 存的条目
 * （RWX 的 classes.dex 就是这样）继续用 STORED，保证加载器认得出。
 */
class ModPackage(val file: File) {
    val isArchive: Boolean = file.isFile &&
        file.extension.lowercase() in setOf("jar", "zip", "rwmod")

    private val raw = LinkedHashMap<String, ByteArray>()
    private val stored = LinkedHashMap<String, Boolean>()
    /** 条目 → 改后的文本。 */
    private val edited = LinkedHashMap<String, String>()

    val entries: List<String> get() = raw.keys.toList()

    val isModified: Boolean get() = edited.isNotEmpty()

    init {
        if (isArchive) {
            ZipFile(file).use { zf ->
                val es = zf.entries()
                while (es.hasMoreElements()) {
                    val e = es.nextElement()
                    if (e.isDirectory) continue
                    val bytes = zf.getInputStream(e).use { it.readBytes() }
                    raw[e.name] = bytes
                    stored[e.name] = e.method == ZipEntry.STORED
                }
            }
        } else if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(file).path.replace('\\', '/')
                raw[rel] = f.readBytes()
                stored[rel] = false
            }
        }
    }

    fun readText(entry: String): String? {
        edited[entry]?.let { return it }
        val b = raw[entry] ?: return null
        return runCatching { String(b, Charsets.UTF_8) }.getOrNull()
    }

    /** 读原始字节（看 .class / .dex 用）。 */
    fun readBytes(entry: String): ByteArray? {
        edited[entry]?.let { return it.toByteArray(Charsets.UTF_8) }
        return raw[entry]
    }

    fun writeText(entry: String, text: String) {
        edited[entry] = text
    }

    fun hasEntry(entry: String): Boolean = raw.containsKey(entry)

    fun addEntry(name: String, bytes: ByteArray) {
        raw[name] = bytes
        stored[name] = false
    }

    /** 补一份最小 mod.toml（导出成新 jar 时用）。 */
    fun ensureModToml(id: String, name: String) {
        if (hasEntry("mod.toml")) return
        val toml = "id = \"" + id + "\"\n" +
            "entrypoint = \"com.example.mymod.MyFirstMod\"\n" +
            "name = \"" + name + "\"\n" +
            "version = \"1.0.0\"\n" +
            "minGameVersion = \"1.15\"\n"
        addEntry("mod.toml", toml.toByteArray(Charsets.UTF_8))
    }

    /** 写回原处：归档重新打包，目录直接落文件。 */
    fun save() {
        if (!isModified) return
        if (isArchive) {
            writeJar(file)
        } else {
            for ((rel, text) in edited) {
                val f = File(file, rel)
                f.parentFile?.mkdirs()
                f.writeText(text, Charsets.UTF_8)
            }
            edited.clear()
        }
    }

    /** 另存为新的 jar（目录模组也能一键打包）。 */
    fun exportJar(target: File) {
        writeJar(target)
    }

    private fun writeJar(target: File) {
        val abs = target.absoluteFile
        abs.parentFile?.mkdirs()
        val tmp = File(abs.parentFile, abs.name + ".tmp")
        ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
            for ((name, bytes) in raw) {
                val data = edited[name]?.toByteArray(Charsets.UTF_8) ?: bytes
                val e = ZipEntry(name)
                if (stored[name] == true) {
                    val crc = CRC32()
                    crc.update(data)
                    e.method = ZipEntry.STORED
                    e.size = data.size.toLong()
                    e.compressedSize = data.size.toLong()
                    e.crc = crc.value
                } else {
                    e.method = ZipEntry.DEFLATED
                }
                zos.putNextEntry(e)
                zos.write(data)
                zos.closeEntry()
            }
        }
        if (abs.exists()) {
            abs.copyTo(File(abs.parentFile, abs.name + ".bak"), overwrite = true)
        }
        if (!tmp.renameTo(abs)) {
            tmp.copyTo(abs, overwrite = true)
            tmp.delete()
        }
        edited.clear()
    }
}
