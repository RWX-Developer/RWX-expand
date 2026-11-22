package io.github.rwx.editor

/**
 * ini / toml 的模块化文档模型：一个节就是一个模块。
 *
 * 两种语法共用一套模型，只差分隔符：
 *   ini   key:value   节头 [name]
 *   toml  key = value 节头 [table]
 *
 * 节内所有行原样保存（注释、空行、缩进都不动），改值只替换分隔符之后的部分，
 * 所以来回编辑不会把别人的排版搅乱。首节之前的「顶层键」也当成一个无头节，
 * 这样 mod.toml 这种通篇没有 [节] 的文件照样能按模块看。
 */

/** 一行 key/value 的解析结果：键、值、以及值在原始行里的起始位置。 */
data class LineKv(val key: String, val value: String, val valueStart: Int)

/** 一个节 = 一个模块。 */
class IniSection(
    var name: String,
    /** 节头之后、下一个节头之前的原始行（含空行与注释）。 */
    val lines: MutableList<String> = mutableListOf(),
    /** 编辑器里新加的节，UI 上标一下。 */
    var isNew: Boolean = false,
    /** 分隔符：ini 用 ":"，toml 用 "="。 */
    var separator: String = ":",
    /** 无头节：没有 [...] 包头（顶层键就是这种）。 */
    val headerless: Boolean = false,
) {
    fun parseLine(raw: String): LineKv? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("#") || t.startsWith("//") || t.startsWith(";")) return null
        if (t.startsWith("[") && t.endsWith("]")) return null
        val sepChar = if (separator == "=") '=' else ':'
        val idx = raw.indexOf(sepChar)
        if (idx <= 0) return null
        val key = raw.substring(0, idx).trim()
        if (key.isEmpty() || key.startsWith("#")) return null
        var vStart = idx + 1
        while (vStart < raw.length && (raw[vStart] == ' ' || raw[vStart] == '\t')) vStart++
        return LineKv(key, raw.substring(vStart).trim(), vStart)
    }

    fun keyValues(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (raw in lines) {
            val kv = parseLine(raw) ?: continue
            out.add(kv.key to kv.value)
        }
        return out
    }

    /** 改一个键的值，保留原来的键名、分隔符和空格写法；键不存在就追加。 */
    fun setValue(key: String, value: String) {
        for (idx in lines.indices) {
            val kv = parseLine(lines[idx]) ?: continue
            if (kv.key != key) continue
            lines[idx] = lines[idx].substring(0, kv.valueStart) + value
            return
        }
        lines.add(key + if (separator == "=") " = " else ":" + value)
    }

    fun valueOf(key: String): String? {
        for ((k, v) in keyValues()) if (k == key) return v
        return null
    }

    fun numeric(key: String): Float? = valueOf(key)?.trim()?.trim('"')?.toFloatOrNull()
}

/** 整份文档。 */
class IniDocument(
    var separator: String = ":",
    val sections: MutableList<IniSection> = mutableListOf(),
) {
    private var rootSection: IniSection? = null

    /** 首个节头之前的纯空行：不显示成模块，但要原样留住，否则回写会少行。 */
    val leadingBlank: MutableList<String> = mutableListOf()

    /** 首个节头之前的内容，当成无头节返回（没有就建一个）。 */
    fun root(): IniSection {
        val r = rootSection
        if (r != null) return r
        val made = IniSection(name = "顶层键", separator = separator, headerless = true)
        sections.add(0, made)
        rootSection = made
        return made
    }

    fun dropEmptyRoot() {
        val r = rootSection ?: return
        if (r.lines.all { it.isBlank() }) {
            leadingBlank.addAll(r.lines)
            sections.remove(r)
            rootSection = null
        }
    }

    fun render(): String {
        val sb = StringBuilder()
        for (l in leadingBlank) sb.append(l).append('\n')
        for (s in sections) {
            if (!s.headerless) sb.append('[').append(s.name).append(']').append('\n')
            for (l in s.lines) sb.append(l).append('\n')
        }
        return sb.toString().trimEnd('\n')
    }

    fun addSection(name: String): IniSection {
        val s = IniSection(name = name, isNew = true, separator = separator)
        if (sections.isNotEmpty() || leadingBlank.isNotEmpty()) s.lines.add("")
        sections.add(s)
        return s
    }

    fun removeSection(s: IniSection) {
        sections.remove(s)
    }

    fun renameSection(s: IniSection, newName: String) {
        if (!s.headerless) s.name = newName
    }

    fun turretSections(): List<IniSection> = sections.filter { it.name.startsWith("turret") }

    companion object {
        fun parse(text: String, separator: String = ":"): IniDocument {
            val doc = IniDocument(separator = separator)
            var current: IniSection? = null
            for (rawLine in text.split("\n")) {
                val line = rawLine.trimEnd('\r')
                val t = line.trim()
                if (t.length > 2 && t.startsWith("[") && t.endsWith("]")) {
                    val s = IniSection(
                        name = t.substring(1, t.length - 1).trim(),
                        separator = separator,
                    )
                    doc.sections.add(s)
                    current = s
                } else {
                    val target = current ?: doc.root().also { current = it }
                    target.lines.add(line)
                }
            }
            doc.dropEmptyRoot()
            return doc
        }
    }
}

/** .kt 文件的顶层声明，用来做只读的模块视图。 */
data class KtDecl(val kind: String, val name: String, val line: Int, val text: String)

object KtOutline {
    private val decl = Regex(
        "^(?:@[A-Za-z_][A-Za-z0-9_]*(?:\\([^)]*\\))?\\s+)*" +
            "(?:(?:public|private|internal|protected|abstract|open|final|sealed|data|enum|annotation|inline|value|suspend|operator|infix|tailrec|external|override|const)\\s+)*" +
            "(class|object|interface|fun|val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)",
    )

    fun parse(text: String): List<KtDecl> {
        val out = ArrayList<KtDecl>()
        text.split("\n").forEachIndexed { i, raw ->
            if (raw.isEmpty() || raw[0] == ' ' || raw[0] == '\t') return@forEachIndexed
            val t = raw.trim()
            if (t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") ||
                t.startsWith("import ") || t.startsWith("package ") || t.startsWith("}")
            ) return@forEachIndexed
            val m = decl.find(t) ?: return@forEachIndexed
            out.add(KtDecl(m.groupValues[1], m.groupValues[2], i + 1, t))
        }
        return out
    }
}
