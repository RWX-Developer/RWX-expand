package io.github.rwx.editor

/**
 * 条目分类：决定前端拿它怎么办。
 *
 * ini / toml 走模块视图，kt 走文本，class 走结构 + 反编译，dex 走统计，
 * 剩下的二进制给十六进制预览——一律不当文本硬解。
 */

fun isTextEntry(name: String): Boolean {
    if (name.endsWith("/")) return false
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("ini", "template", "toml", "kt", "java", "txt", "md", "json", "xml", "cfg", "properties", "gradle", "kts", "sh", "bat", "ps1", "yml", "yaml")
}

fun isEditableSource(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("ini", "template", "toml", "kt", "java", "txt", "md")
}

/** ini / toml / kt / text / class / dex / binary */
fun entryKindOf(name: String, text: String? = null): String {
    if (JvmInspect.looksLikeClass(name)) return "class"
    if (JvmInspect.looksLikeDex(name)) return "dex"
    if (!isTextEntry(name)) return "binary"
    val ext = name.substringAfterLast('.', "").lowercase()
    if (ext == "toml") return "toml"
    if (ext == "kt" || ext == "java") return "kt"
    if (!isEditableSource(name)) return "text"
    val t = text ?: return "text"
    val hasSections = t.split("\n").any { l ->
        val s = l.trim()
        s.length > 2 && s.startsWith("[") && s.endsWith("]")
    }
    val hasColon = t.split("\n").any { it.contains(':') && !it.trim().startsWith("#") }
    return if (hasSections || hasColon) "ini" else "text"
}

/** 十六进制预览：偏移 + 16 字节 + ASCII 栏。 */
fun hexPreview(bytes: ByteArray?, limit: Int = 1024): String {
    if (bytes == null) return "（读不出内容）"
    if (bytes.isEmpty()) return "（空文件）"
    val sb = StringBuilder()
    val end = minOf(bytes.size, limit)
    var i = 0
    while (i < end) {
        sb.append(String.format("%08X  ", i))
        for (k in 0 until 16) {
            if (i + k < end) sb.append(String.format("%02X ", bytes[i + k])) else sb.append("   ")
            if (k == 7) sb.append(' ')
        }
        sb.append(" |")
        for (k in 0 until 16) {
            if (i + k >= end) break
            val c = bytes[i + k].toInt() and 0xFF
            sb.append(if (c in 32..126) c.toChar() else '.')
        }
        sb.append("|\n")
        i += 16
    }
    if (bytes.size > limit) sb.append("…（共 " + bytes.size + " B，只显示前 " + limit + "）")
    return sb.toString()
}
